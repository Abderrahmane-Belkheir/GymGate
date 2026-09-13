import 'member_index.dart';
import 'models.dart';
import 'repositories.dart';

/// Wraps another [MembersRepository] with a single in-memory cache of the member
/// list, shared by everything that reads members through the same instance —
/// the Members screen and the Home dashboard.
///
/// `fetchMembers()` serves the cache; `fetchMembers(forceRefresh: true)` (a
/// pull-to-refresh) refetches and repopulates it. Realtime `members` events are
/// applied surgically instead — [applyMemberUpsert] / [applyMemberRemoval]
/// splice a single row so a check-in's `remaining_days` tick doesn't re-download
/// every member (and photo). Every path rebuilds the shared [memberIndex] (name
/// + photo by id). Failed fetches are not cached.
class CachingMembersRepository implements MembersRepository {
  CachingMembersRepository(this._inner, {this.memberIndex});

  final MembersRepository _inner;

  /// Rebuilt from every (re)fetch so rows / realtime events can resolve a
  /// member's name and photo by id.
  final MemberIndex? memberIndex;

  Future<List<Member>>? _cache;

  @override
  Future<List<Member>> fetchMembers({bool forceRefresh = false}) {
    if (forceRefresh) _cache = null;
    return _cache ??= _load();
  }

  Future<List<Member>> _load() async {
    try {
      final list = await _inner.fetchMembers();
      memberIndex?.rebuild(list);
      return list;
    } catch (_) {
      _cache = null; // don't hold on to a failure
      rethrow;
    }
  }

  @override
  Future<Member?> fetchMember(String id) async {
    final cached = _cache;
    if (cached != null) {
      try {
        for (final m in await cached) {
          if (m.id == id) return m;
        }
      } catch (_) {
        // Cache resolved to an error — fall back to a direct fetch.
      }
    }
    return _inner.fetchMember(id);
  }

  @override
  Future<void> applyCheckIn(String memberId) async {
    final cached = _cache;
    if (cached == null) return;
    final List<Member> list;
    try {
      list = await cached;
    } catch (_) {
      return;
    }
    final i = list.indexWhere((m) => m.id == memberId);
    if (i < 0) return;
    final member = list[i];
    if (member.remainingDays == null) return; // unlimited — nothing to decrement

    final updated = [...list];
    updated[i] = member.withRemainingDays(member.remainingDays! - 1);
    _cache = Future<List<Member>>.value(List.unmodifiable(updated));
    memberIndex?.rebuild(updated);
  }

  @override
  Future<void> applyMemberUpsert(String id) async {
    final cached = _cache;
    if (cached == null) return; // nothing loaded yet — the next fetch includes it
    final List<Member> list;
    try {
      list = await cached;
    } catch (_) {
      return;
    }
    Member? fresh;
    try {
      fresh = await _inner.fetchMember(id);
    } catch (_) {
      return; // leave the cache untouched on a failed lookup
    }
    if (fresh == null) {
      // The row was deleted between the event and our fetch — treat as removal.
      await applyMemberRemoval(id);
      return;
    }

    final updated = [...list];
    final i = updated.indexWhere((m) => m.id == id);
    if (i >= 0) {
      updated[i] = fresh;
    } else {
      updated
        ..add(fresh)
        ..sort(_byIdAsc); // keep the source's `order('id')` ordering
    }
    _cache = Future<List<Member>>.value(List.unmodifiable(updated));
    memberIndex?.rebuild(updated);
  }

  @override
  Future<void> cancelMembership(String id) async {
    await _inner.cancelMembership(id);
    // Pull the now-cleared row (no plan / dates) back into the cache so the
    // Members screen re-filters it onto the Inactive side immediately.
    await applyMemberUpsert(id);
  }

  @override
  Future<void> renewMembership({
    required String memberId,
    required Plan plan,
  }) async {
    await _inner.renewMembership(memberId: memberId, plan: plan);
    // Pull the renewed row (plan + fresh dates) back into the cache so the
    // member re-appears on the Active side immediately.
    await applyMemberUpsert(memberId);
  }

  @override
  Future<void> applyMemberRemoval(String id) async {
    final cached = _cache;
    if (cached == null) return;
    final List<Member> list;
    try {
      list = await cached;
    } catch (_) {
      return;
    }
    final updated = list.where((m) => m.id != id).toList();
    if (updated.length == list.length) return; // wasn't cached — nothing to do
    _cache = Future<List<Member>>.value(List.unmodifiable(updated));
    memberIndex?.rebuild(updated);
  }

  static int _byIdAsc(Member a, Member b) {
    final ai = int.tryParse(a.id);
    final bi = int.tryParse(b.id);
    if (ai != null && bi != null) return ai.compareTo(bi);
    return a.id.compareTo(b.id);
  }
}
