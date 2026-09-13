import 'package:flutter/material.dart';

import '../../../core/formatting.dart';
import '../../../data/models.dart';
import '../../../i18n/app_strings.dart';
import '../../../theme/gym_colors.dart';
import '../../../theme/gym_tokens.dart';
import '../../../widgets/gym_card.dart';
import '../../../widgets/gym_ghost_icon_button.dart';

/// A single membership plan, following the desktop plan card: name + billing
/// period, a blue-soft price block, then labelled spec rows. Shows a pencil
/// (edit) action when [onEdit] is given.
class PlanCard extends StatelessWidget {
  const PlanCard({super.key, required this.plan, this.onEdit});

  final Plan plan;
  final VoidCallback? onEdit;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = context.s;

    return GymCard(
      padding: const EdgeInsets.all(GymSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      plan.name,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      plan.billingLabel,
                      style: theme.textTheme.bodySmall?.copyWith(fontSize: 12),
                    ),
                  ],
                ),
              ),
              if (onEdit != null)
                GymGhostIconButton(
                  icon: Icons.edit_outlined,
                  tooltip: s.t('Edit_Plan'),
                  onPressed: onEdit,
                ),
            ],
          ),
          const SizedBox(height: GymSpacing.md),
          const Divider(height: 1),
          const SizedBox(height: GymSpacing.md),
          _PriceBlock(plan.priceDzd),
          const SizedBox(height: GymSpacing.md),
          _SpecRow(
            label: s.t('Days_per_month'),
            value: Text(
              plan.isUnlimited ? s.t('Unlimited') : plan.visitsLabel,
              style: theme.textTheme.bodyLarge,
            ),
          ),
          const SizedBox(height: GymSpacing.sm),
          _SpecRow(
            label: s.t('Cardio_Included'),
            value: _YesNoPill(plan.cardioIncluded),
          ),
        ],
      ),
    );
  }
}

class _PriceBlock extends StatelessWidget {
  const _PriceBlock(this.priceDzd);

  final int priceDzd;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(GymSpacing.md),
      decoration: BoxDecoration(
        color: GymPalette.accentSoft,
        borderRadius: BorderRadius.circular(GymRadii.priceBlock),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            context.s.t('Price'),
            style: theme.textTheme.labelSmall?.copyWith(
              color: GymPalette.accent,
            ),
          ),
          const SizedBox(height: 4),
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text(
                formatThousands(priceDzd, separator: ','),
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

class _SpecRow extends StatelessWidget {
  const _SpecRow({required this.label, required this.value});

  final String label;
  final Widget value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Text(
          label,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: GymPalette.textMuted,
          ),
        ),
        const Spacer(),
        value,
      ],
    );
  }
}

class _YesNoPill extends StatelessWidget {
  const _YesNoPill(this.value);

  final bool value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: GymPalette.pageTint,
        borderRadius: BorderRadius.circular(GymRadii.pill),
      ),
      child: Text(
        context.s.t(value ? 'Yes' : 'No'),
        style: const TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w700,
          color: GymPalette.textSlate,
        ),
      ),
    );
  }
}
