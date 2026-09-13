import 'dart:math' as math;

/// Granularity of a trend bar chart — the desktop Payments / Attendance
/// "Statistics" switch has the same three modes.
enum TrendMode { daily, monthly, allTime }

/// A resolved bar-chart series produced by the data layer. The numbers are
/// final; the screen turns this into localized labels + a `TrendChartInput`.
class TrendSeries {
  const TrendSeries({
    required this.mode,
    required this.values,
    this.years = const [],
    this.busiestWeekday,
    this.busiestHour,
  });

  final TrendMode mode;

  /// One bucket per category, `>= 0`. daily → day `1..N` of the month;
  /// monthly → month `1..12`; allTime → one per entry in [years].
  final List<int> values;

  /// allTime only: the calendar year each bucket represents. Empty otherwise.
  final List<int> years;

  /// Attendance daily view only: `1 = Monday … 7 = Sunday` of the busiest
  /// weekday, and the busiest hour `0..23`. Null when not applicable / no data.
  final int? busiestWeekday;
  final int? busiestHour;
}

/// A single timestamped contribution to a series (a check-in → weight 1, a
/// payment → its amount).
typedef TrendEvent = ({DateTime when, int weight});

/// Buckets [events] into a [TrendSeries] for [mode]. [year] / [month] scope the
/// daily and monthly views (ignored for all-time). [now] is only used by the
/// caller for `currentIndex`; bucketing itself does not need it.
TrendSeries bucketTrend(
  TrendMode mode,
  Iterable<TrendEvent> events, {
  required int year,
  required int month,
}) {
  switch (mode) {
    case TrendMode.daily:
      final days = DateTime(year, month + 1, 0).day;
      final values = List<int>.filled(days, 0);
      for (final e in events) {
        final d = e.when;
        if (d.year == year && d.month == month) values[d.day - 1] += e.weight;
      }
      return TrendSeries(mode: mode, values: values);

    case TrendMode.monthly:
      final values = List<int>.filled(12, 0);
      for (final e in events) {
        if (e.when.year == year) values[e.when.month - 1] += e.weight;
      }
      return TrendSeries(mode: mode, values: values);

    case TrendMode.allTime:
      final byYear = <int, int>{};
      for (final e in events) {
        byYear[e.when.year] = (byYear[e.when.year] ?? 0) + e.weight;
      }
      if (byYear.isEmpty) {
        return const TrendSeries(mode: TrendMode.allTime, values: []);
      }
      final lo = byYear.keys.reduce(math.min);
      final hi = byYear.keys.reduce(math.max);
      final years = [for (var y = lo; y <= hi; y++) y];
      return TrendSeries(
        mode: mode,
        values: [for (final y in years) byYear[y] ?? 0],
        years: years,
      );
  }
}

/// The most frequent weekday (`1..7`) and hour (`0..23`) across [times], each
/// the earliest key on a tie. Null when [times] is empty.
({int? weekday, int? hour}) busiestOf(Iterable<DateTime> times) {
  final byDay = <int, int>{};
  final byHour = <int, int>{};
  for (final t in times) {
    byDay[t.weekday] = (byDay[t.weekday] ?? 0) + 1;
    byHour[t.hour] = (byHour[t.hour] ?? 0) + 1;
  }
  int? top(Map<int, int> counts) {
    int? best;
    var bestCount = 0;
    for (final key in counts.keys.toList()..sort()) {
      if (counts[key]! > bestCount) {
        bestCount = counts[key]!;
        best = key;
      }
    }
    return best;
  }

  return (weekday: top(byDay), hour: top(byHour));
}

/// The index of a still-open period (drawn lighter), per
/// `flutter-trend-barchart-spec.md` §2. `-1` when the viewed period is historic.
int currentTrendIndex(
  TrendMode mode, {
  required int year,
  required int month,
  DateTime? now,
}) {
  final t = now ?? DateTime.now();
  switch (mode) {
    case TrendMode.daily:
      return (year == t.year && month == t.month) ? t.day - 1 : -1;
    case TrendMode.monthly:
      return year == t.year ? t.month - 1 : -1;
    case TrendMode.allTime:
      return -1;
  }
}

// ---------------------------------------------------------------------------
// Pure helpers ported verbatim from flutter-trend-barchart-spec.md §3.
// ---------------------------------------------------------------------------

class TrendStats {
  const TrendStats(this.total, this.peak, this.peakIndex, this.average);

  final int total;
  final int peak;

  /// First index that reaches [peak]; `-1` when every value is zero.
  final int peakIndex;

  /// Trailing-trimmed mean: divided by the count up to and including the last
  /// non-zero period, so trailing empty days don't drag it toward zero. Not
  /// drawn on the chart — used only for the header hint.
  final double average;
}

TrendStats summarize(List<int> values) {
  var total = 0, peak = 0, peakIndex = -1, lastActive = -1;
  for (var i = 0; i < values.length; i++) {
    final v = values[i];
    total += v;
    if (v > peak) {
      peak = v;
      peakIndex = i;
    }
    if (v > 0) lastActive = i;
  }
  var average = 0.0;
  if (lastActive >= 0) {
    var windowSum = 0;
    for (var i = 0; i <= lastActive; i++) {
      windowSum += values[i];
    }
    average = windowSum / (lastActive + 1);
  }
  return TrendStats(total, peak, peakIndex, average);
}

/// Returns `[upperBound, tickUnit]` — ~5 intervals on 1/2/2.5/5/10 × 10^n steps.
/// Magnitude floored at 1 so integer counts never get fractional ticks.
List<num> niceScale(int max) {
  if (max <= 0) return [10, 2];
  final rough = max / 5.0;
  final mag = math.max(1, math.pow(10, (math.log(rough) / math.ln10).floor()));
  final norm = rough / mag;
  final step = norm <= 1
      ? 1
      : norm <= 2
          ? 2
          : norm <= 2.5
              ? 2.5
              : norm <= 5
                  ? 5
                  : 10;
  final unit = step * mag;
  final upper = ((max + unit * 0.15) / unit).ceil() * unit;
  return [upper, unit];
}

/// `12000 -> "12k"`, `1500000 -> "1.5M"`, `950 -> "950"`. Axis + on-bar labels
/// only — the tooltip uses full thousands-separated digits.
String compactTrendValue(int v) {
  if (v >= 1000000) return '${_trimZero(v / 1000000.0)}M';
  if (v >= 1000) return '${_trimZero(v / 1000.0)}k';
  return v.toString();
}

String _trimZero(double d) =>
    d == d.floorToDouble() ? d.toInt().toString() : d.toStringAsFixed(1);
