import 'package:supabase_flutter/supabase_flutter.dart';

import '../models.dart';
import '../repositories.dart';
import '../trend.dart';
import 'date_text_filter.dart';
import 'row_mappers.dart';
import 'supabase_session.dart';

/// Live [AttendanceRepository] backed by the Supabase `attendance` table.
///
/// Table columns (Postgres):
///   id BIGINT, member_id BIGINT (FK -> members(id)),
///   date TEXT (ISO-8601 local date-time, e.g. `2026-08-29T14:21:16.887`),
///   synced INT
///
/// Mirrors the desktop `AttendanceDao`: [findAttendance] / [countAttendance]
/// take an optional year / month / day, matched against the stored `date` text
/// the same way the desktop `strftime('%Y' | '%m' | '%d', ...)` filters do —
/// each supplied component pins those digits, the rest stay wild.
class SupabaseAttendanceRepository implements AttendanceRepository {
  const SupabaseAttendanceRepository();

  /// Embeds the related member so `attendanceFromRow` can fill `memberName`.
  static const String _columns =
      'id, member_id, date, member:members ( id, first_name, last_name )';

  @override
  Future<List<AttendanceEntry>> fetchRecentAttendance() => findAttendance();

  @override
  Future<List<AttendanceEntry>> findAttendance({
    int? year,
    int? month,
    int? day,
  }) async {
    final pattern = isoDateLikePattern(year, month, day);
    final rows = await SupabaseSession.instance.read((client) {
      var query = client.from('attendance').select(_columns);
      if (pattern != null) query = query.like('date', pattern);
      return query.order('date', ascending: false);
    });
    return [
      for (final row in rows as List)
        attendanceFromRow(Map<String, dynamic>.from(row as Map)),
    ];
  }

  @override
  Future<int> countAttendance({int? year, int? month, int? day}) async {
    final pattern = isoDateLikePattern(year, month, day);
    return SupabaseSession.instance.read((client) {
      var query = client.from('attendance').count(CountOption.exact);
      if (pattern != null) query = query.like('date', pattern);
      return query;
    });
  }

  @override
  Future<TrendSeries> checkInTrend({
    required TrendMode mode,
    required int year,
    required int month,
  }) async {
    // One slim query — just the date column, no member join.
    final pattern = switch (mode) {
      TrendMode.daily => isoDateLikePattern(year, month, null),
      TrendMode.monthly => isoDateLikePattern(year, null, null),
      TrendMode.allTime => null,
    };
    final rows = await SupabaseSession.instance.read((client) {
      var query = client.from('attendance').select('date');
      if (pattern != null) query = query.like('date', pattern);
      return query;
    });
    final times = <DateTime>[];
    for (final row in rows as List) {
      final when = dateFromText(Map<String, dynamic>.from(row as Map)['date']);
      if (when != null) times.add(when);
    }
    final series = bucketTrend(
      mode,
      times.map((t) => (when: t, weight: 1)),
      year: year,
      month: month,
    );
    if (mode != TrendMode.daily) return series;
    final b = busiestOf(
      times.where((t) => t.year == year && t.month == month),
    );
    return TrendSeries(
      mode: series.mode,
      values: series.values,
      busiestWeekday: b.weekday,
      busiestHour: b.hour,
    );
  }
}
