import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../data/models.dart';
import '../../data/repository_scope.dart';
import '../../data/trend.dart';
import '../../i18n/app_strings.dart';
import '../../realtime/realtime_bus.dart';
import '../../theme/gym_colors.dart';
import '../../theme/gym_tokens.dart';
import '../../widgets/gym_badges.dart';
import '../../widgets/gym_card.dart';
import '../../widgets/gym_date_filter.dart';
import '../../widgets/gym_empty_state.dart';
import '../../widgets/gym_header_card.dart';
import '../../widgets/gym_scaffold.dart';
import '../../widgets/gym_segmented_toggle.dart';
import '../../widgets/gym_trend_chart.dart';
import '../../widgets/gym_trend_statistics.dart';
import 'widgets/attendance_list_row.dart';

enum _AttendanceView { list, statistics }

/// Attendance — the desktop `Présence` screen adapted to mobile. A `List` /
/// `Statistics` switch: List is a day's check-ins (year / month / day filter,
/// the framed list); Statistics is the trend bar chart (daily / monthly /
/// all-time check-ins), one network round trip per view.
class AttendanceScreen extends StatefulWidget {
  const AttendanceScreen({super.key});

  @override
  State<AttendanceScreen> createState() => _AttendanceScreenState();
}

class _AttendanceScreenState extends State<AttendanceScreen>
    with RealtimeReload<AttendanceScreen> {
  late int _year;
  late int _month;
  late int _day;

  late int _appliedYear;
  late int _appliedMonth;
  late int _appliedDay;

  _AttendanceView _view = _AttendanceView.list;
  int _statsTick = 0;
  int? _periodCount;

  Future<List<AttendanceEntry>>? _future;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _year = _appliedYear = now.year;
    _month = _appliedMonth = now.month;
    _day = _appliedDay = now.day;
  }

  @override
  void dispose() {
    SystemChrome.setPreferredOrientations(
        const [DeviceOrientation.portraitUp]);
    super.dispose();
  }

  void _setView(_AttendanceView v) {
    if (v == _view) return;
    setState(() => _view = v);
    SystemChrome.setPreferredOrientations(
      v == _AttendanceView.statistics
          ? const [
              DeviceOrientation.landscapeLeft,
              DeviceOrientation.landscapeRight,
            ]
          : const [DeviceOrientation.portraitUp],
    );
  }

  @override
  List<String> get realtimeTables => const ['attendance'];

  @override
  void onRealtimeChange() {
    final future = _query();
    future.whenComplete(() {
      if (mounted) {
        setState(() {
          _future = future;
          _statsTick++;
        });
      }
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _future ??= _query();
    watchRealtime(context);
  }

  Future<List<AttendanceEntry>> _query() =>
      RepositoryScope.of(context).attendance.findAttendance(
            year: _appliedYear,
            month: _appliedMonth,
            day: _appliedDay,
          );

  bool get _dirty =>
      _year != _appliedYear ||
      _month != _appliedMonth ||
      _day != _appliedDay;

  void _confirm() {
    if (!_dirty) return;
    setState(() {
      _appliedYear = _year;
      _appliedMonth = _month;
      _appliedDay = _day;
      _future = _query();
    });
  }

  Future<void> _refresh() async {
    final future = _query();
    setState(() {
      _future = future;
      _statsTick++;
    });
    await future;
  }

  void _setYear(int y) => setState(() {
        _year = y;
        _day = GymDateFilterCard.clampDay(_day, _year, _month);
      });

  void _setMonth(int m) => setState(() {
        _month = m;
        _day = GymDateFilterCard.clampDay(_day, _year, _month);
      });

  void _setDay(int d) => setState(() => _day = d);

  String _checkInTitle(TrendMode mode) {
    final s = context.s;
    return switch (mode) {
      TrendMode.daily => s.t('daily_check_ins'),
      TrendMode.monthly => s.t('monthly_check_ins'),
      TrendMode.allTime => s.t('yearly_check_ins'),
    };
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<AttendanceEntry>>(
      future: _future,
      builder: (context, snapshot) {
        final s = context.s;
        final theme = Theme.of(context);
        final entries = snapshot.data ?? const <AttendanceEntry>[];
        final loading = snapshot.connectionState == ConnectionState.waiting &&
            !snapshot.hasData;

        final isStats = _view == _AttendanceView.statistics;

        return GymScaffold(
          onRefresh: _refresh,
          children: [
            GymHeaderCard(
              title: s.t('Attendance'),
              subtitle: s.t('Member_check_in_history'),
              trailing: isStats
                  ? Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text('${_periodCount ?? 0}',
                            style: theme.textTheme.titleMedium),
                        const SizedBox(height: 3),
                        Text(
                          s.t('Check-ins'),
                          style: theme.textTheme.bodySmall
                              ?.copyWith(fontSize: 11),
                        ),
                      ],
                    )
                  : GymCountBadge(
                      '${entries.length} ${s.t('present_count')}'),
            ),
            _ViewToggleCard(view: _view, onChanged: _setView),
            if (isStats)
              GymTrendStatistics(
                key: const ValueKey('attendance-stats'),
                refreshTick: _statsTick,
                unit: '',
                emptyText: s.t('no_check_in_data'),
                titleOf: _checkInTitle,
                showBusiestLine: true,
                onPeriodTotal: (t) => setState(() => _periodCount = t),
                fetch: (mode, year, month) => RepositoryScope.of(context)
                    .attendance
                    .checkInTrend(mode: mode, year: year, month: month),
              )
            else ...[
              GymDateFilterCard(
                year: _year,
                month: _month,
                day: _day,
                dirty: _dirty,
                onYear: _setYear,
                onMonth: _setMonth,
                onDay: _setDay,
                onConfirm: _confirm,
              ),
              _AttendanceList(
                entries: entries,
                loading: loading,
                hasError: snapshot.hasError,
              ),
            ],
          ],
        );
      },
    );
  }
}

