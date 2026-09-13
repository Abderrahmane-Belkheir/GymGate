import 'package:flutter/material.dart';

import '../../../core/formatting.dart';
import '../../../data/models.dart';
import '../../../data/repository_scope.dart';
import '../../../i18n/app_strings.dart';
import '../../../theme/gym_colors.dart';
import '../../../theme/gym_tokens.dart';
import '../../../widgets/gym_avatar.dart';
import '../../../widgets/gym_badges.dart';
import 'confirm_cancel_sheet.dart';
import 'renew_membership_sheet.dart';

/// One member, adapted from the desktop table row into a stacked mobile row:
/// avatar + name/phone, a membership meta line (plan chip + date range), a
/// trailing block with remaining days and the status pill, and a `⋯` menu
/// (Renew when the membership isn't active, Cancel when it is).
class MemberListRow extends StatelessWidget {
  const MemberListRow({
    super.key,
    required this.member,
    required this.now,
    this.onChanged,
  });

  final Member member;
  final DateTime now;

  /// Called after the membership is cancelled so the screen can re-filter the
  /// list (the member moves to the Inactive side).
  final VoidCallback? onChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = member.statusFrom(now);
    final remaining = member.displayRemainingDays(now);
    final photo =
        RepositoryScope.maybeOf(context)?.memberIndex.photoFor(member.id) ??
            member.photoBytes;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          GymAvatar(
            initials: member.initials,
            imageBytes: photo,
            size: 44,
          ),
          const SizedBox(width: GymSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  member.fullName,
                  style: theme.textTheme.titleSmall,
                ),
                const SizedBox(height: 3),
                Row(
                  children: [
                    const Icon(Icons.phone_outlined,
                        size: 12, color: GymPalette.textMuted),
                    const SizedBox(width: 5),
                    Flexible(
                      child: Text(
                        member.phone,
                        style: theme.textTheme.bodySmall?.copyWith(fontSize: 12),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: GymSpacing.sm),
                Wrap(
                  spacing: GymSpacing.sm,
                  runSpacing: 6,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    if (member.plan != null) GymPlanChip(member.plan!.name),
                    if (member.membershipStart != null &&
                        member.membershipEnd != null)
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.event_outlined,
                              size: 11, color: GymPalette.textMuted),
                          const SizedBox(width: 4),
                          Text(
                            formatIsoRange(
                              member.membershipStart!,
                              member.membershipEnd!,
                            ),
                            style: theme.textTheme.bodySmall
                                ?.copyWith(fontSize: 11),
                          ),
                        ],
                      ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(width: GymSpacing.sm),
          Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              if (remaining != null) ...[
                Text(
                  context.s.days(remaining),
                  style: theme.textTheme.bodyLarge?.copyWith(fontSize: 12),
                ),
                const SizedBox(height: 4),
              ],
              GymStatusPill(status),
            ],
          ),
          _MemberActionsMenu(member: member, status: status, onChanged: onChanged),
        ],
      ),
    );
  }
}

/// The trailing `⋯` menu. One action, chosen by membership state:
/// active / expiring → **Cancel Membership** (clears the plan + dates server-
/// side, member drops to Inactive); everything else → **Renew** (opens the plan
/// picker — UI only for now).
class _MemberActionsMenu extends StatelessWidget {
  const _MemberActionsMenu({
    required this.member,
    required this.status,
    this.onChanged,
  });

  final Member member;
  final MembershipStatus status;
  final VoidCallback? onChanged;

  bool get _isActive =>
      status == MembershipStatus.active || status == MembershipStatus.expiring;

  Future<void> _cancel(BuildContext context) async {
    final confirmed = await showConfirmCancelSheet(context, member: member);
    if (confirmed != true || !context.mounted) return;

    final messenger = ScaffoldMessenger.of(context);
    final s = context.s;
    try {
      await RepositoryScope.of(context).members.cancelMembership(member.id);
      onChanged?.call();
      messenger.showSnackBar(
        SnackBar(content: Text(s.t('Membership_cancelled'))),
      );
    } catch (e) {
      messenger.showSnackBar(
        SnackBar(
          content: Text('${s.t('Could_not_cancel_the_membership')}: $e'),
        ),
      );
    }
  }

  Future<void> _renew(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    final s = context.s;
    final renewed = await showRenewMembershipSheet(context, member: member);
    if (renewed != true) return;
    onChanged?.call();
    messenger.showSnackBar(
      SnackBar(content: Text(s.t('Membership_renewed'))),
    );
  }

  @override
  Widget build(BuildContext context) {
    final s = context.s;
    final renew = !_isActive;
    final label = renew ? s.t('Renew') : s.t('Cancel_Membership');

    return PopupMenuButton<bool>(
      tooltip: label,
      position: PopupMenuPosition.under,
      padding: EdgeInsets.zero,
      onSelected: (_) => renew ? _renew(context) : _cancel(context),
      itemBuilder: (context) => [
        PopupMenuItem<bool>(
          value: true,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                renew ? Icons.autorenew : Icons.cancel_outlined,
                size: 16,
                color: renew ? GymPalette.success : GymPalette.danger,
              ),
              const SizedBox(width: GymSpacing.sm),
              Text(label),
            ],
          ),
        ),
      ],
      child: const SizedBox(
        width: 32,
        height: 44,
        child: Icon(Icons.more_vert, size: 18, color: GymPalette.textSlate),
      ),
    );
  }
}
