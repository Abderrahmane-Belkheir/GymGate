import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/mock/mock_repositories.dart';

/// Locks in the desktop `MemberDao.count(MemberCountType)` rules the Supabase
/// dashboard mirrors, plus the payments sum:
///  - NEW     = `date(created_at) = today`
///  - RENEW   = `date(start_date) = today AND date(created_at) != date(start_date)`
///  - EXPIRED = `date(end_date) = today OR (remaining_days = 0 AND checked in today)`
///  - revenue = SUM(amount) over today's payments
void main() {
  final reference = DateTime(2026, 8, 29, 19);

  test('stat-card aggregates are computed from the live sources', () async {
    final summary = await buildMockRepositories(reference: reference)
        .dashboard
        .fetchSummary();

    // m_14: created today + starts today -> new, not a renewal.
    expect(summary.newMembersToday, 1);
    // m_02: starts today, created weeks ago -> renewal, not new.
    expect(summary.renewalsToday, 1);
    // m_08: end_date is today  +  m_06: remaining_days 0 and checked in today.
    expect(summary.expiredToday, 2);
    // p_01 + p_02 + p_03 fall on the reference day.
    expect(summary.revenueTodayDzd, 2000 + 4000 + 2000);
  });
}
