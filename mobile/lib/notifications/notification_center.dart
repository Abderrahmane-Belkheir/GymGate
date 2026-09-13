import 'dart:collection';

import 'package:flutter/widgets.dart';

import 'gym_notification.dart';

/// In-memory notification store (nothing is persisted). Holds the full list for
/// the bell panel and a small queue of banners waiting to be shown.
class NotificationCenter extends ChangeNotifier {
  static const int _maxItems = 60;

  final List<GymNotification> _items = <GymNotification>[];
  final Queue<GymNotification> _bannerQueue = Queue<GymNotification>();

  /// Newest first.
  List<GymNotification> get items => List.unmodifiable(_items);

  int get unreadCount => _items.where((n) => !n.read).length;

  /// The next banner to show, or null. [consumeBanner] advances the queue.
  GymNotification? get pendingBanner =>
      _bannerQueue.isEmpty ? null : _bannerQueue.first;

  void add(GymNotification notification) {
    _items.insert(0, notification);
    if (_items.length > _maxItems) _items.removeRange(_maxItems, _items.length);
    _bannerQueue.add(notification);
    notifyListeners();
  }

  /// Called by the banner host once a banner has finished its show/fly cycle.
  void consumeBanner() {
    if (_bannerQueue.isNotEmpty) _bannerQueue.removeFirst();
    notifyListeners();
  }

  /// Mark everything read (called when the bell panel opens).
  void markAllRead() {
    if (unreadCount == 0) return;
    for (final n in _items) {
      n.read = true;
    }
    notifyListeners();
  }

  void clear() {
    _items.clear();
    _bannerQueue.clear();
    notifyListeners();
  }
}

/// Exposes the [NotificationCenter] to the tree.
class NotificationScope extends InheritedNotifier<NotificationCenter> {
  const NotificationScope({
    super.key,
    required NotificationCenter center,
    required super.child,
  }) : super(notifier: center);

  static NotificationCenter? maybeOf(BuildContext context) => context
      .dependOnInheritedWidgetOfExactType<NotificationScope>()
      ?.notifier;

  static NotificationCenter of(BuildContext context) {
    final center = maybeOf(context);
    assert(center != null, 'No NotificationScope in the widget tree');
    return center!;
  }
}
