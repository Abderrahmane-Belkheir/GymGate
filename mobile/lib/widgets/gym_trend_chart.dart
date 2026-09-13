import 'package:flutter/material.dart';

import '../core/formatting.dart';
import '../data/trend.dart';

/// Min height for the framed panel on both stat screens — the List frame is
/// pinned to this so it reads the same size as the Statistics chart card and
/// the screen doesn't jump when you switch tabs.
const double kTrendPanelMinHeight = 300;

/// One x-axis category: a pre-localized label and its `>= 0` value.
class TrendBar {
  const TrendBar(this.label, this.value);

  final String label;
  final int value;
}

/// The fully-resolved view model a [GymTrendChart] renders — no fetching or
/// business logic happens inside the widget (see `flutter-trend-barchart-spec`).
class TrendChartInput {
  const TrendChartInput({
    required this.bars,
    this.currentIndex = -1,
    this.unit = '',
    this.emptyText = '',
  });

  final List<TrendBar> bars;

  /// Index of a still-open period (drawn lighter); `-1` = none.
  final int currentIndex;

  /// Tooltip suffix — `DZD` for payments, empty for attendance.
  final String unit;

  final String emptyText;
}

/// GymGate trend bar chart — the mobile port of the desktop Payments /
/// Attendance "Statistics" chart. Flat blue bars, hairline grid, a nice-scale
/// value axis, always-on labels on the peak and current bars, and a dark pill
/// tooltip on tap. One widget, used by both stat screens.
class GymTrendChart extends StatefulWidget {
  const GymTrendChart({super.key, required this.input, this.height = 236});

  final TrendChartInput input;
  final double height;

  @override
  State<GymTrendChart> createState() => _GymTrendChartState();
}

