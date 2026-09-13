import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../config/supabase_config.dart';

/// Owns the Supabase access token (JWT) on the device.
///
/// Every Supabase read runs through [read], which guarantees a usable JWT is
/// attached to the request:
///
///   1. **Before the request** — [client] hands back a client only once there
///      is a non-expired session:
///        * no session, a saved token that is still valid -> restore it;
///        * no session / saved token expired              -> sign in with the
///          configured email + password and persist the new token.
///   2. **If the server rejects the token anyway** (revoked, secret rotated,
///      clock skew, user deleted) — [read] signs in again with email + password
///      and retries the query once.
///
/// The token lives in [SharedPreferences] under [_prefsKey] as the gotrue
/// session JSON (access token + refresh token + expiry). `onAuthStateChange`
/// writes it back on every change so the persisted copy never lags the live
/// one.
class SupabaseSession {
  SupabaseSession._();

  /// Shared instance — repositories reach it directly.
  static final SupabaseSession instance = SupabaseSession._();

  static const String _prefsKey = 'supabase_session_json';

  /// Treat a token that expires within this window as already expired, so a
  /// request can't start with a JWT that dies mid-flight.
  static const Duration _expiryLeeway = Duration(seconds: 30);

  SupabaseClient get _rawClient => Supabase.instance.client;
  GoTrueClient get _auth => _rawClient.auth;

  bool _listening = false;
  SupabaseConfig? _config;

  /// De-dupes concurrent sign-ins — the first read to need one owns it, the
  /// rest await the same future.
  Future<void>? _signIn;

  /// Call once from `main()` after `Supabase.initialize`. Restores any saved
  /// token and starts persisting later changes.
  Future<void> init() async {
    if (!_listening) {
      _listening = true;
      _auth.onAuthStateChange.listen((data) {
        final session = data.session;
        if (session != null) {
          _persist(session);
        } else if (data.event == AuthChangeEvent.signedOut) {
          _clearStored();
        }
      });
    }
    await _ensureFreshSession();
  }

  /// Runs [query] against a client that carries a fresh JWT. If Supabase
  /// rejects the token, signs in again with email + password and retries once.
  Future<T> read<T>(Future<T> Function(SupabaseClient client) query) async {
    await _ensureFreshSession();
    try {
      return await query(_rawClient);
    } catch (e) {
      if (!_isTokenRejection(e)) rethrow;
      debugPrint('SupabaseSession: server rejected the JWT — re-authenticating');
      await _forceSignIn();
      return query(_rawClient);
    }
  }

  /// Same guarantees as [read] — the JWT is fresh before the call and a
  /// server-side token rejection triggers one re-auth + retry — for a write
  /// (insert / update / delete).
  Future<T> write<T>(Future<T> Function(SupabaseClient client) mutation) =>
      read(mutation);

  /// The client to run reads against. Awaited, so by the time it returns the
  /// current JWT is present and not expired. Prefer [read], which also recovers
  /// from a server-side token rejection.
  Future<SupabaseClient> client() async {
    await _ensureFreshSession();
    return _rawClient;
  }

  /// The current access token, or `null` if a session could not be established.
  String? get accessToken => _auth.currentSession?.accessToken;

  /// The signed-in user's id (the JWT `sub` claim), or `null` if there is no
  /// session. Used to match a row's owner against the current account.
  String? get currentUserId => _auth.currentSession?.user.id;

  Future<void> _ensureFreshSession() {
    final current = _auth.currentSession;
    if (current != null && !_isExpired(current)) return Future.value();
    return _startSignIn(restoreFirst: current == null);
  }

  Future<void> _forceSignIn() => _startSignIn(restoreFirst: false);

  Future<void> _startSignIn({required bool restoreFirst}) {
    return _signIn ??= _acquireSession(restoreFirst: restoreFirst)
        .whenComplete(() => _signIn = null);
  }

  Future<void> _acquireSession({required bool restoreFirst}) async {
    if (restoreFirst && await _restoreStored()) return;
    // A live-but-expired (or server-rejected) session: the user asked for a
    // fresh password sign-in rather than a refresh-token round trip.
    await _signInWithPassword();
  }

  /// Loads the saved session and, if it is still valid, hands it to the client.
  /// Returns whether a usable session is now active.
  Future<bool> _restoreStored() async {
    final stored = await _readStored();
    if (stored == null || _isExpired(stored)) return false;
    try {
      await _auth.recoverSession(jsonEncode(stored.toJson()));
      final active = _auth.currentSession;
      return active != null && !_isExpired(active);
    } catch (e) {
      debugPrint('SupabaseSession: could not restore stored token: $e');
      return false;
    }
  }

  Future<void> _signInWithPassword() async {
    final config = await _loadConfig();
    if (!config.hasCredentials) {
      debugPrint(
        'SupabaseSession: no email/password in config/supabase.properties',
      );
      return;
    }
    try {
      final res = await _auth.signInWithPassword(
        email: config.email,
        password: config.password,
      );
      final session = res.session;
      if (session != null) {
        await _persist(session);
      } else {
        debugPrint('SupabaseSession: sign-in returned no session');
      }
    } catch (e) {
      debugPrint('SupabaseSession: password sign-in failed: $e');
    }
  }

  bool _isExpired(Session session) {
    final expiresAt = session.expiresAt;
    if (expiresAt == null) return false;
    final expiry = DateTime.fromMillisecondsSinceEpoch(expiresAt * 1000);
    return DateTime.now().add(_expiryLeeway).isAfter(expiry);
  }

  /// Whether [error] is Supabase saying "this JWT is no good" — as opposed to a
  /// normal query error we should surface. PostgREST answers an unusable token
  /// with HTTP 401 and code `PGRST301` / `PGRST303`; gotrue throws
  /// [AuthException] for the same.
  bool _isTokenRejection(Object error) {
    if (error is AuthException) return true;
    if (error is PostgrestException) {
      final code = error.code;
      if (code == 'PGRST301' || code == 'PGRST303' || code == '401') return true;
      final message = error.message.toLowerCase();
      return message.contains('jwt') ||
          message.contains('token is expired') ||
          message.contains('unauthorized');
    }
    return false;
  }

  Future<SupabaseConfig> _loadConfig() async =>
      _config ??= await SupabaseConfig.load();

  Future<Session?> _readStored() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_prefsKey);
    if (raw == null) return null;
    try {
      return Session.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    } catch (e) {
      debugPrint('SupabaseSession: stored token unreadable, dropping it: $e');
      await prefs.remove(_prefsKey);
      return null;
    }
  }

  Future<void> _persist(Session session) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefsKey, jsonEncode(session.toJson()));
  }

  Future<void> _clearStored() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_prefsKey);
  }
}
