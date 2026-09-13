import 'package:flutter/material.dart';

import '../../../core/formatting.dart';
import '../../../data/models.dart';
import '../../../data/repository_scope.dart';
import '../../../i18n/app_strings.dart';
import '../../../theme/gym_colors.dart';
import '../../../theme/gym_tokens.dart';
import '../../../widgets/gym_empty_state.dart';
import '../../../widgets/gym_pill_button.dart';

/// Opens the renew-membership plan picker for [member]: the plans for the
/// member's gender in a scrollable list. Confirming updates the member onto the
/// chosen plan (fresh start/end dates + remaining days) and records the
/// payment. Resolves to `true` once that write succeeds, `null` if dismissed.
Future<bool?> showRenewMembershipSheet(
  BuildContext context, {
  required Member member,
}) {
  return showModalBottomSheet<bool>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) => _RenewSheet(member: member),
  );
}

class _RenewSheet extends StatefulWidget {
  const _RenewSheet({required this.member});

  final Member member;

  @override
  State<_RenewSheet> createState() => _RenewSheetState();
}

class _RenewSheetState extends State<_RenewSheet> {
  Future<List<Plan>>? _future;
  Plan? _selected;
  bool _saving = false;
  String? _error;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _future ??= RepositoryScope.of(context).plans.fetchPlans();
  }

  Future<void> _confirm() async {
    final plan = _selected;
    if (plan == null || _saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await RepositoryScope.of(context).members.renewMembership(
            memberId: widget.member.id,
            plan: plan,
          );
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = '${context.s.t('Could_not_renew_the_membership')}: $e';
        });
      }
    }
  }

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
          Text(s.t('Choose_a_Plan'), style: theme.textTheme.titleLarge),
          const SizedBox(height: 2),
          Text(
            widget.member.fullName,
            style: theme.textTheme.bodySmall?.copyWith(
              fontSize: 12,
              color: GymPalette.textMuted,
            ),
          ),
          const SizedBox(height: GymSpacing.md),

          ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.of(context).size.height * 0.5,
            ),
            child: FutureBuilder<List<Plan>>(
              future: _future,
              builder: (context, snapshot) {
                if (snapshot.connectionState == ConnectionState.waiting) {
                  return const Padding(
                    padding: EdgeInsets.symmetric(vertical: 40),
                    child: Center(
                      child: SizedBox(
                        width: 22,
                        height: 22,
                        child: CircularProgressIndicator(strokeWidth: 2.4),
                      ),
                    ),
                  );
                }
                final plans = (snapshot.data ?? const <Plan>[])
                    .where((p) =>
                        p.restrictedTo == null ||
                        p.restrictedTo == widget.member.gender)
                    .toList();
                if (plans.isEmpty) {
                  return GymEmptyState(
                    icon: Icons.card_membership_outlined,
                    message: s.t('No_plans_for_selection'),
                  );
                }
                return ListView.separated(
                  shrinkWrap: true,
                  itemCount: plans.length,
                  separatorBuilder: (_, _) => const SizedBox(height: GymSpacing.sm),
                  itemBuilder: (context, i) {
                    final plan = plans[i];
                    return _PlanOption(
                      plan: plan,
                      selected: plan.id == _selected?.id,
                      onTap: () => setState(() => _selected = plan),
                    );
                  },
                );
              },
            ),
          ),

          if (_error != null) ...[
            const SizedBox(height: GymSpacing.sm),
            Text(
              _error!,
              style: theme.textTheme.bodySmall?.copyWith(
                color: GymPalette.danger,
              ),
            ),
          ],
          const SizedBox(height: GymSpacing.md),
          GymPillButton(
            label: s.t('Confirm_Renewal'),
            icon: _saving ? null : Icons.check,
            variant: GymButtonVariant.primary,
            expand: true,
            onPressed: (_selected == null || _saving) ? null : _confirm,
          ),
          const SizedBox(height: GymSpacing.sm),
          GymPillButton(
            label: s.t('Cancel'),
            expand: true,
            onPressed: _saving ? null : () => Navigator.of(context).pop(),
          ),
        ],
      ),
    );
  }
}

class _PlanOption extends StatelessWidget {
  const _PlanOption({
    required this.plan,
    required this.selected,
    required this.onTap,
  });

  final Plan plan;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = context.s;

    return Material(
      color: selected ? GymPalette.accentSoft : GymPalette.surfaceLow,
      borderRadius: BorderRadius.circular(GymRadii.card),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(GymRadii.card),
        child: Padding(
          padding: const EdgeInsets.all(GymSpacing.md),
          child: Row(
            children: [
              Icon(
                selected
                    ? Icons.radio_button_checked
                    : Icons.radio_button_unchecked,
                size: 20,
                color: selected ? GymPalette.accent : GymPalette.textMuted,
              ),
              const SizedBox(width: GymSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      plan.name,
                      style: theme.textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '${plan.billingLabel} · '
                      '${plan.isUnlimited ? s.t('Unlimited') : plan.visitsLabel}'
                      '${plan.cardioIncluded ? ' · ${s.t('Cardio_Included')}' : ''}',
                      style: theme.textTheme.bodySmall?.copyWith(
                        fontSize: 11,
                        color: GymPalette.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: GymSpacing.sm),
              Text(
                formatDzd(plan.priceDzd),
                style: theme.textTheme.bodyLarge?.copyWith(
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                  color: GymPalette.accent,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
