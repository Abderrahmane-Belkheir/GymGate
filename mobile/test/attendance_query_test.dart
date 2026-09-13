import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/mock/mock_repositories.dart';
import 'package:gymgate_app/data/models.dart';
import 'package:gymgate_app/data/repositories.dart';

/// Locks in the desktop `AttendanceDao.find` / `.count` semantics that the
/// Supabase repository mirrors: each supplied year / month / day component
/// narrows the match, an omitted one stays wild, newest first.
void main() {
  final reference = DateTime(2026, 8, 29, 19);
  late AttendanceRepository attendance;

  setUp(() {
    attendance = buildMockRepositories(reference: reference).attendance;
  });

  test('no filter returns every check-in, newest first', () async {
    final rows = await attendance.findAttendance();
    expect(rows, isNotEmpty);
    for (var i = 1; i < rows.length; i++) {
      expect(rows[i - 1].checkIn.isAfter(rows[i].checkIn), isTrue);
    }
    expect(await attendance.countAttendance(), rows.length);
  });

  test('year + month + day matches only that calendar day', () async {
    final rows = await attendance.findAttendance(year: 2026, month: 8, day: 29);
    expect(rows, isNotEmpty);
    expect(
      rows,
      everyElement(predicate<AttendanceEntry>((a) {
        final d = a.checkIn;
        return d.year == 2026 && d.month == 8 && d.day == 29;
      })),
    );
    expect(await attendance.countAttendance(year: 2026, month: 8, day: 29),
        rows.length);

    // A day with no check-ins comes back empty.
    expect(await attendance.countAttendance(year: 2026, month: 8, day: 1), 0);
    expect(await attendance.countAttendance(year: 2025), 0);
  });

  test('an omitted component stays wild', () async {
    final allCount = await attendance.countAttendance();
    // The seed check-ins all fall on one August day, so a bare month / year
    // filter still matches them all, but a different month matches none.
    expect(await attendance.countAttendance(month: 8), allCount);
    expect(await attendance.countAttendance(year: 2026), allCount);
    expect(await attendance.countAttendance(month: 1), 0);
  });
}
