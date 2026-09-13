import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/foundation.dart';

import '../data/supabase/supabase_session.dart';
import '../realtime/realtime_bus.dart';
import '../realtime/realtime_service.dart';

/// Watches the OS network status and, on a transition from "no connection" to
/// "connected", retries the Supabase sign-in, makes sure the realtime socket is
/// actually still open (and rebuilds it if not), and nudges every live screen
/// to refetch.
///
/// Two failure modes this fixes, neither of which self-reports:
///  - [SupabaseSession] only retries a failed sign-in *lazily* — on the next
///    `read()` / `write()` / `client()` call. An app launched with no network
///    gets no JWT and [RealtimeService]'s channels stay on the anon key;
///    nothing then prompts a retry until the user happens to pull-to-refresh.
///  - A backgrounded app's realtime **socket can be silently killed by the
///    OS** without a close/error event ever reaching [RealtimeClient], which
///    then has no reason to run its own reconnect logic — no more realtime
///    events arrive (inserts, updates, *and* deletes) even though nothing
///    looks wrong from inside the app.
class ConnectivityRecovery {
  ConnectivityRecovery({
    required this.bus,
    this.connectivityStream,
    Future<void> Function()? ensureSignedIn,
    this.ensureRealtimeConnected,
  }) : ensureSignedIn = ensureSignedIn ?? SupabaseSession.instance.client;

  final RealtimeBus bus;

  /// Overridable for tests; defaults to the real OS connectivity stream.
  final Stream<List<ConnectivityResult>>? connectivityStream;

  /// Overridable for tests; defaults to [SupabaseSession.client].
  final Future<void> Function() ensureSignedIn;

  /// Typically [RealtimeService.ensureConnected]. Optional only so this class
  /// doesn't hard-require a [RealtimeService] instance at construction time.
  final Future<void> Function()? ensureRealtimeConnected;

  StreamSubscription<List<ConnectivityResult>>? _subscription;
  bool _wasConnected = true;

  void start() {
    if (_subscription != null) return;
    try {
      final stream = connectivityStream ?? Connectivity().onConnectivityChanged;
      _subscription = stream.listen(
        _onChanged,
        onError: (Object e) =>
            debugPrint('ConnectivityRecovery: stream error — $e'),
      );
    } catch (e) {
      debugPrint('ConnectivityRecovery: could not start — $e');
    }
  }

  void dispose() {
    _subscription?.cancel();
    _subscription = null;
  }

  void _onChanged(List<ConnectivityResult> results) {
    final connected = hasConnection(results);
    final justReconnected = connected && !_wasConnected;
    _wasConnected = connected;
    if (justReconnected) _recover();
  }

  /// Whether any of [results] indicates an active network.
  @visibleForTesting
  static bool hasConnection(List<ConnectivityResult> results) =>
      results.any((r) => r != ConnectivityResult.none);

  Future<void> _recover() async {
    debugPrint('ConnectivityRecovery: network back — recovering');
    try {
      // No-op if already signed in with a valid token; otherwise retries.
      // A success fires `signedIn`, which supabase_flutter propagates to the
      // realtime socket (`realtime.setAuth`), upgrading any channel still
      // stuck on the anon key.
      await ensureSignedIn();
    } catch (e) {
      debugPrint('ConnectivityRecovery: sign-in retry failed — $e');
    }
    try {
      // Rebuilds every channel from scratch if the socket isn't actually open
      // — see the class doc on why that can happen silently.
      await ensureRealtimeConnected?.call();
    } catch (e) {
      debugPrint('ConnectivityRecovery: realtime reconnect failed — $e');
    }
    // Nudge every live screen to refetch — covers a first fetch that failed
    // while offline, independent of whether the above succeeded.
    for (final table in RealtimeService.tables) {
      bus.bump(table);
    }
  }
}
