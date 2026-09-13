import 'package:flutter/material.dart';

import '../../../core/formatting.dart';
import '../../../data/models.dart';
import '../../../i18n/app_strings.dart';
import '../../../theme/gym_colors.dart';
import '../../../theme/gym_tokens.dart';
import '../../../widgets/gym_card.dart';
import '../../../widgets/gym_icon_tile.dart';
import '../../../widgets/gym_stat_card.dart';

/// Home's "Today's statistics" section, following the desktop panel layout:
/// title + subtitle on the left, a "Real-time" pill on the right, a 2x2 grid of
/// stat cards, then a full-width centred revenue card.
class TodaysStatistics extends StatelessWidget {
  const TodaysStatistics({super.key, required this.summary});

  final DashboardSummary summary;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = context.s;

    final gridCards = <Widget>[
      GymStatCard(
        value: '${summary.checkInsToday}',
        label: s.t('Check-ins'),
        icon: Icons.login_outlined,
        tone: GymTileTone.accent,
      ),
      GymStatCard(
        value: '${summary.renewalsToday}',
        label: s.t('Renewed_Subs'),
        icon: Icons.autorenew,
        tone: GymTileTone.success,
      ),
      GymStatCard(
        value: '${summary.expiredToday}',
        label: s.t('Expired_Today'),
        icon: Icons.event_busy_outlined,
        tone: GymTileTone.warning,
      ),
      GymStatCard(
        value: '${summary.newMembersToday}',
        label: s.t('New_Members'),
        icon: Icons.person_add_alt_1_outlined,
        tone: GymTileTone.accent,
      ),
    ];

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
                    Text(s.t("Today's_Statistics"),
                        style: theme.textTheme.titleMedium),
                    const SizedBox(height: 2),
                    Text(
                      s.t('Live_gym_activity'),
                      style: theme.textTheme.bodySmall?.copyWith(fontSize: 12),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: GymSpacing.md),
              const _RealtimePill(),
            ],
          ),
          const SizedBox(height: GymSpacing.lg),
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: gridCards.length,
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              mainAxisSpacing: GymSpacing.md,
              crossAxisSpacing: GymSpacing.md,
              mainAxisExtent: 148,
            ),
            itemBuilder: (_, i) => gridCards[i],
          ),
          const SizedBox(height: GymSpacing.sm),
          GymStatCard(
            value: formatDzd(summary.revenueTodayDzd),
            label: s.t('Revenue'),
            icon: Icons.payments_outlined,
            tone: GymTileTone.success,
            centered: true,
          ),
        ],
      ),
    );
  }
}

class _RealtimePill extends StatelessWidget {
  const _RealtimePill();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: GymPalette.pageTint,
        borderRadius: BorderRadius.circular(GymRadii.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: const BoxDecoration(
              color: GymPalette.success,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 6),
          Text(
            context.s.t('Real-time'),
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w700,
              color: GymPalette.textSlate,
            ),
          ),
        ],
      ),
    );
  }
}
