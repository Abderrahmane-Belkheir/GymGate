import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/mock/mock_repositories.dart';
import 'package:gymgate_app/data/trend.dart';

void main() {
  group('summarize', () {
    test('total, peak and the first peak index', () {
      final s = summarize([2, 5, 5, 1]);
      expect(s.total, 13);
      expect(s.peak, 5);
      expect(s.peakIndex, 1); // first index that reaches the peak
    });

    test('trailing-trimmed mean ignores empty periods after the last active', () {
      // last non-zero is index 2 → divide by 3, not 6.
      final s = summarize([3, 0, 3, 0, 0, 0]);
      expect(s.average, closeTo(2.0, 1e-9));
    });

    test('all-zero series', () {
      final s = summarize([0, 0, 0]);
      expect(s.peakIndex, -1);
      expect(s.average, 0);
    });
  });

  group('niceScale', () {
    test('payments peak 8000 → 10k axis on 2k ticks', () {
      expect(niceScale(8000), [10000, 2000]);
    });

    test('attendance peak 7 → 8 axis on integer 2s', () {
      expect(niceScale(7), [8, 2]);
    });

    test('zero peak falls back to [10, 2]', () {
      expect(niceScale(0), [10, 2]);
    });
  });

  group('compactTrendValue', () {
    test('k / M suffixes, trimmed', () {
      expect(compactTrendValue(950), '950');
      expect(compactTrendValue(8000), '8k');
      expect(compactTrendValue(12000), '12k');
      expect(compactTrendValue(1500000), '1.5M');
    });
  });

  group('bucketTrend', () {
    final events = <TrendEvent>[
      (when: DateTime(2026, 8, 6, 10), weight: 5),
      (when: DateTime(2026, 8, 6, 18), weight: 3),
      (when: DateTime(2026, 8, 30, 9), weight: 2),
      (when: DateTime(2025, 12, 1), weight: 7),
    ];

    test('daily → one bucket per day of the month, zero-filled', () {
      final s = bucketTrend(TrendMode.daily, events, year: 2026, month: 8);
      expect(s.values.length, 31);
      expect(s.values[5], 8); // Aug 6
      expect(s.values[29], 2); // Aug 30
      expect(s.values[0], 0);
    });

    test('monthly → 12 buckets for the year', () {
      final s = bucketTrend(TrendMode.monthly, events, year: 2026, month: 8);
      expect(s.values.length, 12);
      expect(s.values[7], 10); // August
      expect(s.values.where((v) => v != 0).length, 1);
    });

    test('allTime → contiguous years from min to max', () {
      final s = bucketTrend(TrendMode.allTime, events, year: 2026, month: 8);
      expect(s.years, [2025, 2026]);
      expect(s.values, [7, 10]);
    });
  });

  group('currentTrendIndex', () {
    final now = DateTime(2026, 8, 29, 12);

    test('daily: the running day only in the current month', () {
      expect(currentTrendIndex(TrendMode.daily, year: 2026, month: 8, now: now),
          28);
      expect(currentTrendIndex(TrendMode.daily, year: 2026, month: 7, now: now),
          -1);
    });

    test('monthly: the running month only in the current year', () {
      expect(
          currentTrendIndex(TrendMode.monthly, year: 2026, month: 8, now: now),
          7);
      expect(
          currentTrendIndex(TrendMode.monthly, year: 2025, month: 8, now: now),
          -1);
    });

    test('allTime: never', () {
      expect(
          currentTrendIndex(TrendMode.allTime, year: 2026, month: 8, now: now),
          -1);
    });
  });

  group('mock repositories', () {
    final reference = DateTime(2026, 8, 29, 19);

    test('revenueTrend daily buckets the seeded payments by day', () async {
      final repo = buildMockRepositories(reference: reference).payments;
      final s = await repo.revenueTrend(mode: TrendMode.daily, year: 2026, month: 8);
      expect(s.values.length, 31);
      expect(s.values[28], 8000); // Aug 29: 2000 + 4000 + 2000
      expect(s.values[27], 4200); // Aug 28: 4000 plan + 200 séance
      expect(summarize(s.values).total, 18200); // séance payments count too
    });

    test('checkInTrend daily reports the busiest weekday and hour', () async {
      final repo = buildMockRepositories(reference: reference).attendance;
      final s =
          await repo.checkInTrend(mode: TrendMode.daily, year: 2026, month: 8);
      expect(s.values[28], 6); // six seeded check-ins, all on Aug 29
      expect(s.busiestWeekday, DateTime(2026, 8, 29).weekday);
      expect(s.busiestHour, 17); // 17:xx appears twice, the earliest hour to
    });

    test('checkInTrend only fills the busiest line for the daily mode', () async {
      final repo = buildMockRepositories(reference: reference).attendance;
      final monthly =
          await repo.checkInTrend(mode: TrendMode.monthly, year: 2026, month: 8);
      expect(monthly.busiestWeekday, isNull);
      expect(monthly.values[7], 6); // August
    });
  });
}
