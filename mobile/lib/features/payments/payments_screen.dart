import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/formatting.dart';
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
import 'widgets/payment_list_row.dart';

enum _PaymentsView { list, statistics }

/// Payments — the desktop `Paiements` screen adapted to mobile. A `List` /
/// `Statistics` switch: List is the day's payments (header revenue + count, a
/// year / month / day filter, the framed list); Statistics is the trend bar
/// chart (daily / monthly / all-time revenue), one network round trip per view.
class PaymentsScreen extends StatefulWidget {
  const PaymentsScreen({super.key});

  @override
  State<PaymentsScreen> createState() => _PaymentsScreenState();
}

class _PaymentsScreenState extends State<PaymentsScreen>
    with RealtimeReload<PaymentsScreen> {
  late int _year;
  late int _month;
  late int _day;

  late int _appliedYear;
  late int _appliedMonth;
  late int _appliedDay;

  _PaymentsView _view = _PaymentsView.list;
  int _statsTick = 0;
  int? _periodRevenue;

  Future<_PaymentsData>? _future;

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
    // Leaving the screen while the chart was up: hand portrait back.
    SystemChrome.setPreferredOrientations(
        const [DeviceOrientation.portraitUp]);
    super.dispose();
  }

  void _setView(_PaymentsView v) {
    if (v == _view) return;
    setState(() => _view = v);
    // The bar chart needs the width — force landscape while it's shown.
    SystemChrome.setPreferredOrientations(
      v == _PaymentsView.statistics
          ? const [
              DeviceOrientation.landscapeLeft,
              DeviceOrientation.landscapeRight,
            ]
          : const [DeviceOrientation.portraitUp],
    );
  }

  @override
  List<String> get realtimeTables => const ['payments'];

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

  Future<_PaymentsData> _query() async {
    final repo = RepositoryScope.of(context).payments;
    final results = await Future.wait<Object?>([
      repo.findPayments(
          year: _appliedYear, month: _appliedMonth, day: _appliedDay),
      repo.sumAmount(
          year: _appliedYear, month: _appliedMonth, day: _appliedDay),
    ]);
    return _PaymentsData(
      payments: results[0] as List<Payment>,
      totalDzd: results[1] as int,
    );
  }

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

  String _revenueTitle(TrendMode mode) {
    final s = context.s;
    return switch (mode) {
      TrendMode.daily => s.t('daily_revenue'),
      TrendMode.monthly => s.t('monthly_revenue'),
      TrendMode.allTime => s.t('yearly_revenue'),
    };
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<_PaymentsData>(
      future: _future,
      builder: (context, snapshot) {
        final s = context.s;
        final theme = Theme.of(context);
        final data = snapshot.data;
        final loading = snapshot.connectionState == ConnectionState.waiting &&
            !snapshot.hasData;

        final isStats = _view == _PaymentsView.statistics;
        final headerAmount =
            isStats ? (_periodRevenue ?? 0) : (data?.totalDzd ?? 0);

        return GymScaffold(
          onRefresh: _refresh,
          children: [
            GymHeaderCard(
              title: s.t('Payments'),
              subtitle: s.t('View_payment_history'),
              trailing: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    formatDzd(headerAmount),
                    style: theme.textTheme.titleMedium,
                  ),
                  const SizedBox(height: 3),
                  isStats
                      ? Text(
                          s.t('Revenue'),
                          style: theme.textTheme.bodySmall
                              ?.copyWith(fontSize: 11),
                        )
                      : GymCountBadge(
                          '${data?.payments.length ?? 0} ${s.t('Payments')}'),
                ],
              ),
            ),
            _ViewToggleCard(view: _view, onChanged: _setView),
            if (isStats)
              GymTrendStatistics(
                key: const ValueKey('payments-stats'),
                refreshTick: _statsTick,
                unit: 'DZD',
                emptyText: s.t('no_revenue_data'),
                titleOf: _revenueTitle,
                onPeriodTotal: (t) => setState(() => _periodRevenue = t),
                fetch: (mode, year, month) => RepositoryScope.of(context)
                    .payments
                    .revenueTrend(mode: mode, year: year, month: month),
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
              _PaymentsList(
                payments: data?.payments ?? const <Payment>[],
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

  final _PaymentsView view;
  final ValueChanged<_PaymentsView> onChanged;

  @override
  Widget build(BuildContext context) {
    final s = context.s;
    return GymCard(
      emphasis: GymCardEmphasis.header,
      padding: const EdgeInsets.symmetric(
          horizontal: GymSpacing.md, vertical: GymSpacing.md),
      child: GymSegmentedToggle<_PaymentsView>(
        value: view,
        onChanged: onChanged,
        expanded: true,
        segments: [
          GymSegment(
            value: _PaymentsView.list,
            label: s.t('List'),
            style: GymSegmentStyle.solidBlue,
          ),
          GymSegment(
            value: _PaymentsView.statistics,
            label: s.t('Statistics'),
            style: GymSegmentStyle.solidBlue,
          ),
        ],
      ),
    );
  }
}

class _PaymentsData {
  const _PaymentsData({required this.payments, required this.totalDzd});

  final List<Payment> payments;
  final int totalDzd;
}

class _PaymentsList extends StatelessWidget {
  const _PaymentsList({
    required this.payments,
    required this.loading,
    required this.hasError,
  });

  final List<Payment> payments;
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
    } else if (payments.isEmpty) {
      body = SizedBox(
        height: bodyMinHeight,
        child: Center(
          child: GymEmptyState(
            icon: Icons.receipt_long_outlined,
            message: s.t('No_payments_for_this_date.'),
          ),
        ),
      );
    } else {
      body = ConstrainedBox(
        constraints: const BoxConstraints(minHeight: bodyMinHeight),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = 0; i < payments.length; i++) ...[
              PaymentListRow(payment: payments[i]),
              if (i != payments.length - 1) const Divider(height: 1),
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
                  Text(s.t('Amount'), style: theme.textTheme.labelSmall),
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
