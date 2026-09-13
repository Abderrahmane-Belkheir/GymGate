import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/models.dart';

/// Direct checks that `Member.isNewOn` / `isRenewedOn` / `isExpiredOn` are exact
/// ports of the desktop `MemberDao.count(MemberCountType)` SQL predicates.
void main() {
  final today = DateTime(2026, 8, 29);
  final earlier = DateTime(2026, 7, 1);

  Member member({
    DateTime? createdAt,
    DateTime? start,
    DateTime? end,
    int? remainingDays,
  }) =>
      Member(
        id: 'm',
        firstName: 'A',
        lastName: 'B',
        gender: Gender.male,
        phone: '0',
        membershipStart: start,
        membershipEnd: end,
        remainingDays: remainingDays,
        createdAt: createdAt,
      );

  group('NEW — date(created_at) = today', () {
    test('created today', () {
      expect(member(createdAt: today).isNewOn(today), isTrue);
    });
    test('created another day / null', () {
      expect(member(createdAt: earlier).isNewOn(today), isFalse);
      expect(member(createdAt: null).isNewOn(today), isFalse);
    });
  });

  group('RENEW — start = today AND created != start', () {
    test('start today, created earlier', () {
      expect(
        member(start: today, createdAt: earlier).isRenewedOn(today),
        isTrue,
      );
    });
    test('start today but created same day → not a renewal', () {
      expect(member(start: today, createdAt: today).isRenewedOn(today), isFalse);
    });
    test('start today, created null → NULL != x is not true', () {
      expect(member(start: today, createdAt: null).isRenewedOn(today), isFalse);
    });
    test('start not today', () {
      expect(
        member(start: earlier, createdAt: earlier).isRenewedOn(today),
        isFalse,
      );
    });
  });

  group('EXPIRED — end = today OR (remaining_days = 0 AND attended today)', () {
    test('end_date is today (attendance irrelevant)', () {
      final m = member(end: today, remainingDays: 42);
      expect(m.isExpiredOn(today, attendedToday: false), isTrue);
    });
    test('remaining_days 0 and checked in today', () {
      final m = member(end: earlier, remainingDays: 0);
      expect(m.isExpiredOn(today, attendedToday: true), isTrue);
    });
    test('remaining_days negative and checked in today (<= 0, not == 0)', () {
      final m = member(end: earlier, remainingDays: -2);
      expect(m.isExpiredOn(today, attendedToday: true), isTrue);
    });
    test('remaining_days 0 but no check-in today', () {
      final m = member(end: earlier, remainingDays: 0);
      expect(m.isExpiredOn(today, attendedToday: false), isFalse);
    });
    test('checked in today but remaining_days not 0', () {
      final m = member(end: earlier, remainingDays: 3);
      expect(m.isExpiredOn(today, attendedToday: true), isFalse);
    });
    test('nothing matches', () {
      final m = member(end: earlier, remainingDays: null);
      expect(m.isExpiredOn(today, attendedToday: true), isFalse);
    });
  });
}
