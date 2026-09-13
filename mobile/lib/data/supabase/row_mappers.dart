import 'dart:typed_data';

import '../models.dart';

/// Maps raw Supabase/PostgREST rows onto the domain models. Shared by the
/// Supabase repositories so the `plans` shape is decoded the same way whether it
/// arrives on its own or embedded inside a member row.

Gender? genderFromSexe(Object? value) {
  switch ((value as String?)?.trim().toUpperCase()) {
    case 'FEMALE':
    case 'F':
      return Gender.female;
    case 'MALE':
    case 'M':
      return Gender.male;
    default:
      return null;
  }
}

bool boolFromInt(Object? value) =>
    value == 1 || value == true || value == '1' || value == 'true';

DateTime? dateFromText(Object? value) {
  if (value is! String || value.isEmpty) return null;
  return DateTime.tryParse(value);
}

int? intOrNull(Object? value) => (value as num?)?.toInt();

/// Decodes a PostgREST `bytea` value — a `\x`-prefixed hex string like
/// `\xffd8ffe0…` — into bytes. Null / malformed input → null.
Uint8List? bytesFromHex(Object? value) {
  if (value is! String || value.isEmpty) return null;
  final hex = value.startsWith(r'\x') ? value.substring(2) : value;
  if (hex.isEmpty || hex.length.isOdd) return null;
  final out = Uint8List(hex.length ~/ 2);
  for (var i = 0; i < out.length; i++) {
    final byte = int.tryParse(hex.substring(i * 2, i * 2 + 2), radix: 16);
    if (byte == null) return null;
    out[i] = byte;
  }
  return out;
}

/// `plans` row → [Plan].
Plan planFromRow(Map<String, dynamic> row) {
  final durationMonths = intOrNull(row['duration_months']) ?? 1;
  final name = (row['name'] as String?)?.trim();

  return Plan(
    id: row['id'].toString(),
    name: (name == null || name.isEmpty) ? 'Unnamed plan' : name,
    priceDzd: (row['price'] as num?)?.round() ?? 0,
    billingDays: durationMonths * 30,
    visitsPerMonth: intOrNull(row['days_per_month']) ?? 0, // null -> unlimited
    cardioIncluded: boolFromInt(row['cardio']),
    restrictedTo: genderFromSexe(row['sexe']),
  );
}

/// `seance` row → [Seance]. `cardio` is a 0/1 int; `sexe` scopes the rate.
Seance seanceFromRow(Map<String, dynamic> row) {
  return Seance(
    id: row['id'].toString(),
    priceDzd: (row['price'] as num?)?.round() ?? 0,
    cardio: boolFromInt(row['cardio']),
    restrictedTo: genderFromSexe(row['sexe']),
  );
}

/// `members` row (with an embedded `plan` and, LEFT-joined on `members.id`, an
/// embedded `photo` from `member_photos`) → [Member].
Member memberFromRow(Map<String, dynamic> row) {
  final planValue = row['plan'];
  final plan = planValue is Map
      ? planFromRow(Map<String, dynamic>.from(planValue))
      : null;

  return Member(
    id: row['id'].toString(),
    firstName: (row['first_name'] as String?) ?? '',
    lastName: (row['last_name'] as String?) ?? '',
    gender: genderFromSexe(row['sexe']) ?? Gender.male,
    phone: (row['phone_number'] as String?) ?? '',
    plan: plan,
    membershipStart: dateFromText(row['start_date']),
    membershipEnd: dateFromText(row['end_date']),
    remainingDays: intOrNull(row['remaining_days']),
    createdAt: dateFromText(row['created_at']),
    photoBytes: _photoBytes(row['photo']),
  );
}

/// The embedded `member_photos` row — a single object for the one-to-one
/// relationship, but tolerate a one-element list too.
Uint8List? _photoBytes(Object? photo) {
  if (photo is Map) return bytesFromHex(photo['image']);
  if (photo is List && photo.isNotEmpty && photo.first is Map) {
    return bytesFromHex((photo.first as Map)['image']);
  }
  return null;
}

/// `attendance` row (optionally with an embedded `member`) → [AttendanceEntry].
///
/// The table records check-in events only — there is no check-out column — so
/// [AttendanceEntry.checkOut] stays null and the member always reads as "inside".
AttendanceEntry attendanceFromRow(Map<String, dynamic> row) {
  final memberValue = row['member'];
  final member = memberValue is Map
      ? Map<String, dynamic>.from(memberValue)
      : const <String, dynamic>{};

  final first = ((member['first_name'] as String?) ?? '').trim();
  final last = ((member['last_name'] as String?) ?? '').trim();

  return AttendanceEntry(
    id: row['id'].toString(),
    memberId: (row['member_id'] ?? member['id']).toString(),
    memberName: [first, last].where((s) => s.isNotEmpty).join(' '),
    checkIn: dateFromText(row['date']) ?? DateTime.fromMillisecondsSinceEpoch(0),
  );
}

/// `payments` row (with embedded `member` / `plan`) → [Payment].
///
/// The table has no payment-method column, so [Payment.method] stays null.
/// A payment carries either a plan (`planName`) or a séance ([Payment.seance]).
/// The séance is resolved from an embedded `seance` object when present, else
/// looked up in [seancesById] by `seance_id` (there is no `payments→seance` FK
/// for PostgREST to embed, so the repository stitches it).
Payment paymentFromRow(
  Map<String, dynamic> row, {
  Map<String, Seance> seancesById = const {},
}) {
  final memberValue = row['member'];
  final member = memberValue is Map
      ? Map<String, dynamic>.from(memberValue)
      : const <String, dynamic>{};
  final planValue = row['plan'];
  final plan = planValue is Map
      ? Map<String, dynamic>.from(planValue)
      : const <String, dynamic>{};
  final seanceValue = row['seance'];
  final seance = seanceValue is Map
      ? seanceFromRow(Map<String, dynamic>.from(seanceValue))
      : seancesById[row['seance_id']?.toString()];

  final first = ((member['first_name'] as String?) ?? '').trim();
  final last = ((member['last_name'] as String?) ?? '').trim();
  final planName = (plan['name'] as String?)?.trim() ?? (row['plan_name'] as String?);

  // A séance ("walk-in") payment has no member.
  final memberId = (row['member_id'] ?? member['id'])?.toString() ?? '';

  return Payment(
    id: row['id']?.toString() ?? '$memberId-${row['payment_date']}',
    memberId: memberId,
    memberName: [first, last].where((s) => s.isNotEmpty).join(' '),
    amountDzd: (row['amount'] as num?)?.round() ?? 0,
    date: dateFromText(row['payment_date']) ??
        DateTime.fromMillisecondsSinceEpoch(0),
    planName: (planName == null || planName.isEmpty) ? null : planName,
    seance: seance,
  );
}
