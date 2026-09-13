/// Small display helpers shared across screens. Presentation only.
library;

const List<String> _months = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

const List<String> _weekdays = [
  'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday',
];

const String _thinSpace = ' ';

/// `12 500` — digits grouped in threes by [separator] (default thin space).
String formatThousands(int amount, {String separator = _thinSpace}) {
  final digits = amount.abs().toString();
  final buffer = StringBuffer();
  for (var i = 0; i < digits.length; i++) {
    if (i != 0 && (digits.length - i) % 3 == 0) buffer.write(separator);
    buffer.write(digits[i]);
  }
  return '${amount < 0 ? '-' : ''}$buffer';
}

/// `12 500 DZD`
String formatDzd(int amount) => '${formatThousands(amount)} DZD';

/// `4 Jun 2026`
String formatDate(DateTime d) => '${d.day} ${_months[d.month - 1]} ${d.year}';

/// `2026-08-28`
String formatIsoDate(DateTime d) => '${d.year.toString().padLeft(4, '0')}-'
    '${d.month.toString().padLeft(2, '0')}-'
    '${d.day.toString().padLeft(2, '0')}';

/// `2026-08-28 -> 2026-09-28`
String formatIsoRange(DateTime start, DateTime end) =>
    '${formatIsoDate(start)} → ${formatIsoDate(end)}';

/// `4 Jun -> 3 Sep` (year omitted when both fall in the same year)
String formatDateRange(DateTime start, DateTime end) {
  final sameYear = start.year == end.year;
  final startText = sameYear
      ? '${start.day} ${_months[start.month - 1]}'
      : formatDate(start);
  return '$startText → ${formatDate(end)}';
}

/// `18:42`
String formatTime(DateTime d) =>
    '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';

/// `Thursday, 28 August`
String formatLongWeekday(DateTime d) =>
    '${_weekdays[d.weekday - 1]}, ${d.day} ${_months[d.month - 1]}';

/// `Ahmed Belkheir` → `AB` — first letters of the first and last name words.
String initialsFrom(String name) {
  final parts =
      name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
  if (parts.isEmpty) return '?';
  if (parts.length == 1) return parts.first[0].toUpperCase();
  return (parts.first[0] + parts.last[0]).toUpperCase();
}

/// `18 days left` / `Expires today` / `Expired 3 days ago`
String remainingLabel(int? days) {
  if (days == null) return 'No plan';
  if (days == 0) return 'Expires today';
  if (days > 0) return '$days ${days == 1 ? 'day' : 'days'} left';
  final past = -days;
  return 'Expired $past ${past == 1 ? 'day' : 'days'} ago';
}
