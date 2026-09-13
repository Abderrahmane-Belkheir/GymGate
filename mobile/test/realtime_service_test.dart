import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:supabase_flutter/supabase_flutter.dart' show PostgresChangeEvent;

import 'package:gymgate_app/data/member_index.dart';
import 'package:gymgate_app/data/models.dart';
import 'package:gymgate_app/data/repositories.dart';
import 'package:gymgate_app/notifications/gym_notification.dart';
import 'package:gymgate_app/notifications/notification_center.dart';
import 'package:gymgate_app/realtime/realtime_bus.dart';
import 'package:gymgate_app/realtime/realtime_service.dart';

class _NoopMembers implements MembersRepository {
  @override
  Future<List<Member>> fetchMembers({bool forceRefresh = false}) async => const [];
  @override
  Future<Member?> fetchMember(String id) async => null;
  @override
  Future<void> applyCheckIn(String memberId) async {}
  @override
  Future<void> applyMemberUpsert(String id) async {}
  @override
  Future<void> applyMemberRemoval(String id) async {}

  @override
  Future<void> cancelMembership(String id) async {}

  @override
  Future<void> renewMembership({required String memberId, required Plan plan}) async {}
}

void main() {
  late RealtimeService service;

  setUp(() {
    final index = MemberIndex()
      ..rebuild([
        Member(
          id: '7',
          firstName: 'Abderrahmane',
          lastName: 'Belkheir',
          gender: Gender.male,
          phone: '0',
          photoBytes: Uint8List.fromList([1, 2, 3]),
        ),
      ]);
    service = RealtimeService(
      bus: RealtimeBus(),
      notifications: NotificationCenter(),
      memberIndex: index,
      members: _NoopMembers(),
    );
  });

  GymNotification? build(
    String table,
    PostgresChangeEvent event,
    Map<String, dynamic> row,
  ) =>
      service.notificationFor(table, event, row);

  test('attendance / payments — insert only, name + photo from the index', () {
    final checkIn =
        build('attendance', PostgresChangeEvent.insert, {'member_id': 7})!;
    expect(checkIn.kind, GymNotificationKind.checkIn);
    expect(checkIn.memberName, 'Abderrahmane Belkheir');
    expect(checkIn.photoBytes, [1, 2, 3]);

    final payment = build('payments', PostgresChangeEvent.insert,
        {'member_id': 7, 'amount': 2000})!;
    expect(payment.kind, GymNotificationKind.payment);
    expect(payment.amountDzd, 2000);
    expect(payment.photoBytes, [1, 2, 3]);

    // Non-insert events on these tables raise nothing.
    expect(build('attendance', PostgresChangeEvent.update, {'member_id': 7}),
        isNull);
    expect(build('payments', PostgresChangeEvent.delete, {'member_id': 7}),
        isNull);
  });

  test('attendance / payments delete → no banner (list just refreshes)', () {
    for (final table in ['attendance', 'payments']) {
      expect(
        build(table, PostgresChangeEvent.delete, {'id': 7, 'member_id': 7}),
        isNull,
        reason: '$table delete should raise no banner',
      );
    }
  });

  test('plans — insert / update raise a banner; delete does not', () {
    final added =
        build('plans', PostgresChangeEvent.insert, {'name': ' open '})!;
    expect(added.kind, GymNotificationKind.planAdded);
    expect(added.planName, 'open');

    final updated =
        build('plans', PostgresChangeEvent.update, {'name': 'open'})!;
    expect(updated.kind, GymNotificationKind.planUpdated);
    expect(updated.planName, 'open');

    // Delete still updates the list (bus bump in _onChange) but never banners.
    expect(build('plans', PostgresChangeEvent.delete, {'name': 'open'}), isNull);
  });

  test('members — banner on insert only; update and delete are silent', () {
    final added = build('members', PostgresChangeEvent.insert,
        {'id': 99, 'first_name': 'New', 'last_name': 'Member'})!;
    expect(added.kind, GymNotificationKind.memberAdded);
    expect(added.memberName, 'New Member'); // from the row payload
    expect(added.photoBytes, isNull); // not in the index yet

    // An update (e.g. remaining_days ticking on check-in) raises no banner —
    // the list still refreshes via the bus bump / cache splice.
    expect(
      build('members', PostgresChangeEvent.update,
          {'id': 7, 'first_name': 'Abderrahmane', 'last_name': 'Belkheir'}),
      isNull,
    );

    // A delete also just refreshes the list — no banner (deletes aren't
    // reliably nameable; see class doc on REPLICA IDENTITY).
    expect(build('members', PostgresChangeEvent.delete, {'id': 7}), isNull);
  });

  test('seance — banner on update only; insert/delete are ignored entirely', () {
    final updated = build('seance', PostgresChangeEvent.update,
        {'price': 250, 'sexe': 'FEMALE', 'cardio': 1})!;
    expect(updated.kind, GymNotificationKind.seancePriceUpdated);
    expect(updated.amountDzd, 250);
    expect(updated.seanceGender, Gender.female);
    expect(updated.seanceCardio, isTrue);

    // Not just no banner — insert/delete are not relevant at all for this
    // table (only its price is ever edited), so they raise nothing.
    expect(
      build('seance', PostgresChangeEvent.insert,
          {'price': 150, 'sexe': 'MALE', 'cardio': 0}),
      isNull,
    );
    expect(
      build('seance', PostgresChangeEvent.delete, {'price': 150}),
      isNull,
    );
  });

  group('belongsToUser — every event is matched to the account by user_id', () {
    const me = 'user-aaa';
    const someoneElse = 'user-bbb';

    test('only the current account\'s row passes', () {
      expect(RealtimeService.belongsToUser({'id': 7, 'user_id': me}, me), isTrue);
      expect(
        RealtimeService.belongsToUser({'id': 3, 'user_id': someoneElse}, me),
        isFalse,
      );
    });

    test('no session, or no / empty user_id on the row -> dropped', () {
      expect(
        RealtimeService.belongsToUser({'id': 7, 'user_id': me}, null),
        isFalse,
      );
      expect(RealtimeService.belongsToUser({'id': 7}, me), isFalse);
      expect(RealtimeService.belongsToUser({'user_id': ''}, me), isFalse);
    });
  });

  group('onlyBookkeepingChanged — tells a sync ack apart from a real edit', () {
    test('only `synced` differs -> true (the desktop\'s ack write)', () {
      expect(
        RealtimeService.onlyBookkeepingChanged(
          {'id': 2, 'name': '3 fois', 'price': 2000, 'synced': 0},
          {'id': 2, 'name': '3 fois', 'price': 2000, 'synced': 1},
        ),
        isTrue,
      );
    });

    test('a business column also differs -> false (a real edit)', () {
      expect(
        RealtimeService.onlyBookkeepingChanged(
          {'id': 2, 'name': '3 fois', 'price': 2000, 'synced': 1},
          {'id': 2, 'name': '3 fois', 'price': 2500, 'synced': 0},
        ),
        isFalse,
      );
    });

    test('no `oldRecord` (not REPLICA IDENTITY FULL) -> false, play it safe',
        () {
      expect(
        RealtimeService.onlyBookkeepingChanged(null, {'id': 2, 'price': 2000}),
        isFalse,
      );
      expect(
        RealtimeService.onlyBookkeepingChanged(
            const {}, {'id': 2, 'price': 2000}),
        isFalse,
      );
    });
  });
}
