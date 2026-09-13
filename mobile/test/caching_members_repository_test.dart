import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/caching_members_repository.dart';
import 'package:gymgate_app/data/models.dart';
import 'package:gymgate_app/data/repositories.dart';

/// Counts calls so the test can see when the source is actually hit.
class _CountingMembers implements MembersRepository {
  int fetchCount = 0;
  List<Member> members = const [];

  @override
  Future<List<Member>> fetchMembers({bool forceRefresh = false}) async {
    fetchCount++;
    return members;
  }

  @override
  Future<Member?> fetchMember(String id) async =>
      members.where((m) => m.id == id).firstOrNull;

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

Member _m(String id) => Member(
      id: id,
      firstName: 'F$id',
      lastName: 'L$id',
      gender: Gender.male,
      phone: '0',
    );

void main() {
  late _CountingMembers source;
  late CachingMembersRepository repo;

  setUp(() {
    source = _CountingMembers()..members = [_m('1'), _m('2')];
    repo = CachingMembersRepository(source);
  });

  test('caches the list — repeated reads hit the source once', () async {
    await repo.fetchMembers();
    await repo.fetchMembers();
    await repo.fetchMembers();
    expect(source.fetchCount, 1);
  });

  test('forceRefresh refetches and the shared cache then serves the new data',
      () async {
    final first = await repo.fetchMembers();
    expect(first, hasLength(2));

    // Source changes (a member was added on the Members screen).
    source.members = [_m('1'), _m('2'), _m('3')];

    // A plain read still sees the stale cache...
    expect(await repo.fetchMembers(), hasLength(2));

    // ...a forced read (pull-to-refresh) updates it for everyone.
    expect(await repo.fetchMembers(forceRefresh: true), hasLength(3));
    expect(await repo.fetchMembers(), hasLength(3));
    expect(source.fetchCount, 2);
  });

  group('realtime splices — no full refetch', () {
    test('applyMemberUpsert adds a new member, kept ordered by id', () async {
      await repo.fetchMembers(); // cache = [1, 2]
      source.members = [_m('1'), _m('2'), _m('10'), _m('3')];

      await repo.applyMemberUpsert('10');
      await repo.applyMemberUpsert('3');

      expect((await repo.fetchMembers()).map((m) => m.id), ['1', '2', '3', '10']);
      expect(source.fetchCount, 1); // only fetchMember was used, not fetchMembers
    });

    test('applyMemberUpsert replaces an existing member in place', () async {
      await repo.fetchMembers();
      source.members = [
        Member(
          id: '1',
          firstName: 'Changed',
          lastName: 'L1',
          gender: Gender.male,
          phone: '0',
        ),
        _m('2'),
      ];

      await repo.applyMemberUpsert('1');

      final list = await repo.fetchMembers();
      expect(list, hasLength(2));
      expect(list.first.firstName, 'Changed');
    });

    test('applyMemberUpsert of a vanished row falls back to removal', () async {
      await repo.fetchMembers();
      source.members = [_m('2')]; // '1' is gone

      await repo.applyMemberUpsert('1');

      expect((await repo.fetchMembers()).map((m) => m.id), ['2']);
    });

    test('applyMemberRemoval drops the member', () async {
      await repo.fetchMembers();
      await repo.applyMemberRemoval('1');
      expect((await repo.fetchMembers()).map((m) => m.id), ['2']);
    });

    test('both are no-ops before anything is cached', () async {
      await repo.applyMemberUpsert('1');
      await repo.applyMemberRemoval('1');
      expect(source.fetchCount, 0);
    });
  });

  test('a failed fetch is not cached', () async {
    final failing = _FailingOnce();
    final r = CachingMembersRepository(failing);

    await expectLater(r.fetchMembers(), throwsA(isA<StateError>()));
    // Next call retries instead of replaying the cached error.
    expect(await r.fetchMembers(), isEmpty);
  });
}

class _FailingOnce implements MembersRepository {
  bool _failed = false;

  @override
  Future<List<Member>> fetchMembers({bool forceRefresh = false}) async {
    if (!_failed) {
      _failed = true;
      throw StateError('boom');
    }
    return const [];
  }

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
