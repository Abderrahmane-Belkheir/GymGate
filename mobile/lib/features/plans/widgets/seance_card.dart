import 'package:flutter/material.dart';

import '../../../core/formatting.dart';
import '../../../data/models.dart';
import '../../../i18n/app_strings.dart';
import '../../../theme/gym_colors.dart';
import '../../../theme/gym_tokens.dart';
import '../../../widgets/gym_card.dart';
import '../../../widgets/gym_ghost_icon_button.dart';

/// One single-session ("séance") price, following the desktop "الحصة" cards: a
/// pill saying whether it includes cardio, then the price. Shows a pencil
/// (update price) action when [onEdit] is given.
class SeanceCard extends StatelessWidget {
  const SeanceCard({super.key, required this.seance, this.onEdit});

  final Seance seance;
  final VoidCallback? onEdit;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = context.s;
    final cardio = seance.cardio;

    return GymCard(
      padding: const EdgeInsets.all(GymSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: cardio ? GymPalette.successSoft : GymPalette.pageTint,
                    borderRadius: BorderRadius.circular(GymRadii.pill),
                  ),
                  child: Text(
                    s.t(cardio ? 'With_cardio' : 'Standard'),
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: cardio ? GymPalette.success : GymPalette.textSlate,
                    ),
                  ),
                ),
              ),
              if (onEdit != null)
                GymGhostIconButton(
                  icon: Icons.edit_outlined,
                  tooltip: s.t('Update_Price'),
                  onPressed: onEdit,
                ),
            ],
          ),
          const SizedBox(height: GymSpacing.md),
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text(
                formatThousands(seance.priceDzd, separator: ','),
                style: theme.textTheme.headlineMedium?.copyWith(
                  color: GymPalette.accent,
                ),
              ),
              const SizedBox(width: 5),
              Text(
                'DZD',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: GymPalette.accent.withValues(alpha: 0.7),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
