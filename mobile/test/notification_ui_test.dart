import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/i18n/app_strings.dart';
import 'package:gymgate_app/notifications/gym_notification.dart';
import 'package:gymgate_app/notifications/notification_banner_host.dart';
import 'package:gymgate_app/notifications/notification_center.dart';
import 'package:gymgate_app/notifications/notification_sheet.dart';
import 'package:gymgate_app/theme/gym_theme.dart';

import 'support.dart';

void main() {
  late AppStrings strings;
  setUpAll(() async {
    strings = await loadEnglishStrings();
  });

  Widget host(NotificationCenter center, Widget child) => withStrings(
        strings,
        NotificationScope(
          center: center,
          child: MaterialApp(theme: buildGymTheme(), home: child),
        ),
      );

  testWidgets('banner slides in, shows title + body, then flies away',
      (tester) async {
    final center = NotificationCenter();
    final bellKey = GlobalKey();

    await tester.pumpWidget(host(
      center,
      Scaffold(
        body: Stack(
          children: [
            Positioned(
              top: 0,
              right: 0,
              child: SizedBox(key: bellKey, width: 40, height: 40),
            ),
            NotificationBannerHost(bellKey: bellKey),
          ],
        ),
      ),
    ));

    center.add(GymNotification(
      kind: GymNotificationKind.checkIn,
      memberName: 'Ahmed Belkheir',
    ));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400)); // enter finishes

    expect(find.text('New check-in'), findsOneWidget);
    expect(find.text('Ahmed Belkheir visited'), findsOneWidget);
    expect(center.pendingBanner, isNotNull);

    await tester.pump(const Duration(seconds: 4)); // hold expires → fly
    await tester.pump(const Duration(milliseconds: 800)); // fly finishes

    expect(center.pendingBanner, isNull); // consumed
    expect(find.text('New check-in'), findsNothing);
  });

  testWidgets('tapping the banner sends it to the bell immediately',
      (tester) async {
    final center = NotificationCenter();
    final bellKey = GlobalKey();

    await tester.pumpWidget(host(
      center,
      Scaffold(
        body: Stack(
          children: [
            Positioned(
              top: 0,
              right: 0,
              child: SizedBox(key: bellKey, width: 40, height: 40),
            ),
            NotificationBannerHost(bellKey: bellKey),
          ],
        ),
      ),
    ));

    center.add(GymNotification(kind: GymNotificationKind.payment, amountDzd: 2000));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    await tester.tap(find.text('Payment recorded'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 800)); // fly finishes early

    expect(center.pendingBanner, isNull);
  });

  testWidgets('the bell sheet lists notifications and marks them read',
      (tester) async {
    final center = NotificationCenter()
      ..add(GymNotification(
          kind: GymNotificationKind.checkIn, memberName: 'Ahmed'))
      ..add(GymNotification(kind: GymNotificationKind.planAdded, planName: 'open'));
    expect(center.unreadCount, 2);

    await tester.pumpWidget(host(
      center,
      Scaffold(
        body: Builder(
          builder: (context) => Center(
            child: ElevatedButton(
              onPressed: () => showNotificationSheet(context),
              child: const Text('open'),
            ),
          ),
        ),
      ),
    ));

    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();

    expect(find.text('Notifications'), findsOneWidget);
    expect(find.text('New check-in'), findsOneWidget);
    expect(find.text('New plan'), findsOneWidget);
    expect(center.unreadCount, 0); // opening marks all read
  });
}
