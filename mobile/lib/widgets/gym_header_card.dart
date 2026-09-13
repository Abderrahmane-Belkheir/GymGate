import 'package:flutter/material.dart';

import '../theme/gym_colors.dart';
import '../theme/gym_tokens.dart';
import 'gym_card.dart';

/// The accent-bar header card every screen opens with:
/// `[4px blue bar] [title / subtitle] ...... [trailing context]`.
class GymHeaderCard extends StatelessWidget {
  const GymHeaderCard({
    super.key,
    required this.title,
    this.subtitle,
    this.trailing,
  });

  final String title;
  final String? subtitle;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return GymCard(
      emphasis: GymCardEmphasis.header,
      padding: const EdgeInsets.symmetric(horizontal: GymSpacing.lg, vertical: 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Container(
            width: 4,
            height: 40,
            decoration: BoxDecoration(
              color: GymPalette.accent,
              borderRadius: BorderRadius.circular(GymRadii.accentBar),
            ),
          ),
          const SizedBox(width: GymSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  title,
                  style: theme.textTheme.displaySmall,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                if (subtitle != null) ...[
                  const SizedBox(height: 2),
                  Text(
                    subtitle!,
                    style: theme.textTheme.bodySmall?.copyWith(fontSize: 12),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ],
            ),
          ),
          if (trailing != null) ...[
            const SizedBox(width: GymSpacing.md),
            trailing!,
          ],
        ],
      ),
    );
  }
}
