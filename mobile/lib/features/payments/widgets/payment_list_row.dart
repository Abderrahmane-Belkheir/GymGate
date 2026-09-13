import 'package:flutter/material.dart';

import '../../../core/formatting.dart';
import '../../../data/models.dart';
import '../../../data/repository_scope.dart';
import '../../../i18n/app_strings.dart';
import '../../../theme/gym_colors.dart';
import '../../../theme/gym_tokens.dart';
import '../../../widgets/gym_avatar.dart';
import '../../../widgets/gym_badges.dart';

/// One payment, from the desktop `Paiements` table row (Member · Forfait · Date ·
/// Heure · Montant) collapsed into a mobile row: avatar + name, a muted meta
/// line (plan chip · date · time), and the amount emphasised on the trailing
/// edge.
class PaymentListRow extends StatelessWidget {
  const PaymentListRow({super.key, required this.payment});

  final Payment payment;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final name = payment.memberName.trim();
    final photo = RepositoryScope.maybeOf(context)
        ?.memberIndex
        .photoFor(payment.memberId);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        children: [
          GymAvatar(initials: initialsFrom(name), imageBytes: photo, size: 44),
          const SizedBox(width: GymSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  name.isNotEmpty
                      ? name
                      : context.s.t(
                          payment.seance != null
                              ? 'Walk_in'
                              : 'Unknown_member',
                        ),
                  style: theme.textTheme.titleSmall,
                ),
                const SizedBox(height: 6),
                Wrap(
                  spacing: GymSpacing.sm,
                  runSpacing: 4,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    if (payment.planName != null &&
                        payment.planName!.isNotEmpty)
                      GymPlanChip(payment.planName!)
                    else if (payment.seance != null)
                      GymPlanChip(context.s.t('The_session')),
                    if (payment.seance?.restrictedTo != null)
                      _Meta(
                        icon: Icons.wc,
                        text: context.s.t(
                          payment.seance!.restrictedTo == Gender.female
                              ? 'Female'
                              : 'Male',
                        ),
                      ),
                    if (payment.seance != null)
                      _Meta(
                        icon: payment.seance!.cardio
                            ? Icons.favorite_border
                            : Icons.fitness_center,
                        text: context.s.t(
                          payment.seance!.cardio ? 'With_cardio' : 'Standard',
                        ),
                      ),
                    if (payment.seance != null)
                      _Meta(
                        icon: Icons.sell_outlined,
                        text: formatDzd(payment.seance!.priceDzd),
                      ),
                    _Meta(icon: Icons.event_outlined, text: formatDate(payment.date)),
                    _Meta(icon: Icons.schedule, text: formatTime(payment.date)),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(width: GymSpacing.sm),
          Text(
            formatDzd(payment.amountDzd),
            style: theme.textTheme.bodyLarge?.copyWith(
              fontSize: 14,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _Meta extends StatelessWidget {
  const _Meta({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 11, color: GymPalette.textMuted),
        const SizedBox(width: 4),
        Text(
          text,
          style: Theme.of(context)
              .textTheme
              .bodySmall
              ?.copyWith(fontSize: 11),
        ),
      ],
    );
  }
}
