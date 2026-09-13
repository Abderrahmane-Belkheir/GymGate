import 'package:flutter/foundation.dart';

import 'models.dart';

/// One member's display bits, keyed by id.
@immutable
class MemberIndexEntry {
  const MemberIndexEntry({required this.name, this.photoBytes});

  final String name;
  final Uint8List? photoBytes;
}

/// In-memory `memberId → {name, photo}` index, rebuilt from every members fetch.
///
/// It lets anything that only carries a member id — an attendance row, a payment
/// row, a realtime `member_id` in a change event — resolve the member's name and
/// photo in O(1) without another query. A forced members refresh clears the map
/// and repopulates it.
class MemberIndex {
  final Map<String, MemberIndexEntry> _byId = <String, MemberIndexEntry>{};

  int get length => _byId.length;

  MemberIndexEntry? operator [](String memberId) => _byId[memberId];

  Uint8List? photoFor(String memberId) => _byId[memberId]?.photoBytes;

  String? nameFor(String memberId) => _byId[memberId]?.name;

  /// Drop everything and repopulate from [members] — the result of the latest
  /// `MembersRepository.fetchMembers()` call.
  void rebuild(Iterable<Member> members) {
    _byId.clear();
    for (final m in members) {
      _byId[m.id] = MemberIndexEntry(name: m.fullName, photoBytes: m.photoBytes);
    }
  }
}
