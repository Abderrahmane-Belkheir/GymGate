/// Remembers rows this device just wrote to Supabase, so the Realtime echo of
/// our own change doesn't raise a "new plan" / "payment recorded" banner at the
/// person who just made it.
///
/// A repository calls [mark] right before an insert/update; [RealtimeService]
/// calls [consume] when a change arrives and skips the notification on a hit.
/// Entries self-expire after [_ttl] (the echo normally lands within a second).
class SelfWriteRegistry {
  SelfWriteRegistry._();

  static final SelfWriteRegistry instance = SelfWriteRegistry._();

  static const Duration _ttl = Duration(seconds: 15);

  final Map<String, DateTime> _entries = <String, DateTime>{};

  /// Record that this device wrote `table`/`id`. No-op for a null id.
  void mark(String table, Object? id) {
    if (id == null) return;
    _sweep();
    _entries['$table/$id'] = DateTime.now();
  }

  /// Whether `table`/`id` was written here within the TTL — and removes the
  /// entry so a later, unrelated change to the same row still notifies.
  bool consume(String table, Object? id) {
    if (id == null) return false;
    _sweep();
    return _entries.remove('$table/$id') != null;
  }

  void _sweep() {
    final now = DateTime.now();
    _entries.removeWhere((_, at) => now.difference(at) > _ttl);
  }
}
