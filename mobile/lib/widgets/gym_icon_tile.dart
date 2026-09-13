import 'package:flutter/material.dart';

import '../theme/gym_colors.dart';
import '../theme/gym_tokens.dart';

enum GymTileTone { accent, success, warning, danger, neutral }

/// Rounded-square (~40, radius 12) with a soft semantic background and a
/// matching-colour glyph.
class GymIconTile extends StatelessWidget {
  const GymIconTile({
    super.key,
    required this.icon,
    this.tone = GymTileTone.accent,
    this.size = 40,
  });

  final IconData icon;
  final GymTileTone tone;
  final double size;

  @override
  Widget build(BuildContext context) {
    final c = context.gymColors;
    final (Color fg, Color bg) = switch (tone) {
      GymTileTone.accent => (GymPalette.accent, c.accentSoft),
      GymTileTone.success => (c.success, c.successSoft),
      GymTileTone.warning => (c.warning, c.warningSoft),
      GymTileTone.danger => (c.danger, c.dangerSoft),
      GymTileTone.neutral => (GymPalette.textSlate, GymPalette.pageTint),
    };

    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(GymRadii.card),
      ),
      child: Icon(icon, size: size * 0.45, color: fg),
    );
  }
}
