import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../data/supabase/supabase_session.dart';

/// Obtains the FCM registration token and keeps Supabase's `tokens` table in
/// step with it (`token` text **primary key**, `user_id` defaults to
/// `auth.uid()`, `updated_at` defaults to `now()` — verified live).
///
/// No notification handling lives here on purpose — just registration.
class FcmTokenService {
  static const String _tokenKey = 'fcm_token';

  /// Returns the currently cached token, or `null` if none is cached yet.
  Future<String?> getStoredToken() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_tokenKey);
  }

  /// Fetches the current token from Firebase and (re-)registers it on **every**
  /// launch — an upsert on the token's own primary key is cheap, and unlike
  /// gating on a local "already sent" flag, it self-heals a past registration
  /// that silently failed (no network at that moment, session not ready yet,
  /// …) instead of skipping it forever. Also listens for `onTokenRefresh` —
  /// the token can rotate at any time while the app is running, and that must
  /// reach Supabase too, not just the local cache.
  ///
  /// Requests notification permission first: on iOS, [FirebaseMessaging.getToken]
  /// does not resolve until permission has been granted (or denied).
  Future<String?> init() async {
    try {
      try {
        await FirebaseMessaging.instance.requestPermission();
      } catch (e) {
        debugPrint('FCM requestPermission failed: $e');
      }

      final token = await FirebaseMessaging.instance.getToken();
      debugPrint('FCM getToken -> $token');
      if (token != null) await _syncToken(token);

      FirebaseMessaging.instance.onTokenRefresh.listen((newToken) {
        debugPrint('FCM onTokenRefresh -> $newToken');
        _syncToken(newToken);
      });

      return token;
    } catch (e, s) {
      debugPrint('FcmTokenService.init failed: $e\n$s');
      return null;
    }
  }

  /// Upserts [token] into `tokens`, then — only once that succeeds, and only
  /// if it differs from what was cached before — deletes the stale row for
  /// the previous token, so the desktop stops holding a dead device around.
  /// A failed upsert leaves the cache and the old row untouched, so the next
  /// launch (or the next `onTokenRefresh`) retries instead of losing track.
  Future<void> _syncToken(String token) async {
    final prefs = await SharedPreferences.getInstance();
    final previous = prefs.getString(_tokenKey);

    try {
      await SupabaseSession.instance.write(
        (client) => client.from('tokens').upsert({
          'token': token,
          'updated_at': DateTime.now().toUtc().toIso8601String(),
        }),
      );
      debugPrint('Supabase tokens upsert ok: $token');
    } catch (e, s) {
      debugPrint('Supabase tokens upsert failed: $e\n$s');
      return;
    }

    await prefs.setString(_tokenKey, token);

    if (previous != null && previous != token) {
      try {
        await SupabaseSession.instance.write(
          (client) => client.from('tokens').delete().eq('token', previous),
        );
        debugPrint('Supabase tokens: removed stale token $previous');
      } catch (e, s) {
        debugPrint('Supabase tokens: could not remove stale token — $e\n$s');
      }
    }
  }
}