class _GymTrendChartState extends State<GymTrendChart>
    with SingleTickerProviderStateMixin {
  late final AnimationController _entry = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 260),
  )..forward();

  int? _selected;

  @override
  void didUpdateWidget(GymTrendChart oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_sameBars(oldWidget.input.bars, widget.input.bars)) {
      _selected = null;
      _entry
        ..reset()
        ..forward();
    }
  }

  bool _sameBars(List<TrendBar> a, List<TrendBar> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i].value != b[i].value || a[i].label != b[i].label) return false;
    }
    return true;
  }

  @override
  void dispose() {
    _entry.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final input = widget.input;
    final values = [for (final b in input.bars) b.value];
    final stats = summarize(values);

    Widget content;
    if (input.bars.isEmpty || stats.total == 0) {
      content = _EmptyPlot(text: input.emptyText);
    } else {
      content = LayoutBuilder(
        builder: (context, constraints) {
          final geometry = _ChartGeometry(
            size: Size(constraints.maxWidth, widget.height),
            values: values,
            peak: stats.peak,
          );
          return Stack(
            children: [
              Positioned.fill(
                child: CustomPaint(
                  painter: _TrendPainter(
                    geometry: geometry,
                    input: input,
                    peakIndex: stats.peakIndex,
                    selectedIndex: _selected,
                  ),
                ),
              ),
              Positioned.fill(
                child: GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTapDown: (details) {
                    final hit = geometry.slotAt(details.localPosition.dx);
                    setState(() => _selected = (hit == _selected) ? null : hit);
                  },
                ),
              ),
              if (_selected case final index?)
                _Tooltip(
                  geometry: geometry,
                  index: index,
                  bar: input.bars[index],
                  unit: input.unit,
                ),
            ],
          );
        },
      );
    }

    return SizedBox(
      height: widget.height,
      child: AnimatedBuilder(
        animation: _entry,
        builder: (context, child) {
          final t = Curves.easeOut.transform(_entry.value);
          return Opacity(
            opacity: t,
            child: Transform.translate(offset: Offset(0, 8 * (1 - t)), child: child),
          );
        },
        child: content,
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Geometry
// ---------------------------------------------------------------------------

class _ChartGeometry {
  _ChartGeometry({
    required this.size,
    required this.values,
    required int peak,
  }) {
    final scale = niceScale(peak);
    upperBound = scale[0].toDouble();
    tickUnit = scale[1].toDouble();

    plot = Rect.fromLTRB(
      _yAxisWidth,
      _plotPadTop,
      size.width - _plotPadRight,
      size.height - _xAxisHeight,
    );

    final n = values.length;
    final gap = n >= 28
        ? 10.0
        : n >= 12
            ? 18.0
            : n >= 6
                ? 30.0
                : 46.0;
    slotWidth = plot.width / n;
    barWidth = (slotWidth - gap).clamp(3.0, 64.0);
  }

  static const double _yAxisWidth = 34;
  static const double _plotPadTop = 16;
  static const double _plotPadRight = 10;
  static const double _xAxisHeight = 22;

  final Size size;
  final List<int> values;

  late final double upperBound;
  late final double tickUnit;
  late final Rect plot;
  late final double slotWidth;
  late final double barWidth;

  double yOf(num value) =>
      plot.bottom - (value / upperBound) * plot.height;

  double slotCenter(int index) => plot.left + slotWidth * (index + 0.5);

  Rect barRect(int index) {
    final center = slotCenter(index);
    final top = yOf(values[index]);
    return Rect.fromLTRB(
      center - barWidth / 2,
      top.clamp(plot.top, plot.bottom),
      center + barWidth / 2,
      plot.bottom,
    );
  }

  int? slotAt(double dx) {
    if (values.isEmpty) return null;
    final raw = ((dx - plot.left) / slotWidth).floor();
    if (raw < 0 || raw >= values.length) return null;
    return raw;
  }
}

// ---------------------------------------------------------------------------
// Painter
// ---------------------------------------------------------------------------

class _TrendPainter extends CustomPainter {
  _TrendPainter({
    required this.geometry,
    required this.input,
    required this.peakIndex,
    this.selectedIndex,
  });

  final _ChartGeometry geometry;
  final TrendChartInput input;
  final int peakIndex;
  final int? selectedIndex;

  static const Color _gridLine = Color(0xFFEDF0F5);
  static const Color _baseline = Color(0xFFDBE0EA);
  static const Color _yLabel = Color(0xFFAEB6C3);
  static const Color _xLabel = Color(0xFF8B95A6);
  static const Color _xLabelSelected = Color(0xFF2F6BFF);
  static const Color _barNormal = Color(0xFF5C88EC);
  static const Color _barPeak = Color(0xFF2F6BFF);
  static const Color _barCurrent = Color(0xFFBDD0F5);
  static const Color _peakLabel = Color(0xFF1B2437);
  static const Color _currentLabel = Color(0xFF98A3B7);

  /// The pressed / active fill for a selected bar (spec §4.4).
  static Color _pressed(Color base) {
    if (base == _barPeak) return const Color(0xFF1E56E8);
    if (base == _barCurrent) return const Color(0xFFA9C1F0);
    return const Color(0xFF3F6FE3);
  }

  @override
  void paint(Canvas canvas, Size size) {
    final plot = geometry.plot;

    // Horizontal grid + y tick labels at each tickUnit (nothing at 0).
    final gridPaint = Paint()
      ..color = _gridLine
      ..strokeWidth = 1;
    for (var v = geometry.tickUnit; v <= geometry.upperBound + 0.01; v += geometry.tickUnit) {
      final y = geometry.yOf(v);
      canvas.drawLine(Offset(plot.left, y), Offset(plot.right, y), gridPaint);
      _paintText(
        canvas,
        compactTrendValue(v.round()),
        Offset(plot.left - 6, y),
        const TextStyle(color: _yLabel, fontSize: 10, fontWeight: FontWeight.w600),
        align: TextAlign.right,
        anchor: _Anchor.centerRight,
        maxWidth: _ChartGeometry._yAxisWidth,
      );
    }

    // Baseline rule (replaces the y=0 grid line).
    canvas.drawLine(
      Offset(plot.left, plot.bottom),
      Offset(plot.right, plot.bottom),
      Paint()
        ..color = _baseline
        ..strokeWidth = 1,
    );

    final n = input.bars.length;
    // Show every category label once each slot is wide enough to fit one;
    // otherwise thin to ~every 5th + the ends (dense portrait daily view).
    final showEveryLabel = geometry.slotWidth >= 15 || n < 16;

    for (var i = 0; i < n; i++) {
      final isCurrent = i == input.currentIndex;
      final isPeak = i == peakIndex && input.bars[i].value > 0;
      final isSelected = i == selectedIndex;
      var color = isCurrent
          ? _barCurrent
          : isPeak
              ? _barPeak
              : _barNormal;
      if (isSelected) color = _pressed(color);

      final rect = geometry.barRect(i);
      if (rect.height > 0) {
        canvas.drawRRect(
          RRect.fromRectAndCorners(
            rect,
            topLeft: const Radius.circular(4),
            topRight: const Radius.circular(4),
          ),
          Paint()..color = color,
        );
      }

      // On-bar value label: only the peak and the current bar.
      if (input.bars[i].value > 0 && (isPeak || isCurrent)) {
        final peakStyled = isPeak; // peak wins when the index is both
        final labelY = (rect.top - 6).clamp(plot.top + 1, plot.bottom);
        _paintText(
          canvas,
          compactTrendValue(input.bars[i].value),
          Offset(geometry.slotCenter(i), labelY),
          TextStyle(
            color: peakStyled ? _peakLabel : _currentLabel,
            fontSize: peakStyled ? 10.5 : 10,
            fontWeight: peakStyled ? FontWeight.w800 : FontWeight.w700,
          ),
          anchor: _Anchor.bottomCenter,
        );
      }

      // X tick label — always drawn for the selected bar and the ends.
      final showLabel = showEveryLabel ||
          isSelected ||
          i == 0 ||
          i == n - 1 ||
          (i + 1) % 5 == 0;
      if (showLabel) {
        _paintText(
          canvas,
          input.bars[i].label,
          Offset(geometry.slotCenter(i), plot.bottom + 6),
          TextStyle(
            color: isSelected ? _xLabelSelected : _xLabel,
            fontSize: 10,
            fontWeight: isSelected ? FontWeight.w800 : FontWeight.w600,
          ),
          anchor: _Anchor.topCenter,
        );
      }
    }
  }

  void _paintText(
    Canvas canvas,
    String text,
    Offset at,
    TextStyle style, {
    TextAlign align = TextAlign.center,
    _Anchor anchor = _Anchor.topCenter,
    double maxWidth = 200,
  }) {
    final painter = TextPainter(
      text: TextSpan(text: text, style: style),
      textAlign: align,
      textDirection: TextDirection.ltr,
    )..layout(maxWidth: maxWidth);

    var dx = at.dx;
    var dy = at.dy;
    switch (anchor) {
      case _Anchor.topCenter:
        dx -= painter.width / 2;
      case _Anchor.bottomCenter:
        dx -= painter.width / 2;
        dy -= painter.height;
      case _Anchor.centerRight:
        dx -= painter.width;
        dy -= painter.height / 2;
    }
    painter.paint(canvas, Offset(dx, dy));
  }

  @override
  bool shouldRepaint(_TrendPainter old) =>
      old.geometry.size != geometry.size ||
      old.input != input ||
      old.peakIndex != peakIndex ||
      old.selectedIndex != selectedIndex;
}

enum _Anchor { topCenter, bottomCenter, centerRight }

// ---------------------------------------------------------------------------
// Tooltip + empty state
// ---------------------------------------------------------------------------

class _Tooltip extends StatelessWidget {
  const _Tooltip({
    required this.geometry,
    required this.index,
    required this.bar,
    required this.unit,
  });

  final _ChartGeometry geometry;
  final int index;
  final TrendBar bar;
  final String unit;

  @override
  Widget build(BuildContext context) {
    final barRect = geometry.barRect(index);
    return Positioned(
      left: 0,
      right: 0,
      top: (barRect.top - 46).clamp(0.0, geometry.size.height),
      child: IgnorePointer(
        child: Align(
          alignment: Alignment(
            ((geometry.slotCenter(index) / geometry.size.width) * 2 - 1)
                .clamp(-1.0, 1.0),
            0,
          ),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: const Color(0xFF0F1729),
              borderRadius: BorderRadius.circular(9),
              border: Border.all(color: const Color(0x1AFFFFFF)),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x6B0A1020),
                  blurRadius: 18,
                  offset: Offset(0, 8),
                ),
              ],
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 6,
                  height: 6,
                  decoration: const BoxDecoration(
                    color: Color(0xFF5C88EC),
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 6),
                Text(
                  formatThousands(bar.value, separator: ','),
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                if (unit.isNotEmpty) ...[
                  const SizedBox(width: 6),
                  Text(
                    unit.toUpperCase(),
                    style: const TextStyle(
                      color: Color(0x85FFFFFF),
                      fontSize: 9.5,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.5,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _EmptyPlot extends StatelessWidget {
  const _EmptyPlot({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.bar_chart_rounded, size: 28, color: Color(0xFFC6CDD9)),
          const SizedBox(height: 12),
          Text(
            text,
            style: const TextStyle(fontSize: 13, color: Color(0xFF8993A6)),
          ),
        ],
      ),
    );
  }
}
