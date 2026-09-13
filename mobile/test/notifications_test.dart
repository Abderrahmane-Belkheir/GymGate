import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/core/formatting.dart';
import 'package:gymgate_app/data/models.dart';
import 'package:gymgate_app/notifications/gym_notification.dart';
import 'package:gymgate_app/notifications/notification_center.dart';

import 'support.dart';

void main() {
  group('NotificationCenter', () {
    test('add → newest first, unread count, banner queue', () {
      final center = NotificationCenter();
      final a = GymNotification(kind: GymNotificationKind.checkIn);
      final b = GymNotification(kind: GymNotificationKind.payment, amountDzd: 2000);

      center.add(a);
      center.add(b);

      expect(center.items.map((n) => n.id).toList(), [b.id, a.id]);
      expect(center.unreadCount, 2);
      expect(center.pendingBanner, a); // FIFO

      center.consumeBanner();
      expect(center.pendingBanner, b);
      center.consumeBanner();
      expect(center.pendingBanner, isNull);
    });

    test('markAllRead clears the unread count', () {
      final center = NotificationCenter()
        ..add(GymNotification(kind: GymNotificationKind.checkIn))
        ..add(GymNotification(kind: GymNotificationKind.checkIn));
      expect(center.unreadCount, 2);
      center.markAllRead();
      expect(center.unreadCount, 0);
    });

    test('caps the list at 60', () {
      final center = NotificationCenter();
      for (var i = 0; i < 100; i++) {
        center.add(GymNotification(kind: GymNotificationKind.checkIn));
      }
      expect(center.items.length, 60);
    });
  });

  group('GymNotification.present', () {
    test('builds localised title + body from kind and data', () async {
      final s = await loadEnglishStrings();

      final checkIn = GymNotification(
        kind: GymNotificationKind.checkIn,
        memberName: 'Ahmed Belkheir',
      ).present(s);
      expect(checkIn.title, 'New check-in');
      expect(checkIn.body, 'Ahmed Belkheir visited');

      final payment = GymNotification(
        kind: GymNotificationKind.payment,
        memberName: 'Ahmed Belkheir',
        amountDzd: 2000,
      ).present(s);
      expect(payment.title, 'Payment recorded');
      expect(payment.body, 'Ahmed Belkheir paid ${formatDzd(2000)}');

      final anon = GymNotification(kind: GymNotificationKind.checkIn).present(s);
      expect(anon.body, 'Someone visited');

      final seance = GymNotification(
        kind: GymNotificationKind.seancePriceUpdated,
        seanceGender: Gender.female,
        seanceCardio: true,
        amountDzd: 250,
      ).present(s);
      expect(seance.title, 'Session price updated');
      expect(seance.body, 'Female · With cardio · ${formatDzd(250)}');
    });
  });

  test('notificationAge is compact', () {
    final now = DateTime(2026, 8, 30, 12);
    expect(notificationAge(now.subtract(const Duration(seconds: 5)), now), 'now');
    expect(notificationAge(now.subtract(const Duration(minutes: 9)), now), '9m');
    expect(notificationAge(now.subtract(const Duration(hours: 3)), now), '3h');
    expect(notificationAge(now.subtract(const Duration(days: 2)), now), '2d');
  });
}
