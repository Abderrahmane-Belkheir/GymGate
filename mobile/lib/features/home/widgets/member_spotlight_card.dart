import 'package:flutter/material.dart';

import '../../../core/formatting.dart';
import '../../../data/models.dart';
import '../../../data/repository_scope.dart';
import '../../../i18n/app_strings.dart';
import '../../../theme/gym_colors.dart';
import '../../../theme/gym_tokens.dart';
import '../../../widgets/gym_avatar.dart';
import '../../../widgets/gym_badges.dart';
import '../../../widgets/gym_card.dart';

/// Home's member detail card. On desktop this is fed by face-recognition; on
/// mobile it surfaces the most recent check-in *today*. When nobody has checked
/// in yet, [member] is null and the card shows a "No Person" placeholder (like
/// the desktop). Read-only.
class MemberSpotlightCard extends StatelessWidget {
  const MemberSpotlightCard({
    super.key,
    required this.member,
    this.checkInAt,
    this.now,
  });

  final Member? member;
  final DateTime? checkInAt;
  final DateTime? now;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final c = context.gymColors;
    final s = context.s;
    final reference = now ?? DateTime.now();
    final m = member;

    final name = m?.fullName ?? s.t('No_Person');
    final phone = m?.phone ?? '0xxxxxxxxx';
    final planName = m?.plan?.name ?? 'demo';
    final time = checkInAt ?? (m == null ? reference : null);
    final photo = m == null
        ? null
        : RepositoryScope.maybeOf(context)?.memberIndex.photoFor(m.id) ??
            m.photoBytes;
    final remaining = m?.displayRemainingDays(reference);

    return GymCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.bolt_outlined, size: 14, color: c.slateLabel),
              const SizedBox(width: 5),
              Text(s.t('LAST_CHECK_IN'), style: theme.textTheme.labelSmall),
              const Spacer(),
              if (time != null)
                Text(
                  formatTime(time),
                  style: theme.textTheme.bodySmall?.copyWith(fontSize: 12),
                ),
            ],
          ),
          const SizedBox(height: GymSpacing.md),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              GymAvatar(
                initials: initialsFrom(name),
                imageBytes: photo,
                size: 64,
              ),
              const SizedBox(width: GymSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Full name — shown in full, wraps rather than truncating.
                    Text(name, style: theme.textTheme.titleLarge),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        const Icon(Icons.phone_outlined,
                            size: 12, color: GymPalette.textMuted),
                        const SizedBox(width: 5),
                        Flexible(
                          child: Text(
                            phone,
                            style: theme.textTheme.bodySmall
                                ?.copyWith(fontSize: 12),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(width: GymSpacing.sm),
              // Only a SUCCESS recognition writes an attendance row, so this is
              // always a granted check-in — never the member's current state.
              GymSuccessPill(s.t('Checked_In')),
            ],
          ),
          const SizedBox(height: GymSpacing.md),
          const Divider(height: 1),
          const SizedBox(height: GymSpacing.md),
          Wrap(
            spacing: GymSpacing.sm,
            runSpacing: GymSpacing.sm,
            children: [
              _InfoChip(caption: s.t('Plan'), value: planName),
              _InfoChip(
                caption: s.t('EXPIRES'),
                value: m?.membershipEnd != null
                    ? formatDate(m!.membershipEnd!)
                    : '—',
              ),
              _InfoChip(
                caption: s.t('Remaining'),
                // Always the raw day count — never "Expires today" / "Expired…".
                value: m == null || m.plan == null
                    ? s.t('None')
                    : (remaining == null
                        ? s.t('Unlimited')
                        : s.days(remaining)),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _InfoChip extends StatelessWidget {
  const _InfoChip({required this.caption, required this.value});

  final String caption;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: GymPalette.pageTint,
        borderRadius: BorderRadius.circular(GymRadii.toggleOption),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            caption.toUpperCase(),
            style: theme.textTheme.labelSmall?.copyWith(fontSize: 10),
          ),
          const SizedBox(height: 2),
          Text(
            value,
            style: theme.textTheme.bodyLarge,
          ),
        ],
      ),
    );
  }
}
