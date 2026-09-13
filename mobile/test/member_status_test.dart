import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/models.dart';

/// `Member.statusFrom` — the status pill / Active-Inactive filter
/// (member-stats-consistency §3.3, §3.4): expired iff `!isValidAt`; a valid
/// membership is "expiring" when `remaining_days <= 7` OR `end_date` is within
/// 7 days (the end-date term, added so an "expires today" member is never a
/// plain green Active).
void main() {
  final now = DateTime(2026, 8, 29, 12);
  const plan = Plan(id: 'p', name: 'std', priceDzd: 2000, visitsPerMonth: 12);

  Member m({DateTime? end, int? remainingDays, Plan? withPlan = plan}) => Member(
        id: 'm',
        firstName: 'A',
        lastName: 'B',
        gender: Gender.male,
        phone: '0',
        plan: withPlan,
        membershipEnd: end,
        remainingDays: remainingDays,
      );

  DateTime inDays(int d) => DateTime(2026, 8, 29 + d);

  test('no plan → none', () {
    expect(m(withPlan: null, end: inDays(30)).statusFrom(now),
        MembershipStatus.none);
  });

  test('end date passed → expired', () {
    expect(m(end: inDays(-1), remainingDays: 5).statusFrom(now),
        MembershipStatus.expired);
  });

  test('remaining_days <= 0 → expired (0 and negative)', () {
    expect(m(end: inDays(30), remainingDays: 0).statusFrom(now),
        MembershipStatus.expired);
    expect(m(end: inDays(30), remainingDays: -3).statusFrom(now),
        MembershipStatus.expired);
  });

  test('valid + few days left → expiring', () {
    expect(m(end: inDays(30), remainingDays: 3).statusFrom(now),
        MembershipStatus.expiring);
  });

  test('valid + plenty of days but end date within 7 → expiring', () {
    expect(m(end: inDays(4), remainingDays: 40).statusFrom(now),
        MembershipStatus.expiring);
  });

  test('valid + end date is today → expiring, never plain Active', () {
    expect(m(end: inDays(0), remainingDays: 40).statusFrom(now),
        MembershipStatus.expiring);
  });

  test('valid + unlimited (null days) + end date near → expiring', () {
    expect(m(end: inDays(3), remainingDays: null).statusFrom(now),
        MembershipStatus.expiring);
  });

  test('valid + comfortably far off → active', () {
    expect(m(end: inDays(60), remainingDays: 40).statusFrom(now),
        MembershipStatus.active);
    expect(m(end: inDays(60), remainingDays: null).statusFrom(now),
        MembershipStatus.active);
  });
}
