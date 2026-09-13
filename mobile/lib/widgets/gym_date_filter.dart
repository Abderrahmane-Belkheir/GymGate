import 'package:flutter/material.dart';

import '../i18n/app_strings.dart';
import '../theme/gym_colors.dart';
import '../theme/gym_tokens.dart';
import 'gym_card.dart';
import 'gym_pill_button.dart';

/// The year / month / day picker + Confirm button shared by the Attendance and
/// Payments screens — the mobile form of the desktop DAOs'
/// `find(year, month, day)` filter. The owning screen keeps the draft selection
/// and only re-queries on [onConfirm].
class GymDateFilterCard extends StatelessWidget {
  const GymDateFilterCard({
    super.key,
    required this.year,
    required this.month,
    required this.day,
    required this.dirty,
    required this.onYear,
    required this.onMonth,
    required this.onDay,
    required this.onConfirm,
  });

  final int year;
  final int month;
  final int day;

  /// Whether the draft differs from what's currently shown (enables Confirm).
  final bool dirty;
  final ValueChanged<int> onYear;
  final ValueChanged<int> onMonth;
  final ValueChanged<int> onDay;
  final VoidCallback onConfirm;

  /// Clamps [day] to the number of days in the given month/year.
  static int clampDay(int day, int year, int month) {
    final maxDay = DateTime(year, month + 1, 0).day;
    return day > maxDay ? maxDay : day;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final c = context.gymColors;
    final s = context.s;
    final thisYear = DateTime.now().year;
    final years = [for (var y = thisYear; y >= thisYear - 5; y--) y];
    final daysInMonth = DateTime(year, month + 1, 0).day;

    return GymCard(
      emphasis: GymCardEmphasis.header,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.event_outlined, size: 15, color: c.slateLabel),
              const SizedBox(width: 6),
              Text(s.t('Date'), style: theme.textTheme.labelSmall),
            ],
          ),
          const SizedBox(height: GymSpacing.sm),
          Row(
            children: [
              Expanded(
                flex: 3,
                child: _Dropdown<int>(
                  value: year,
                  items: years,
                  labelOf: (y) => '$y',
                  onChanged: onYear,
                ),
              ),
              const SizedBox(width: GymSpacing.sm),
              Expanded(
                flex: 4,
                child: _Dropdown<int>(
                  value: month,
                  items: const [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
                  labelOf: (m) => s.t('month_$m'),
                  onChanged: onMonth,
                ),
              ),
              const SizedBox(width: GymSpacing.sm),
              Expanded(
                flex: 2,
                child: _Dropdown<int>(
                  value: day,
                  items: [for (var d = 1; d <= daysInMonth; d++) d],
                  labelOf: (d) => '$d',
                  onChanged: onDay,
                ),
              ),
            ],
          ),
          const SizedBox(height: GymSpacing.md),
          GymPillButton(
            label: s.t('Confirm'),
            icon: Icons.check,
            variant: GymButtonVariant.primary,
            expand: true,
            onPressed: dirty ? onConfirm : null,
          ),
        ],
      ),
    );
  }
}

class _Dropdown<T> extends StatelessWidget {
  const _Dropdown({
    required this.value,
    required this.items,
    required this.labelOf,
    required this.onChanged,
  });

  final T value;
  final List<T> items;
  final String Function(T) labelOf;
  final ValueChanged<T> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 40,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        color: GymPalette.pageTint,
        borderRadius: BorderRadius.circular(GymRadii.input),
        border: Border.all(color: GymPalette.hairline),
      ),
      child: DropdownButton<T>(
        value: value,
        isExpanded: true,
        isDense: true,
        underline: const SizedBox.shrink(),
        borderRadius: BorderRadius.circular(GymRadii.menu),
        icon: const Icon(Icons.keyboard_arrow_down_rounded,
            size: 18, color: GymPalette.textMuted),
        style: Theme.of(context).textTheme.bodyMedium,
        items: [
          for (final item in items)
            DropdownMenuItem<T>(
              value: item,
              child: Text(labelOf(item), overflow: TextOverflow.ellipsis),
            ),
        ],
        onChanged: (v) {
          if (v != null) onChanged(v);
        },
      ),
    );
  }
}
