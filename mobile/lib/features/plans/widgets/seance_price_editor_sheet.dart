import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../data/models.dart';
import '../../../data/repository_scope.dart';
import '../../../i18n/app_strings.dart';
import '../../../theme/gym_colors.dart';
import '../../../theme/gym_tokens.dart';
import '../../../widgets/gym_pill_button.dart';

/// A small confirm card to update one séance rate's price. Resolves to `true`
/// once the write succeeds, `null` if dismissed. Gender / cardio aren't shown
/// as editable — they're fixed, just context for which rate this is.
Future<bool?> showSeancePriceEditor(
  BuildContext context, {
  required Seance seance,
}) {
  return showModalBottomSheet<bool>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) => _SeancePriceEditorSheet(seance: seance),
  );
}

class _SeancePriceEditorSheet extends StatefulWidget {
  const _SeancePriceEditorSheet({required this.seance});

  final Seance seance;

  @override
  State<_SeancePriceEditorSheet> createState() =>
      _SeancePriceEditorSheetState();
}

class _SeancePriceEditorSheetState extends State<_SeancePriceEditorSheet> {
  late final TextEditingController _price =
      TextEditingController(text: '${widget.seance.priceDzd}');
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _price.dispose();
    super.dispose();
  }

  int? get _priceValue {
    final v = int.tryParse(_price.text.trim());
    return (v != null && v >= 0) ? v : null;
  }

  bool get _canSubmit => !_saving && _priceValue != null;

  Future<void> _submit() async {
    if (!_canSubmit) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await RepositoryScope.of(context).seance.updatePrice(
            id: widget.seance.id,
            priceDzd: _priceValue!,
          );
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = '${context.s.t('Could_not_update_the_price')}: $e';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = context.s;
    final seance = widget.seance;
    final label = '${s.t(seance.restrictedTo == Gender.female ? 'Female' : 'Male')}'
        ' · ${s.t(seance.cardio ? 'With_cardio' : 'Standard')}';

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
          Text(s.t('Update_Price'), style: theme.textTheme.titleLarge),
          const SizedBox(height: 2),
          Text(
            label,
            style: theme.textTheme.bodySmall?.copyWith(
              fontSize: 12,
              color: GymPalette.textMuted,
            ),
          ),
          const SizedBox(height: GymSpacing.lg),
          Text(
            s.t('Price_(DZD)'),
            style: theme.textTheme.labelMedium?.copyWith(
              color: GymPalette.textSlate,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          TextField(
            controller: _price,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            autofocus: true,
            onChanged: (_) => setState(() {}),
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
            label: s.t('Save_Changes'),
            icon: _saving ? null : Icons.check,
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
    );
  }
}
