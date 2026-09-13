import '../caching_members_repository.dart';
import '../member_index.dart';
import '../models.dart';
import '../repositories.dart';
import '../trend.dart';
import 'mock_gym_data.dart';

/// In-memory implementations of the repository contracts, backed by
/// [MockGymData]. A small artificial delay mimics a network round-trip so the
/// UI's loading states are exercised.
const _latency = Duration(milliseconds: 350);

class MockMembersRepository implements MembersRepository {
  MockMembersRepository(this._data);
  final MockGymData _data;

  @override
  Future<List<Member>> fetchMembers({bool forceRefresh = false}) async {
    await Future<void>.delayed(_latency);
    return List.unmodifiable(_data.members);
  }

  @override
  Future<Member?> fetchMember(String id) async {
    await Future<void>.delayed(_latency);
    for (final m in _data.members) {
      if (m.id == id) return m;
    }
    return null;
  }

  @override
  Future<void> applyCheckIn(String memberId) async {} // no cache to adjust

  @override
  Future<void> applyMemberUpsert(String id) async {} // no cache to adjust

  @override
  Future<void> applyMemberRemoval(String id) async {} // no cache to adjust

  @override
  Future<void> cancelMembership(String id) async {
    await Future<void>.delayed(_latency);
    final i = _data.members.indexWhere((m) => m.id == id);
    if (i < 0) return;
    final m = _data.members[i];
    _data.members[i] = Member(
      id: m.id,
      firstName: m.firstName,
      lastName: m.lastName,
      gender: m.gender,
      phone: m.phone,
      createdAt: m.createdAt,
      photoBytes: m.photoBytes,
      // plan / start / end / remaining_days cleared
    );
  }

  @override
  Future<void> renewMembership({
    required String memberId,
    required Plan plan,
  }) async {
    await Future<void>.delayed(_latency);
    final i = _data.members.indexWhere((m) => m.id == memberId);
    if (i < 0) return;
    final m = _data.members[i];
    final now = DateTime.now();
    final start = DateTime(now.year, now.month, now.day);
    final end = DateTime(start.year, start.month + plan.durationMonths, start.day);
    _data.members[i] = Member(
      id: m.id,
      firstName: m.firstName,
      lastName: m.lastName,
      gender: m.gender,
      phone: m.phone,
      createdAt: m.createdAt,
      photoBytes: m.photoBytes,
      plan: plan,
      membershipStart: start,
      membershipEnd: end,
      remainingDays:
          plan.isUnlimited ? null : plan.visitsPerMonth * plan.durationMonths,
    );
    _data.payments.add(Payment(
      id: 'pay_${now.microsecondsSinceEpoch}',
      memberId: m.id,
      memberName: m.fullName,
      amountDzd: plan.priceDzd,
      date: now,
      planName: plan.name,
    ));
  }
}

class MockPlansRepository implements PlansRepository {
  MockPlansRepository(this._data);
  final MockGymData _data;

  @override
  Future<List<Plan>> fetchPlans() async {
    await Future<void>.delayed(_latency);
    return List.unmodifiable(_data.plans);
  }

  @override
  Future<Plan> createPlan({
    required String name,
    required int priceDzd,
    required int durationMonths,
    required int? daysPerMonth,
    required bool cardioIncluded,
    required Gender gender,
  }) async {
    await Future<void>.delayed(_latency);
    final plan = Plan(
      id: 'plan_${DateTime.now().microsecondsSinceEpoch}',
      name: name,
      priceDzd: priceDzd,
      billingDays: durationMonths * 30,
      visitsPerMonth: daysPerMonth ?? 0,
      cardioIncluded: cardioIncluded,
      restrictedTo: gender,
    );
    _data.plans.add(plan);
    return plan;
  }

  @override
  Future<Plan> updatePlan({
    required String id,
    required String name,
    required int priceDzd,
    required int durationMonths,
    required int? daysPerMonth,
    required bool cardioIncluded,
  }) async {
    await Future<void>.delayed(_latency);
    final i = _data.plans.indexWhere((p) => p.id == id);
    final updated = Plan(
      id: id,
      name: name,
      priceDzd: priceDzd,
      billingDays: durationMonths * 30,
      visitsPerMonth: daysPerMonth ?? 0,
      cardioIncluded: cardioIncluded,
      restrictedTo: i >= 0 ? _data.plans[i].restrictedTo : null, // gender kept
    );
    if (i >= 0) {
      _data.plans[i] = updated;
    } else {
      _data.plans.add(updated);
    }
    return updated;
  }
}

class MockSeanceRepository implements SeanceRepository {
  MockSeanceRepository(this._data);
  final MockGymData _data;

