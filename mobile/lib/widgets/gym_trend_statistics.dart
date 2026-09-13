import 'package:flutter/material.dart';

import '../core/formatting.dart';
import '../data/trend.dart';
import '../i18n/app_strings.dart';
import '../theme/gym_colors.dart';
import '../theme/gym_tokens.dart';
import 'gym_card.dart';
import 'gym_pill_button.dart';
import 'gym_segmented_toggle.dart';
import 'gym_trend_chart.dart';

/// The "Statistics" view shared by the Attendance and Payments screens: a
/// granularity + period control card, then a framed chart card with the
/// `Average · Peak` hint (and, for attendance daily, a busiest day / hour line).
///
/// [fetch] does exactly one network round trip per applied selection; the result
/// is held until Confirm, [refreshTick] changes, or the mode/period changes.
class GymTrendStatistics extends StatefulWidget {
  const GymTrendStatistics({
    super.key,
    required this.fetch,
    required this.titleOf,
    required this.unit,
    required this.emptyText,
    required this.refreshTick,
    this.showBusiestLine = false,
    this.onPeriodTotal,
  });

  final Future<TrendSeries> Function(TrendMode mode, int year, int month) fetch;

  /// The chart header title for a mode, e.g. `Daily revenue` / `Monthly check-ins`.
  final String Function(TrendMode mode) titleOf;

  /// Tooltip unit — `DZD` for payments, empty for attendance.
  final String unit;
  final String emptyText;

  /// Bump from the parent (pull-to-refresh, realtime insert) to force a refetch.
  final int refreshTick;

  final bool showBusiestLine;

  /// Reports `total` of the current period so the parent can show it in the
  /// header KPI slot.
  final ValueChanged<int>? onPeriodTotal;

  @override
  State<GymTrendStatistics> createState() => _GymTrendStatisticsState();
}

class _GymTrendStatisticsState extends State<GymTrendStatistics> {
  late TrendMode _mode;
  late int _year;
  late int _month;

  late TrendMode _draftMode;
  late int _draftYear;
  late int _draftMonth;

  Future<TrendSeries>? _future;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _mode = _draftMode = TrendMode.daily;
    _year = _draftYear = now.year;
    _month = _draftMonth = now.month;
    _future = _run();
  }

  @override
  void didUpdateWidget(GymTrendStatistics oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.refreshTick != widget.refreshTick) {
      final future = _run();
      future.whenComplete(() {
        if (mounted) setState(() => _future = future);
      });
    }
  }

  Future<TrendSeries> _run() {
    final future = widget.fetch(_mode, _year, _month);
    future.then((series) {
      if (!mounted || widget.onPeriodTotal == null) return;
      final total = summarize(series.values).total;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) widget.onPeriodTotal!(total);
      });
    });
    return future;
  }

  bool get _dirty =>
      _draftMode != _mode || _draftYear != _year || _draftMonth != _month;

  void _confirm() {
    if (!_dirty) return;
    setState(() {
      _mode = _draftMode;
      _year = _draftYear;
      _month = _draftMonth;
      _future = _run();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        _ControlsCard(
          mode: _draftMode,
          year: _draftYear,
          month: _draftMonth,
          dirty: _dirty,
          onMode: (m) => setState(() => _draftMode = m),
          onYear: (y) => setState(() => _draftYear = y),
          onMonth: (m) => setState(() => _draftMonth = m),
          onConfirm: _confirm,
        ),
        const SizedBox(height: GymSpacing.sectionGap),
        FutureBuilder<TrendSeries>(
          future: _future,
          builder: (context, snapshot) {
            return _ChartCard(
              mode: _mode,
              year: _year,
              month: _month,
              series: snapshot.data,
              loading: snapshot.connectionState == ConnectionState.waiting &&
                  !snapshot.hasData,
              hasError: snapshot.hasError,
              title: widget.titleOf(_mode),
              unit: widget.unit,
              emptyText: widget.emptyText,
              showBusiestLine: widget.showBusiestLine,
            );
          },
        ),
      ],
    );
  }
}

class _ControlsCard extends StatelessWidget {
  const _ControlsCard({
    required this.mode,
    required this.year,
    required this.month,
    required this.dirty,
    required this.onMode,
    required this.onYear,
    required this.onMonth,
    required this.onConfirm,
  });

  final TrendMode mode;
  final int year;
  final int month;
  final bool dirty;
  final ValueChanged<TrendMode> onMode;
  final ValueChanged<int> onYear;
  final ValueChanged<int> onMonth;
  final VoidCallback onConfirm;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final c = context.gymColors;
    final s = context.s;
    final thisYear = DateTime.now().year;
    final years = [for (var y = thisYear; y >= thisYear - 5; y--) y];

