import 'package:flutter/material.dart';

import '../theme/gym_colors.dart';
import '../theme/gym_tokens.dart';

/// White surface, radius 12, hairline border, soft resting shadow.
/// The single card primitive — do not nest one inside another.
class GymCard extends StatelessWidget {
  const GymCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(GymSpacing.cardPad),
    this.onTap,
    this.emphasis = GymCardEmphasis.resting,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;
  final GymCardEmphasis emphasis;

  @override
  Widget build(BuildContext context) {
    final shadow = switch (emphasis) {
      GymCardEmphasis.resting => GymShadows.card,
      GymCardEmphasis.header => GymShadows.header,
    };

    final decoration = BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(GymRadii.card),
      border: Border.all(color: GymPalette.hairline),
      boxShadow: shadow,
    );

    final content = Padding(padding: padding, child: child);

    if (onTap == null) {
      return DecoratedBox(decoration: decoration, child: content);
    }

    return DecoratedBox(
      decoration: decoration,
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(GymRadii.card),
          child: content,
        ),
      ),
    );
  }
}

enum GymCardEmphasis { resting, header }
