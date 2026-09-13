import 'member_index.dart';
import 'models.dart';
import 'trend.dart';

/// Data contracts for the UI. Feature widgets depend only on these interfaces,
/// never on a concrete source (`MockGymRepositories` for tests / previews, the
/// Supabase-backed implementations for the app).
///
/// The app is read-mostly; the only write so far is [PlansRepository.createPlan].

abstract interface class MembersRepository {
  /// The full member list. Implementations may cache the result; pass
  /// [forceRefresh] to bypass the cache and refetch from the source. A forced
  /// fetch refreshes the shared cache, so anything else reading it next (the
  /// Home dashboard, the Members list) sees the same fresh data.
  Future<List<Member>> fetchMembers({bool forceRefresh = false});
  Future<Member?> fetchMember(String id);

  /// Optimistically reflect a check-in locally: decrement the member's
  /// `remaining_days` in the cache (display only — the backend does its own
  /// decrement; a pull-to-refresh reconciles). No-op for non-caching impls.
  Future<void> applyCheckIn(String memberId);

  /// Splice one member into the cache from a realtime `members` INSERT/UPDATE.
  /// The row is re-fetched by id (with its plan + photo joins) so the cached
  /// list stays complete and correctly ordered. No-op for non-caching impls,
  /// and when nothing is cached yet — the next fetch already includes it.
  Future<void> applyMemberUpsert(String id);

  /// Drop a member from the cache on a realtime `members` DELETE. No-op for
  /// non-caching impls.
  Future<void> applyMemberRemoval(String id);

  /// Cancels a member's membership: clears `plan_id`, `start_date`, `end_date`
  /// and `remaining_days` server-side and flags `synced = 0`. The cache is
  /// updated so the member drops to the Inactive list at once.
  Future<void> cancelMembership(String memberId);

  /// Renews / (re)starts [memberId]'s membership on [plan]: sets `plan_id`, a
  /// fresh `start_date` (today) + `end_date` (today + the plan's duration) +
  /// `remaining_days` server-side (`synced = 0`), and records a `payments` row
  /// for the plan price. The cache is updated so the member returns to the
  /// Active list at once.
  Future<void> renewMembership({required String memberId, required Plan plan});
}

abstract interface class PlansRepository {
  Future<List<Plan>> fetchPlans();

  /// Inserts a new membership plan and returns it with its assigned id.
  /// [daysPerMonth] `null` → an unlimited ("open") plan.
  Future<Plan> createPlan({
    required String name,
    required int priceDzd,
    required int durationMonths,
    required int? daysPerMonth,
    required bool cardioIncluded,
    required Gender gender,
  });

  /// Updates an existing plan (found by [id]) and returns the fresh row. The
  /// plan's gender is not editable (mirrors the desktop). Also flips `synced`
  /// to `0` so the desktop re-pulls the row.
  Future<Plan> updatePlan({
    required String id,
    required String name,
    required int priceDzd,
    required int durationMonths,
    required int? daysPerMonth,
    required bool cardioIncluded,
  });
}

abstract interface class SeanceRepository {
  /// Single-session ("séance") drop-in prices. The Plans screen filters by the
  /// selected `sexe`.
  Future<List<Seance>> fetchSeances();

  /// Updates a séance rate's price and flags `synced = 0` so the desktop
  /// re-pulls it. Gender / cardio aren't editable from mobile — only the price
  /// is ever meant to change.
  Future<Seance> updatePrice({required String id, required int priceDzd});
}

abstract interface class PaymentsRepository {
  /// Recent payments, newest first.
  Future<List<Payment>> fetchPayments();

  /// Payments matching an optional year / month / day, newest first.
  /// Mirrors the desktop `PaymentDao.find`.
  Future<List<Payment>> findPayments({int? year, int? month, int? day});

  /// Sum of `amount` over payments matching an optional year / month / day
  /// (0 when none match). Mirrors the desktop `PaymentDao.sumAmount`.
  Future<int> sumAmount({int? year, int? month, int? day});

  /// Revenue bucketed for the "Statistics" bar chart — one round trip:
  /// daily → per day of [year]/[month]; monthly → per month of [year];
  /// allTime → per year. [month] is ignored for the non-daily modes.
  Future<TrendSeries> revenueTrend({
    required TrendMode mode,
    required int year,
    required int month,
  });
}

abstract interface class AttendanceRepository {
  /// Recent check-ins, newest first.
  Future<List<AttendanceEntry>> fetchRecentAttendance();

  /// Check-ins matching an optional year / month / day, newest first.
  /// Mirrors the desktop `AttendanceDao.find`.
  Future<List<AttendanceEntry>> findAttendance({int? year, int? month, int? day});

  /// Number of check-ins matching an optional year / month / day.
  /// Mirrors the desktop `AttendanceDao.count`.
  Future<int> countAttendance({int? year, int? month, int? day});

  /// Check-ins bucketed for the "Statistics" bar chart — one round trip:
  /// daily → per day of [year]/[month] (plus the busiest weekday / hour);
  /// monthly → per month of [year]; allTime → per year.
  Future<TrendSeries> checkInTrend({
    required TrendMode mode,
    required int year,
    required int month,
  });
}

abstract interface class DashboardRepository {
  /// The Home stat-card aggregates. [forceRefresh] is forwarded to the
  /// underlying member fetch so a pull-to-refresh on Home also refreshes the
  /// shared member cache (and vice-versa).
  Future<DashboardSummary> fetchSummary({bool forceRefresh = false});

  /// The member the reception desk is currently looking at — on desktop this is
  /// driven by face-recognition; on mobile we surface the most recent check-in.
  Future<Member?> fetchSpotlightMember();
}

/// Bundle passed down the widget tree via `RepositoryScope`.
class GymRepositories {
  const GymRepositories({
    required this.members,
    required this.plans,
    required this.seance,
    required this.payments,
    required this.attendance,
    required this.dashboard,
    required this.memberIndex,
  });

  final MembersRepository members;
  final PlansRepository plans;
  final SeanceRepository seance;
  final PaymentsRepository payments;
  final AttendanceRepository attendance;
  final DashboardRepository dashboard;

  /// Shared `memberId → {name, photo}` index, rebuilt on every members fetch.
  /// Read by the Members / Attendance / Payments avatars and by realtime
  /// notifications (which only get a `member_id`).
  final MemberIndex memberIndex;
}