    return GymCard(
      emphasis: GymCardEmphasis.header,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.insights_outlined, size: 15, color: c.slateLabel),
              const SizedBox(width: 6),
              Text(s.t('Statistics'), style: theme.textTheme.labelSmall),
            ],
          ),
          const SizedBox(height: GymSpacing.sm),
          GymSegmentedToggle<TrendMode>(
            value: mode,
            onChanged: onMode,
            expanded: true,
            segments: [
              GymSegment(
                value: TrendMode.daily,
                label: s.t('stat_mode_daily'),
                style: GymSegmentStyle.solidBlue,
              ),
              GymSegment(
                value: TrendMode.monthly,
                label: s.t('stat_mode_monthly'),
                style: GymSegmentStyle.solidBlue,
              ),
              GymSegment(
                value: TrendMode.allTime,
                label: s.t('stat_mode_all_time'),
                style: GymSegmentStyle.solidBlue,
              ),
            ],
          ),
          if (mode != TrendMode.allTime) ...[
            const SizedBox(height: GymSpacing.md),
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
                if (mode == TrendMode.daily) ...[
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
                ],
              ],
            ),
          ],
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

class _ChartCard extends StatelessWidget {
  const _ChartCard({
    required this.mode,
    required this.year,
    required this.month,
    required this.series,
    required this.loading,
    required this.hasError,
    required this.title,
    required this.unit,
    required this.emptyText,
    required this.showBusiestLine,
  });

  final TrendMode mode;
  final int year;
  final int month;
  final TrendSeries? series;
  final bool loading;
  final bool hasError;
  final String title;
  final String unit;
  final String emptyText;
  final bool showBusiestLine;

  String _barLabel(BuildContext context, TrendSeries data, int i) {
    switch (mode) {
      case TrendMode.daily:
        return '${i + 1}';
      case TrendMode.monthly:
        return context.s.t('mon_${i + 1}');
      case TrendMode.allTime:
        return '${data.years[i]}';
    }
  }

  String _period(BuildContext context) {
    final s = context.s;
    switch (mode) {
      case TrendMode.daily:
        return '${s.t('month_$month')} $year';
      case TrendMode.monthly:
        return '$year';
      case TrendMode.allTime:
        return s.t('stat_mode_all_time');
    }
  }

  /// The chart fills more of the screen in landscape (where the Statistics view
  /// forces the phone) so a full month of bars is comfortably tappable.
  double _chartHeight(BuildContext context) {
    final media = MediaQuery.of(context);
    if (media.orientation == Orientation.landscape) {
      return (media.size.height * 0.72).clamp(220.0, 420.0);
    }
    return 236;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = context.s;
    final data = series;
    final chartHeight = _chartHeight(context);

    Widget body;
    if (loading) {
      body = SizedBox(
        height: chartHeight,
        child: const Center(
          child: SizedBox(
            width: 22,
            height: 22,
            child: CircularProgressIndicator(strokeWidth: 2.4),
          ),
        ),
      );
    } else if (hasError || data == null) {
      body = SizedBox(
        height: chartHeight,
        child: Center(
          child: Text(
            s.t('Could_not_load'),
            style: theme.textTheme.bodyMedium
                ?.copyWith(color: GymPalette.textMuted),
          ),
        ),
      );
    } else {
      final stats = summarize(data.values);
      body = Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _Header(
            title: '$title  ·  ${_period(context)}',
            hint: '${s.t('stat_average')} '
                '${formatThousands(stats.average.round(), separator: ',')}'
                '     ·     '
                '${s.t('stat_peak')} '
                '${formatThousands(stats.peak, separator: ',')}',
            pattern: _patternLine(context, data),
          ),
          const SizedBox(height: GymSpacing.sm),
          GymTrendChart(
            height: chartHeight,
            input: TrendChartInput(
              bars: [
                for (var i = 0; i < data.values.length; i++)
                  TrendBar(_barLabel(context, data, i), data.values[i]),
              ],
              currentIndex:
                  currentTrendIndex(mode, year: year, month: month),
              unit: unit,
              emptyText: emptyText,
            ),
          ),
        ],
      );
    }

    return GymCard(child: body);
  }

  String? _patternLine(BuildContext context, TrendSeries data) {
    if (!showBusiestLine ||
        mode != TrendMode.daily ||
        data.busiestWeekday == null ||
        data.busiestHour == null) {
      return null;
    }
    final s = context.s;
    final hour = data.busiestHour!.toString().padLeft(2, '0');
    return '${s.t('busiest_day')} : ${s.t('wd_${data.busiestWeekday}')}'
        '   |   ${s.t('busiest_hour')} : $hour:00';
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.title, required this.hint, this.pattern});

  final String title;
  final String hint;
  final String? pattern;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Expanded(
              child: Text(
                title,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                  color: Color(0xFF1B2437),
                ),
              ),
            ),
            const SizedBox(width: GymSpacing.sm),
            Text(
              hint,
              style: const TextStyle(
                fontSize: 11.5,
                fontWeight: FontWeight.w700,
                color: Color(0xFF8993A6),
              ),
            ),
          ],
        ),
        if (pattern != null) ...[
          const SizedBox(height: 3),
          Text(
            pattern!,
            style: theme.textTheme.bodySmall?.copyWith(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: const Color(0xFF5B6472),
            ),
          ),
        ],
      ],
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
