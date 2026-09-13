import '../models.dart';
import '../repositories.dart';

/// Live [DashboardRepository]. Has no table of its own — the Home stat cards are
/// aggregates, so it composes the other repositories.
///
///  - **Check-ins** — `COUNT(*)` over today's `attendance` rows.
///  - **New members** — members whose `created_at` is today.
///  - **Renewed subs** — members whose `start_date` is today but whose
///    `created_at` is an earlier day (not a fresh signup).
///  - **Expired today** — [Member.isExpiredOn] (desktop `MemberCountType.EXPIRED`
///    / `countsAsExpiring`): `end_date` is today, or `remaining_days <= 0` and
///    the member checked in today. Not the Members-list "within 7 days" bucket.
///  - **Revenue** — `SUM(amount)` over today's `payments` rows.
///  - active count — from the live members list.
///
/// Unlike the desktop (which seeds each card once at startup and applies live
/// deltas — member-stats-consistency §1.1, §5.5), every call here re-queries, so
/// the cards can't drift; Home re-runs this on mount and on pull-to-refresh.
/// `currentlyInside` stays zero because `attendance` has no check-out column.
class SupabaseDashboardRepository implements DashboardRepository {
  SupabaseDashboardRepository({
    required this.members,
    required this.attendance,
    required this.payments,
    this.reference,
  });

  final MembersRepository members;
  final AttendanceRepository attendance;
  final PaymentsRepository payments;

  /// Fixed "now" for tests; production leaves this null and reads the clock
  /// per call so the dashboard doesn't go stale across midnight.
  final DateTime? reference;

  DateTime get _now => reference ?? DateTime.now();

  @override
  Future<DashboardSummary> fetchSummary({bool forceRefresh = false}) async {
    final now = _now;
    final today = DateTime(now.year, now.month, now.day);

    final results = await Future.wait<Object?>([
      members.fetchMembers(forceRefresh: forceRefresh),
      attendance.countAttendance(
        year: today.year,
        month: today.month,
        day: today.day,
      ),
      payments.sumAmount(
        year: today.year,
        month: today.month,
        day: today.day,
      ),
      attendance.findAttendance(
        year: today.year,
        month: today.month,
        day: today.day,
      ),
    ]);
    final memberList = results[0] as List<Member>;
    final checkInsToday = results[1] as int;
    final revenueToday = results[2] as int;
    final attendedToday = <String>{
      for (final a in results[3] as List<AttendanceEntry>) a.memberId,
    };

    var active = 0;
    var expiredToday = 0;
    var newToday = 0;
    var renewalsToday = 0;
    for (final m in memberList) {
      if (m.statusFrom(now) == MembershipStatus.active) active++;
      if (m.isExpiredOn(today, attendedToday: attendedToday.contains(m.id))) {
        expiredToday++;
      }
      if (m.isNewOn(today)) newToday++;
      if (m.isRenewedOn(today)) renewalsToday++;
    }

    return DashboardSummary(
      totalMembers: memberList.length,
      activeMembers: active,
      expiredToday: expiredToday,
      checkInsToday: checkInsToday,
      currentlyInside: 0, // `attendance` has no check-out column yet
      revenueTodayDzd: revenueToday,
      newMembersToday: newToday,
      renewalsToday: renewalsToday,
    );
  }

  @override
  Future<Member?> fetchSpotlightMember() async {
    final now = _now;
    // The most recent check-in *today* — not just the last one on record.
    final todays = await attendance.findAttendance(
      year: now.year,
      month: now.month,
      day: now.day,
    );
    if (todays.isEmpty) return null;
    return members.fetchMember(todays.first.memberId);
  }
}