  @override
  Future<List<Seance>> fetchSeances() async {
    await Future<void>.delayed(_latency);
    return List.unmodifiable(_data.seances);
  }

  @override
  Future<Seance> updatePrice({required String id, required int priceDzd}) async {
    await Future<void>.delayed(_latency);
    final i = _data.seances.indexWhere((s) => s.id == id);
    if (i < 0) throw StateError('Seance not found: $id');
    final existing = _data.seances[i];
    final updated = Seance(
      id: existing.id,
      priceDzd: priceDzd,
      cardio: existing.cardio,
      restrictedTo: existing.restrictedTo,
    );
    _data.seances[i] = updated;
    return updated;
  }
}

class MockPaymentsRepository implements PaymentsRepository {
  MockPaymentsRepository(this._data);
  final MockGymData _data;

  @override
  Future<List<Payment>> fetchPayments() => findPayments();

  @override
  Future<List<Payment>> findPayments({int? year, int? month, int? day}) async {
    await Future<void>.delayed(_latency);
    final rows = _data.payments
        .where((p) => _matchesYmd(p.date, year, month, day))
        .toList()
      ..sort((a, b) => b.date.compareTo(a.date));
    return List.unmodifiable(rows);
  }

  @override
  Future<int> sumAmount({int? year, int? month, int? day}) async {
    await Future<void>.delayed(_latency);
    return _data.payments
        .where((p) => _matchesYmd(p.date, year, month, day))
        .fold<int>(0, (sum, p) => sum + p.amountDzd);
  }

  @override
  Future<TrendSeries> revenueTrend({
    required TrendMode mode,
    required int year,
    required int month,
  }) async {
    await Future<void>.delayed(_latency);
    return bucketTrend(
      mode,
      _data.payments.map((p) => (when: p.date, weight: p.amountDzd)),
      year: year,
      month: month,
    );
  }
}

class MockAttendanceRepository implements AttendanceRepository {
  MockAttendanceRepository(this._data);
  final MockGymData _data;

  @override
  Future<List<AttendanceEntry>> fetchRecentAttendance() => findAttendance();

  @override
  Future<List<AttendanceEntry>> findAttendance({
    int? year,
    int? month,
    int? day,
  }) async {
    await Future<void>.delayed(_latency);
    final rows = _data.attendance
        .where((a) => _matchesYmd(a.checkIn, year, month, day))
        .toList()
      ..sort((a, b) => b.checkIn.compareTo(a.checkIn));
    return List.unmodifiable(rows);
  }

  @override
  Future<int> countAttendance({int? year, int? month, int? day}) async {
    await Future<void>.delayed(_latency);
    return _data.attendance
        .where((a) => _matchesYmd(a.checkIn, year, month, day))
        .length;
  }

  @override
  Future<TrendSeries> checkInTrend({
    required TrendMode mode,
    required int year,
    required int month,
  }) async {
    await Future<void>.delayed(_latency);
    final series = bucketTrend(
      mode,
      _data.attendance.map((a) => (when: a.checkIn, weight: 1)),
      year: year,
      month: month,
    );
    if (mode != TrendMode.daily) return series;
    final b = busiestOf(_data.attendance
        .where((a) => a.checkIn.year == year && a.checkIn.month == month)
        .map((a) => a.checkIn));
    return TrendSeries(
      mode: series.mode,
      values: series.values,
      busiestWeekday: b.weekday,
      busiestHour: b.hour,
    );
  }
}

/// Client-side equivalent of the Supabase `isoDateLikePattern` filter: an
/// omitted component matches anything.
bool _matchesYmd(DateTime d, int? year, int? month, int? day) =>
    (year == null || d.year == year) &&
    (month == null || d.month == month) &&
    (day == null || d.day == day);

class MockDashboardRepository implements DashboardRepository {
  MockDashboardRepository(this._data);
  final MockGymData _data;

  @override
  Future<DashboardSummary> fetchSummary({bool forceRefresh = false}) async {
    await Future<void>.delayed(_latency);
    return _data.buildSummary();
  }

  @override
  Future<Member?> fetchSpotlightMember() async {
    await Future<void>.delayed(_latency);
    return _data.spotlightMember();
  }
}

/// Builds the full set of mock repositories over one shared data set.
GymRepositories buildMockRepositories({DateTime? reference}) {
  final data = MockGymData(reference: reference);
  final memberIndex = MemberIndex();
  return GymRepositories(
    members: CachingMembersRepository(
      MockMembersRepository(data),
      memberIndex: memberIndex,
    ),
    plans: MockPlansRepository(data),
    seance: MockSeanceRepository(data),
    payments: MockPaymentsRepository(data),
    attendance: MockAttendanceRepository(data),
    dashboard: MockDashboardRepository(data),
    memberIndex: memberIndex,
  );
}
