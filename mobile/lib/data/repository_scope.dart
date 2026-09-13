import 'package:flutter/widgets.dart';

import 'repositories.dart';

/// The single integration seam between the UI and its data source.
///
/// Screens read their data through `RepositoryScope.of(context)`. Swapping the
/// mock layer for a real Supabase/API layer later is a one-line change at the
/// app root — no screen needs to be touched.
class RepositoryScope extends InheritedWidget {
  const RepositoryScope({
    super.key,
    required this.repositories,
    required super.child,
  });

  final GymRepositories repositories;

  static GymRepositories of(BuildContext context) {
    final repositories = maybeOf(context);
    assert(repositories != null, 'No RepositoryScope found in the widget tree');
    return repositories!;
  }

  /// Like [of] but returns null when there is no scope — for widgets (avatars)
  /// that read an optional bit of data and can render without it.
  static GymRepositories? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<RepositoryScope>()?.repositories;

  @override
  bool updateShouldNotify(RepositoryScope oldWidget) =>
      oldWidget.repositories != repositories;
}
