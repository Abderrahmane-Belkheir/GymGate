import 'package:flutter/material.dart';

import '../theme/gym_tokens.dart';
import 'gym_card.dart';
import 'gym_icon_tile.dart';

/// White card: icon tile + big value (26/800) + small label (11/700).
///
/// [centered] switches to the wide-card treatment (icon and text centred),
/// used for the full-width revenue tile.
class GymStatCard extends StatelessWidget {
  const GymStatCard({
    super.key,
    required this.value,
    required this.label,
    required this.icon,
    this.tone = GymTileTone.accent,
    this.onTap,
    this.centered = false,
  });

  final String value;
  final String label;
  final IconData icon;
  final GymTileTone tone;
  final VoidCallback? onTap;
  final bool centered;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cross =
        centered ? CrossAxisAlignment.center : CrossAxisAlignment.start;
    final align = centered ? Alignment.center : Alignment.centerLeft;

    return GymCard(
      onTap: onTap,
      padding: EdgeInsets.symmetric(
        horizontal: GymSpacing.lg,
        vertical: centered ? GymSpacing.xl : GymSpacing.lg,
      ),
      child: Column(
        crossAxisAlignment: cross,
        mainAxisSize: centered ? MainAxisSize.min : MainAxisSize.max,
        children: [
          GymIconTile(icon: icon, tone: tone),
          SizedBox(height: centered ? GymSpacing.md : 0),
          if (!centered) const Spacer(),
          Align(
            alignment: align,
            child: FittedBox(
              fit: BoxFit.scaleDown,
              alignment: align,
              child: Text(value, style: theme.textTheme.headlineMedium),
            ),
          ),
          const SizedBox(height: GymSpacing.xs),
          Text(
            label,
            textAlign: centered ? TextAlign.center : TextAlign.start,
            style: theme.textTheme.labelSmall,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}
