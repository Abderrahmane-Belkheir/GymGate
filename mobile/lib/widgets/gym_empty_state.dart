import 'package:flutter/material.dart';

import '../theme/gym_colors.dart';
import '../theme/gym_tokens.dart';
import 'gym_icon_tile.dart';

/// Centred, muted placeholder for screens and lists with nothing to show yet.
class GymEmptyState extends StatelessWidget {
  const GymEmptyState({
    super.key,
    required this.message,
    this.title,
    this.icon = Icons.inbox_outlined,
  });

  final String message;
  final String? title;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: GymSpacing.lg,
        vertical: GymSpacing.xxl,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          GymIconTile(icon: icon, tone: GymTileTone.neutral, size: 48),
          const SizedBox(height: GymSpacing.md),
          if (title != null) ...[
            Text(title!, style: theme.textTheme.titleMedium),
            const SizedBox(height: GymSpacing.xs),
          ],
          Text(
            message,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: GymPalette.textMuted,
            ),
          ),
        ],
      ),
    );
  }
}
