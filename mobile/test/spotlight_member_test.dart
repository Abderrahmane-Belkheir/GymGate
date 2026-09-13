import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/mock/mock_repositories.dart';
import 'package:gymgate_app/data/models.dart';
import 'package:gymgate_app/data/repositories.dart';
import 'package:gymgate_app/data/supabase/supabase_dashboard_repository.dart';
import 'package:gymgate_app/data/trend.dart';

class _FakeAttendance implements AttendanceRepository {
  _FakeAttendance({required this.today, required this.allTime});

  final List<AttendanceEntry> today;
  final List<AttendanceEntry> allTime;

  @override
  Future<List<AttendanceEntry>> findAttendance(
      {int? year, int? month, int? day}) async {
    // A y/m/d filter → "today"; no filter → everything.
    return (year != null && month != null && day != null) ? today : allTime;
  }

  @override
  Future<List<AttendanceEntry>> fetchRecentAttendance() => findAttendance();

  @override
  Future<int> countAttendance({int? year, int? month, int? day}) async =>
      findAttendance(year: year, month: month, day: day)
          .then((r) => r.length);

  @override
  Future<TrendSeries> checkInTrend({
    required TrendMode mode,
    required int year,
    required int month,
  }) async =>
      TrendSeries(mode: mode, values: const []);
}

class _FakeMembers implements MembersRepository {
  _FakeMembers(this._members);
  final List<Member> _members;

  @override
  Future<List<Member>> fetchMembers({bool forceRefresh = false}) async =>
      _members;

  @override
  Future<Member?> fetchMember(String id) async =>
      _members.where((m) => m.id == id).firstOrNull;

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

class _FakePayments implements PaymentsRepository {
  @override
  Future<List<Payment>> fetchPayments() async => const [];
  @override
  Future<List<Payment>> findPayments({int? year, int? month, int? day}) async =>
      const [];
  @override
  Future<int> sumAmount({int? year, int? month, int? day}) async => 0;
  @override
  Future<TrendSeries> revenueTrend({
    required TrendMode mode,
    required int year,
    required int month,
  }) async =>
      TrendSeries(mode: mode, values: const []);
}

AttendanceEntry _entry(String memberId, DateTime at) => AttendanceEntry(
      id: '$memberId-$at',
      memberId: memberId,
      memberName: 'M$memberId',
      checkIn: at,
    );

Member _m(String id) => Member(
      id: id,
      firstName: 'F$id',
      lastName: 'L$id',
      gender: Gender.male,
      phone: '0',
    );

void main() {
  final now = DateTime(2026, 8, 30, 15);

  SupabaseDashboardRepository build(_FakeAttendance attendance) =>
      SupabaseDashboardRepository(
        members: _FakeMembers([_m('X'), _m('Y'), _m('Z')]),
        attendance: attendance,
        payments: _FakePayments(),
        reference: now,
      );

  test('spotlight is the latest check-in TODAY, not the latest overall',
      () async {
    final repo = build(_FakeAttendance(
      today: [
        _entry('X', DateTime(2026, 8, 30, 14, 30)), // latest today
        _entry('Z', DateTime(2026, 8, 30, 9)),
      ],
      allTime: [
        _entry('Y', DateTime(2026, 8, 31, 8)), // "later" but not today
        _entry('X', DateTime(2026, 8, 30, 14, 30)),
      ],
    ));

    final spotlight = await repo.fetchSpotlightMember();
    expect(spotlight?.id, 'X');
  });

  test('no check-in today → no spotlight', () async {
    final repo = build(_FakeAttendance(
      today: const [],
      allTime: [_entry('Y', DateTime(2026, 8, 29, 20))],
    ));

    expect(await repo.fetchSpotlightMember(), isNull);
  });

  test('mock: spotlight is the member behind the most recent check-in today',
      () async {
    final repos = buildMockRepositories(reference: DateTime(2026, 8, 29, 19));
    final spotlight = await repos.dashboard.fetchSpotlightMember();
    // a_01 (18:50) is the latest seeded check-in on the reference day → m_06.
    expect(spotlight?.id, 'm_06');
  });
}