class _ViewToggleCard extends StatelessWidget {
  const _ViewToggleCard({required this.view, required this.onChanged});

  final _AttendanceView view;
  final ValueChanged<_AttendanceView> onChanged;

  @override
  Widget build(BuildContext context) {
    final s = context.s;
    return GymCard(
      emphasis: GymCardEmphasis.header,
      padding: const EdgeInsets.symmetric(
          horizontal: GymSpacing.md, vertical: GymSpacing.md),
      child: GymSegmentedToggle<_AttendanceView>(
        value: view,
        onChanged: onChanged,
        expanded: true,
        segments: [
          GymSegment(
            value: _AttendanceView.list,
            label: s.t('List'),
            style: GymSegmentStyle.solidBlue,
          ),
          GymSegment(
            value: _AttendanceView.statistics,
            label: s.t('Statistics'),
            style: GymSegmentStyle.solidBlue,
          ),
        ],
      ),
    );
  }
}

class _AttendanceList extends StatelessWidget {
  const _AttendanceList({
    required this.entries,
    required this.loading,
    required this.hasError,
  });

  final List<AttendanceEntry> entries;
  final bool loading;
  final bool hasError;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = context.s;

    const bodyMinHeight = kTrendPanelMinHeight - 31; // header band = 30 + 1px

    Widget body;
    if (loading) {
      body = const SizedBox(
        height: bodyMinHeight,
        child: Center(
          child: SizedBox(
            width: 22,
            height: 22,
            child: CircularProgressIndicator(strokeWidth: 2.4),
          ),
        ),
      );
    } else if (hasError) {
      body = SizedBox(
        height: bodyMinHeight,
        child: Center(
          child: GymEmptyState(
            icon: Icons.cloud_off_outlined,
            title: s.t('Could_not_load'),
            message: s.t('Pull_to_retry'),
          ),
        ),
      );
    } else if (entries.isEmpty) {
      body = SizedBox(
        height: bodyMinHeight,
        child: Center(
          child: GymEmptyState(
            icon: Icons.event_available_outlined,
            message: s.t('No_check-ins_for_this_date.'),
          ),
        ),
      );
    } else {
      body = ConstrainedBox(
        constraints: const BoxConstraints(minHeight: bodyMinHeight),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = 0; i < entries.length; i++) ...[
              AttendanceListRow(entry: entries[i]),
              if (i != entries.length - 1) const Divider(height: 1),
            ],
          ],
        ),
      );
    }

    return GymCard(
      padding: EdgeInsets.zero,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(GymRadii.card),
        child: Column(
          children: [
            Container(
              height: 30,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              decoration: const BoxDecoration(
                color: GymPalette.tableHeaderBg,
                border: Border(
                  bottom: BorderSide(color: GymPalette.hairlineStrong),
                ),
              ),
              child: Row(
                children: [
                  Text(s.t('Member'), style: theme.textTheme.labelSmall),
                  const Spacer(),
                  Text(s.t('Check-in_Time'), style: theme.textTheme.labelSmall),
                ],
              ),
            ),
            body,
          ],
        ),
      ),
    );
  }
}
