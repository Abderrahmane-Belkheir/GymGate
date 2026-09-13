import 'package:flutter/material.dart';

import '../../../data/models.dart';
import '../../../i18n/app_strings.dart';
import '../../../theme/gym_colors.dart';
import '../../../theme/gym_tokens.dart';
import '../../../widgets/gym_pill_button.dart';

/// A confirm card for cancelling [member]'s membership. Resolves to `true` if
/// the person confirms, `null` otherwise.
Future<bool?> showConfirmCancelSheet(
  BuildContext context, {
  required Member member,
}) {
  return showModalBottomSheet<bool>(
    context: context,
    showDragHandle: true,
    builder: (context) => _ConfirmCancelSheet(member: member),
  );
}

class _ConfirmCancelSheet extends StatelessWidget {
  const _ConfirmCancelSheet({required this.member});

  final Member member;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = context.s;

    return Padding(
      padding: EdgeInsets.only(
        left: GymSpacing.pageH,
        right: GymSpacing.pageH,
        top: GymSpacing.sm,
        bottom: GymSpacing.lg + MediaQuery.viewInsetsOf(context).bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: const BoxDecoration(
                  color: GymPalette.dangerSoft,
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.person_remove_alt_1_outlined,
                  size: 18,
                  color: GymPalette.danger,
                ),
              ),
              const SizedBox(width: GymSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      s.t('Cancel_Membership?'),
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      member.fullName,
                      style: theme.textTheme.bodySmall?.copyWith(
                        fontSize: 12,
                        color: GymPalette.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: GymSpacing.md),
          Text(
            s.t('Cancel_membership_warning'),
            style: theme.textTheme.bodyMedium?.copyWith(
              color: GymPalette.textSlate,
            ),
          ),
          const SizedBox(height: GymSpacing.lg),
          GymPillButton(
            label: s.t('Cancel_Membership'),
            icon: Icons.person_remove_alt_1_outlined,
            variant: GymButtonVariant.danger,
            expand: true,
            onPressed: () => Navigator.of(context).pop(true),
          ),
          const SizedBox(height: GymSpacing.sm),
          GymPillButton(
            label: s.t('Cancel'),
            expand: true,
            onPressed: () => Navigator.of(context).pop(),
          ),
        ],
      ),
    );
  }
}
