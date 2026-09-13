/// SQL `LIKE` pattern for an ISO-8601 date-time **text** column, covering
/// whichever of year / month / day was supplied (`_` matches any single digit).
///
/// e.g. `isoDateLikePattern(null, 8, null)` -> `____-08-%` ("August, any year").
/// Returns null when nothing is supplied — meaning no filter, every row.
///
/// This is the PostgREST stand-in for the desktop DAOs'
/// `strftime('%Y' | '%m' | '%d', <col>) = ?` filters, and works because the
/// stored values are lexicographically ordered ISO text (`YYYY-MM-DD...`).
String? isoDateLikePattern(int? year, int? month, int? day) {
  if (year == null && month == null && day == null) return null;
  final y = year != null ? year.toString().padLeft(4, '0') : '____';
  final m = month != null ? month.toString().padLeft(2, '0') : '__';
  final d = day != null ? day.toString().padLeft(2, '0') : '__';
  return '$y-$m-$d%';
}
