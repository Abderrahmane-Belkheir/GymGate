import 'package:flutter/widgets.dart';

/// Per-table change counters, bumped by [RealtimeService] whenever Supabase
/// Realtime reports a row change. Live screens watch the tables they care about
/// and re-query when the count moves.
class RealtimeBus extends ChangeNotifier {
  final Map<String, int> _revisions = <String, int>{};

  /// The number of realtime changes seen for [table] since app start.
  int revisionOf(String table) => _revisions[table] ?? 0;

  /// Combined revision across [tables] — changes whenever any of them do.
  int revisionOfAll(Iterable<String> tables) =>
      tables.fold(0, (sum, t) => sum + revisionOf(t));

  void bump(String table) {
    _revisions[table] = revisionOf(table) + 1;
    notifyListeners();
  }
}

/// Exposes the [RealtimeBus] to the tree; dependents rebuild on every change.
class RealtimeScope extends InheritedNotifier<RealtimeBus> {
  const RealtimeScope({
    super.key,
    required RealtimeBus bus,
    required super.child,
  }) : super(notifier: bus);

  static RealtimeBus? maybeOf(BuildContext context) => context
      .dependOnInheritedWidgetOfExactType<RealtimeScope>()
      ?.notifier;

  static RealtimeBus of(BuildContext context) {
    final bus = maybeOf(context);
    assert(bus != null, 'No RealtimeScope in the widget tree');
    return bus!;
  }
}

/// Mix into a screen `State` to re-query when its [realtimeTables] change.
///
/// Call [watchRealtime] at the end of `didChangeDependencies` (which re-runs
/// whenever the bus notifies). The first call just records the baseline.
mixin RealtimeReload<T extends StatefulWidget> on State<T> {
  int? _seenRevision;

  /// Tables whose changes should trigger [onRealtimeChange].
  List<String> get realtimeTables;

  /// Re-fetch this screen's data — ideally without flashing a loading state.
  void onRealtimeChange();

  void watchRealtime(BuildContext context) {
    final bus = RealtimeScope.maybeOf(context);
    if (bus == null) return; // e.g. an isolated widget test — no realtime
    final revision = bus.revisionOfAll(realtimeTables);
    if (_seenRevision == null) {
      _seenRevision = revision;
      return;
    }
    if (revision != _seenRevision) {
      _seenRevision = revision;
      onRealtimeChange();
    }
  }
}
