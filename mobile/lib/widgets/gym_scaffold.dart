import 'package:flutter/material.dart';

import '../theme/gym_tokens.dart';

/// Tinted page + standard mobile padding + vertical scroll. Screens compose
/// their stacked surfaces (header card -> controls -> content) as [children]
/// separated by [GymSpacing.sectionGap].
class GymScaffold extends StatelessWidget {
  const GymScaffold({
    super.key,
    required this.children,
    this.sectionGap = GymSpacing.sectionGap,
    this.onRefresh,
  });

  final List<Widget> children;
  final double sectionGap;
  final Future<void> Function()? onRefresh;

  @override
  Widget build(BuildContext context) {
    final spaced = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      spaced.add(children[i]);
      if (i != children.length - 1) spaced.add(SizedBox(height: sectionGap));
    }

    Widget list = ListView(
      padding: EdgeInsets.fromLTRB(
        GymSpacing.pageH,
        GymSpacing.pageV,
        GymSpacing.pageH,
        GymSpacing.pageV + MediaQuery.paddingOf(context).bottom,
      ),
      physics: const AlwaysScrollableScrollPhysics(),
      children: spaced,
    );

    if (onRefresh != null) {
      list = RefreshIndicator(
        onRefresh: onRefresh!,
        color: Theme.of(context).colorScheme.primary,
        child: list,
      );
    }

    return list;
  }
}
