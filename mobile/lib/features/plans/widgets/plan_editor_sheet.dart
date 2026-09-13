import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../data/models.dart';
import '../../../data/repository_scope.dart';
import '../../../i18n/app_strings.dart';
import '../../../theme/gym_colors.dart';
import '../../../theme/gym_tokens.dart';
import '../../../widgets/gym_pill_button.dart';
import '../../../widgets/gym_segmented_toggle.dart';

/// Opens the plan form. Pass [plan] to edit an existing one (fields pre-filled,
/// gender not shown — it isn't editable — and `synced` is flipped to 0 on save);
/// omit it to create a new plan. Resolves to `true` once the row is written
/// (the caller refreshes the list), `null` if dismissed.
Future<bool?> showPlanEditor(BuildContext context, {Plan? plan}) {
  return showModalBottomSheet<bool>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) => _PlanEditorSheet(plan: plan),
  );
}

class _PlanEditorSheet extends StatefulWidget {
  const _PlanEditorSheet({this.plan});

  final Plan? plan;

  @override
  State<_PlanEditorSheet> createState() => _PlanEditorSheetState();
}

class _PlanEditorSheetState extends State<_PlanEditorSheet> {
  late final TextEditingController _name;
  late final TextEditingController _price;
  late final TextEditingController _duration;
  late final TextEditingController _days;

  late Gender _gender;
  late bool _cardio;
  bool _saving = false;
  String? _error;

  bool get _editing => widget.plan != null;

  @override
  void initState() {
    super.initState();
    final p = widget.plan;
    _name = TextEditingController(text: p?.name ?? '');
    _price = TextEditingController(text: p == null ? '' : '${p.priceDzd}');
    _duration = TextEditingController(
      text: p == null ? '1' : '${(p.billingDays / 30).round().clamp(1, 999)}',
    );
    _days = TextEditingController(
      text: (p == null || p.isUnlimited) ? '' : '${p.visitsPerMonth}',
    );
    _gender = p?.restrictedTo ?? Gender.male;
    _cardio = p?.cardioIncluded ?? false;
  }

  @override
  void dispose() {
    _name.dispose();
    _price.dispose();
    _duration.dispose();
    _days.dispose();
    super.dispose();
  }

  int? get _priceValue {
    final v = int.tryParse(_price.text.trim());
    return (v != null && v >= 0) ? v : null;
  }

  int? get _durationValue {
    final v = int.tryParse(_duration.text.trim());
    return (v != null && v >= 1) ? v : null;
  }

  /// `null` text → unlimited (valid); any text must be a positive int.
  ({bool valid, int? value}) get _daysValue {
    final text = _days.text.trim();
    if (text.isEmpty) return (valid: true, value: null);
    final v = int.tryParse(text);
    return (v != null && v >= 1)
        ? (valid: true, value: v)
        : (valid: false, value: null);
  }

  bool get _canSubmit =>
      !_saving &&
      _name.text.trim().isNotEmpty &&
      _priceValue != null &&
      _durationValue != null &&
      _daysValue.valid;

  Future<void> _submit() async {
    if (!_canSubmit) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    final plans = RepositoryScope.of(context).plans;
    try {
      if (_editing) {
        await plans.updatePlan(
          id: widget.plan!.id,
          name: _name.text.trim(),
          priceDzd: _priceValue!,
          durationMonths: _durationValue!,
          daysPerMonth: _daysValue.value,
          cardioIncluded: _cardio,
        );
      } else {
        await plans.createPlan(
          name: _name.text.trim(),
          priceDzd: _priceValue!,
          durationMonths: _durationValue!,
          daysPerMonth: _daysValue.value,
          cardioIncluded: _cardio,
          gender: _gender,
        );
      }
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = '${context.s.t(_editing ? 'Could_not_update_the_plan' : 'Could_not_create_the_plan')}: $e';
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
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              s.t(_editing ? 'Edit_Plan' : 'New_Plan'),
              style: theme.textTheme.titleLarge,
            ),
            const SizedBox(height: 2),
            Text(
              s.t(_editing
                  ? "Update_this_membership_plan's_details."
                  : 'Create_a_new_membership_plan'),
              style: theme.textTheme.bodySmall?.copyWith(
                fontSize: 12,
                color: GymPalette.textMuted,
              ),
            ),
            const SizedBox(height: GymSpacing.lg),

            _Field(
              label: s.t('Plan_Name'),
              child: TextField(
                controller: _name,
                textInputAction: TextInputAction.next,
                textCapitalization: TextCapitalization.words,
                onChanged: (_) => setState(() {}),
              ),
            ),
            _Field(
              label: s.t('Price_(DZD)'),
              child: TextField(
                controller: _price,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                textInputAction: TextInputAction.next,
                onChanged: (_) => setState(() {}),
              ),
            ),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: _Field(
                    label: s.t('Duration_(months)'),
                    child: TextField(
                      controller: _duration,
                      keyboardType: TextInputType.number,
                      inputFormatters: [
                        FilteringTextInputFormatter.digitsOnly,
                      ],
                      onChanged: (_) => setState(() {}),
                    ),
                  ),
                ),
                const SizedBox(width: GymSpacing.md),
                Expanded(
                  child: _Field(
                    label: s.t('Days_per_Month'),
                    child: TextField(
                      controller: _days,
                      keyboardType: TextInputType.number,
                      inputFormatters: [
                        FilteringTextInputFormatter.digitsOnly,
                      ],
                      decoration: InputDecoration(
                        hintText: s.t('Leave_blank_for_Unlimited'),
                      ),
                      onChanged: (_) => setState(() {}),
                    ),
                  ),
                ),
              ],
            ),
            // Gender is fixed for an existing plan (matches the desktop editor).
            if (!_editing)
              _Field(
                label: '${s.t('Male')} / ${s.t('Female')}',
                child: GymSegmentedToggle<Gender>(
                  value: _gender,
                  onChanged: (g) => setState(() => _gender = g),
                  segments: [
                    GymSegment(
                      value: Gender.male,
                      label: s.t('Male'),
                      style: GymSegmentStyle.solidBlue,
                    ),
                    GymSegment(
                      value: Gender.female,
                      label: s.t('Female'),
                      style: GymSegmentStyle.solidPink,
                    ),
                  ],
                ),
              ),
            _Field(
              label: s.t('Cardio_Included'),
              child: GymSegmentedToggle<bool>(
                value: _cardio,
                onChanged: (v) => setState(() => _cardio = v),
                segments: [
                  GymSegment(
                    value: true,
                    label: s.t('Yes'),
                    style: GymSegmentStyle.softGreen,
                  ),
                  GymSegment(
                    value: false,
                    label: s.t('No'),
                    style: GymSegmentStyle.softRed,
                  ),
                ],
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
              label: s.t(_editing ? 'Save_Changes' : 'Create_Plan'),
              icon: _saving ? null : (_editing ? Icons.save_outlined : Icons.check),
              variant: GymButtonVariant.primary,
              expand: true,
              onPressed: _canSubmit ? _submit : null,
            ),
            const SizedBox(height: GymSpacing.sm),
            GymPillButton(
              label: s.t('Cancel'),
              expand: true,
              onPressed: _saving ? null : () => Navigator.of(context).pop(),
            ),
          ],
        ),
      ),
    );
  }
}

class _Field extends StatelessWidget {
  const _Field({required this.label, required this.child});

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: GymSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: theme.textTheme.labelMedium?.copyWith(
              color: GymPalette.textSlate,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          child,
        ],
      ),
    );
  }
}
