import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/core/connectivity_recovery.dart';
import 'package:gymgate_app/realtime/realtime_bus.dart';
import 'package:gymgate_app/realtime/realtime_service.dart';

void main() {
  group('ConnectivityRecovery.hasConnection', () {
    test('none only -> false', () {
      expect(ConnectivityRecovery.hasConnection([ConnectivityResult.none]), isFalse);
    });

    test('anything else -> true', () {
      expect(ConnectivityRecovery.hasConnection([ConnectivityResult.wifi]), isTrue);
      expect(
        ConnectivityRecovery.hasConnection(
            [ConnectivityResult.none, ConnectivityResult.mobile]),
        isTrue,
      );
    });
  });

  group('ConnectivityRecovery reconnect flow', () {
    late StreamController<List<ConnectivityResult>> controller;
    late RealtimeBus bus;
    late int signInCalls;
    late int realtimeCalls;
    late ConnectivityRecovery recovery;

    setUp(() {
      controller = StreamController<List<ConnectivityResult>>.broadcast();
      bus = RealtimeBus();
      signInCalls = 0;
      realtimeCalls = 0;
      recovery = ConnectivityRecovery(
        bus: bus,
        connectivityStream: controller.stream,
        ensureSignedIn: () async {
          signInCalls++;
        },
        ensureRealtimeConnected: () async {
          realtimeCalls++;
        },
      );
      recovery.start();
    });

    tearDown(() {
      recovery.dispose();
      controller.close();
    });

    test('offline -> online retries sign-in, checks the socket, bumps every table',
        () async {
      controller.add([ConnectivityResult.none]);
      await Future<void>.delayed(Duration.zero);
      expect(signInCalls, 0); // going offline triggers nothing
      expect(realtimeCalls, 0);

      controller.add([ConnectivityResult.wifi]);
      await Future<void>.delayed(Duration.zero);

      expect(signInCalls, 1);
      expect(realtimeCalls, 1);
      for (final table in RealtimeService.tables) {
        expect(bus.revisionOf(table), 1, reason: '$table should have bumped');
      }
    });

    test('staying online (no prior drop) does not trigger recovery', () async {
      controller.add([ConnectivityResult.wifi]);
      await Future<void>.delayed(Duration.zero);
      controller.add([ConnectivityResult.wifi]);
      await Future<void>.delayed(Duration.zero);

      expect(signInCalls, 0);
      expect(realtimeCalls, 0);
      expect(bus.revisionOf('members'), 0);
    });

    test('a sign-in failure still checks the socket and bumps the tables',
        () async {
      recovery.dispose();
      recovery = ConnectivityRecovery(
        bus: bus,
        connectivityStream: controller.stream,
        ensureSignedIn: () async => throw Exception('offline'),
        ensureRealtimeConnected: () async {
          realtimeCalls++;
        },
      )..start();

      controller.add([ConnectivityResult.none]);
      await Future<void>.delayed(Duration.zero);
      controller.add([ConnectivityResult.wifi]);
      await Future<void>.delayed(Duration.zero);

      expect(realtimeCalls, 1);
      expect(bus.revisionOf('plans'), 1);
    });

    test('a realtime-reconnect failure still bumps the tables', () async {
      recovery.dispose();
      recovery = ConnectivityRecovery(
        bus: bus,
        connectivityStream: controller.stream,
        ensureSignedIn: () async {},
        ensureRealtimeConnected: () async => throw Exception('socket dead'),
      )..start();

      controller.add([ConnectivityResult.none]);
      await Future<void>.delayed(Duration.zero);
      controller.add([ConnectivityResult.wifi]);
      await Future<void>.delayed(Duration.zero);

      expect(bus.revisionOf('attendance'), 1);
    });

    test('works fine with no ensureRealtimeConnected provided', () async {
      recovery.dispose();
      recovery = ConnectivityRecovery(
        bus: bus,
        connectivityStream: controller.stream,
        ensureSignedIn: () async {
          signInCalls++;
        },
      )..start();

      controller.add([ConnectivityResult.none]);
      await Future<void>.delayed(Duration.zero);
      controller.add([ConnectivityResult.wifi]);
      await Future<void>.delayed(Duration.zero);

      expect(signInCalls, 1);
      expect(bus.revisionOf('payments'), 1);
    });
  });
}
