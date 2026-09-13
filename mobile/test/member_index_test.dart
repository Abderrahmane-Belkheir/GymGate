import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/caching_members_repository.dart';
import 'package:gymgate_app/data/member_index.dart';
import 'package:gymgate_app/data/models.dart';
import 'package:gymgate_app/data/repositories.dart';

Member _member(String id, {List<int>? photo, int? remaining}) => Member(
      id: id,
      firstName: 'First$id',
      lastName: 'Last$id',
      gender: Gender.male,
      phone: '0',
      remainingDays: remaining,
      photoBytes: photo == null ? null : Uint8List.fromList(photo),
    );

class _FakeMembers implements MembersRepository {
  List<Member> members = const [];
  int fetchCount = 0;

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

void main() {
  test('rebuild indexes every member by id — name always, photo when present',
      () {
    final index = MemberIndex()
      ..rebuild([
        _member('1', photo: [1, 2, 3]),
        _member('2'), // no photo
      ]);

    expect(index.length, 2);
    expect(index.nameFor('1'), 'First1 Last1');
    expect(index.photoFor('1'), [1, 2, 3]);
    expect(index.nameFor('2'), 'First2 Last2'); // still indexed
    expect(index.photoFor('2'), isNull);
    expect(index['404'], isNull);
  });

  test('CachingMembersRepository populates the index on fetch', () async {
    final source = _FakeMembers()
      ..members = [_member('1', photo: [1]), _member('2')];
    final index = MemberIndex();
    final repo = CachingMembersRepository(source, memberIndex: index);

    await repo.fetchMembers();
    expect(index.nameFor('1'), 'First1 Last1');
    expect(index.photoFor('1'), [1]);
    expect(index.nameFor('2'), 'First2 Last2');
  });

  test('a forced refresh destroys the old index and rebuilds from the new query',
      () async {
    final source = _FakeMembers()
      ..members = [_member('1', photo: [1]), _member('2', photo: [2])];
    final index = MemberIndex();
    final repo = CachingMembersRepository(source, memberIndex: index);

    await repo.fetchMembers();
    expect(index.photoFor('1'), [1]);

    source.members = [_member('1'), _member('3', photo: [3])];
    await repo.fetchMembers(forceRefresh: true);

    expect(index.photoFor('1'), isNull); // lost the photo
    expect(index.nameFor('2'), isNull); // gone entirely
    expect(index.photoFor('3'), [3]); // new
    expect(index.length, 2);
  });

  group('applyCheckIn — optimistic remaining_days decrement', () {
    test('decrements a limited member in both the cache and the index',
        () async {
      final source = _FakeMembers()
        ..members = [_member('1', remaining: 5), _member('2', remaining: null)];
      final index = MemberIndex();
      final repo = CachingMembersRepository(source, memberIndex: index);
      await repo.fetchMembers();

      await repo.applyCheckIn('1');

      expect((await repo.fetchMembers()).first.remainingDays, 4);
      expect(index['1']!, isNotNull);
      // Unlimited member is untouched.
      await repo.applyCheckIn('2');
      expect((await repo.fetchMembers())[1].remainingDays, isNull);
    });

    test('no-op for an unknown id or before the first fetch', () async {
      final source = _FakeMembers()..members = [_member('1', remaining: 3)];
      final repo = CachingMembersRepository(source);

      await repo.applyCheckIn('1'); // nothing loaded yet
      await repo.fetchMembers();
      await repo.applyCheckIn('404'); // unknown
      expect((await repo.fetchMembers()).first.remainingDays, 3);
    });
  });
}
