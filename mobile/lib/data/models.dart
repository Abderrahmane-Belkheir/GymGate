import 'package:flutter/foundation.dart';

/// Domain models for GymGate. These are plain immutable value types with no
/// dependency on any data source. The mock layer builds them from static data
/// today; a Supabase/API layer will build the same types from JSON later.

enum Gender { male, female }

/// True when [a] and [b] land on the same calendar day (time ignored).
bool _sameDay(DateTime a, DateTime b) =>
    a.year == b.year && a.month == b.month && a.day == b.day;

/// Membership validity, derived from the plan end date.
enum MembershipStatus { active, expiring, expired, none }

/// Days-left threshold below which a still-valid membership is flagged
/// "expiring" (desktop `EXPIRING_SOON_DAYS`, see member-stats-consistency §3).
const int kExpiringSoonDays = 7;

@immutable
class Plan {
  const Plan({
    required this.id,
    required this.name,
    required this.priceDzd,
    this.billingDays = 30,
    this.visitsPerMonth = 0,
    this.cardioIncluded = false,
    this.restrictedTo,
    this.description,
  });

  final String id;
  final String name;
  final int priceDzd;

  /// Billing period length. 30 → "1 month".
  final int billingDays;

  /// Allowed visits per month. `0` means unlimited ("open") access.
  final int visitsPerMonth;

  final bool cardioIncluded;

  /// `null` → offered to everyone.
  final Gender? restrictedTo;

  final String? description;

  bool get isUnlimited => visitsPerMonth <= 0;

  /// Billing period in whole months (`billingDays` is `months * 30`).
  int get durationMonths => (billingDays / 30).round().clamp(1, 1 << 20);

  String get billingLabel {
    if (billingDays % 30 == 0) {
      final months = billingDays ~/ 30;
      return months == 1 ? '1 month' : '$months months';
    }
    return '$billingDays days';
  }

  String get visitsLabel => isUnlimited ? 'Unlimited' : '$visitsPerMonth';
}

/// A single-session ("séance") drop-in price, from the desktop `seance` table.
/// The desktop Plans screen shows these under "الحصة / La séance": one price
/// with cardio, one without, per gender.
@immutable
class Seance {
  const Seance({
    required this.id,
    required this.priceDzd,
    this.cardio = false,
    this.restrictedTo,
  });

  final String id;
  final int priceDzd;

  /// Whether this rate is for a session that includes cardio.
  final bool cardio;

  /// `null` → offered to everyone.
  final Gender? restrictedTo;
}

@immutable
class Member {
  const Member({
    required this.id,
    required this.firstName,
    required this.lastName,
    required this.gender,
    required this.phone,
    this.plan,
    this.membershipStart,
    this.membershipEnd,
    this.remainingDays,
    this.createdAt,
    this.photoBytes,
  });

  final String id;
  final String firstName;
  final String lastName;
  final Gender gender;
  final String phone;
  final Plan? plan;
  final DateTime? membershipStart;
  final DateTime? membershipEnd;

  /// Stored day count from the backend (`members.remaining_days`). May be null.
  final int? remainingDays;

  /// When the member record was first created (`members.created_at`). Distinct
  /// from [membershipStart]: a renewal moves the start date but not this.
  final DateTime? createdAt;

  /// Decoded profile photo (`member_photos.image`, a JPEG), when the member has
  /// one. Null → the avatar shows [initials].
  final Uint8List? photoBytes;

  /// A copy with a different [remainingDays] — used for the optimistic local
  /// decrement when a check-in event arrives before the backend syncs.
  Member withRemainingDays(int? value) => Member(
        id: id,
        firstName: firstName,
        lastName: lastName,
        gender: gender,
        phone: phone,
        plan: plan,
        membershipStart: membershipStart,
        membershipEnd: membershipEnd,
        remainingDays: value,
        createdAt: createdAt,
        photoBytes: photoBytes,
      );

  // The three predicates below are ports of the desktop
  // `MemberDao.count(MemberCountType)` SQL — `today` is `LocalDate.now()`.
  // They MUST stay consistent with [isExpiredOn] / [isValidAt] / [statusFrom]
  // per member-stats-consistency §3 and §4.

  /// `MemberCountType.NEW` — `date(created_at) = today`.
  ///
  /// Note (member-stats-consistency §2.1): `created_at` is stored UTC while
  /// `today` is local, so a member created in the 00:00–01:00 local window is
  /// counted on the previous day. Matches the desktop; not "fixed" here either.
  bool isNewOn(DateTime today) =>
      createdAt != null && _sameDay(createdAt!, today);

  /// `MemberCountType.RENEW` —
  /// `date(start_date) = today AND date(created_at) != date(start_date)`.
  /// A null `created_at` fails the `!=` (SQL `NULL != x` → not true), so it
  /// does not count.
  bool isRenewedOn(DateTime today) {
    final start = membershipStart;
    if (start == null || !_sameDay(start, today)) return false;
    return createdAt != null && !_sameDay(createdAt!, start);
  }

  /// `MemberCountType.EXPIRED` / `countsAsExpiring` (member-stats-consistency
  /// §3.1 row 4, §4.3). With SQL `AND`/`OR` precedence this is
  /// `date(end_date) = today OR (remaining_days <= 0 AND <checked in today>)`.
  /// [attendedToday] answers the correlated `EXISTS` subquery on `attendance`.
  ///
  /// Uses the `<= 0` day threshold (never `== 0`) — §6 rule 1. This is a daily
  /// event, distinct from the `MembershipStatus.expiring` bucket; a member can
  /// be in both (§3.5).
  bool isExpiredOn(DateTime today, {required bool attendedToday}) {
    final end = membershipEnd;
    if (end != null && _sameDay(end, today)) return true;
    final r = remainingDays;
    return r != null && r <= 0 && attendedToday;
  }

  String get fullName => '$firstName $lastName';

  String get initials {
    final f = firstName.isNotEmpty ? firstName[0] : '';
    final l = lastName.isNotEmpty ? lastName[0] : '';
    return (f + l).toUpperCase();
  }

  /// Days left to display: the stored value when present, otherwise computed
  /// from the end date. `null` for open plans or when unknown.
  int? displayRemainingDays(DateTime now) {
    if (plan?.isUnlimited ?? false) return null;
    if (remainingDays != null) return remainingDays;
    final end = membershipEnd;
    if (end == null) return null;
    final today = DateTime(now.year, now.month, now.day);
    final endDay = DateTime(end.year, end.month, end.day);
    return endDay.difference(today).inDays;
  }

  /// Membership validity — the exact inverse of the desktop
  /// `MemberValidationService.validate()` for the plan / date / days conditions
  /// (member-stats-consistency §3.1 row 2, §3.3): plan set, `end_date` is not
  /// before today (the end date is the last usable day), and `remaining_days`
  /// is null or `> 0` (never `!= 0` — §6 rule 1).
  bool isValidAt(DateTime now) {
    if (plan == null) return false;
    final end = membershipEnd;
    if (end == null) return false;
    final today = DateTime(now.year, now.month, now.day);
    final endDay = DateTime(end.year, end.month, end.day);
    final dateValid = !endDay.isBefore(today);
    final r = remainingDays;
    final daysValid = r == null || r > 0;
    return dateValid && daysValid;
  }

  /// Status pill / Active-Inactive filter (member-stats-consistency §3.4).
  MembershipStatus statusFrom(DateTime now) {
    if (plan == null) return MembershipStatus.none;
    if (!isValidAt(now)) return MembershipStatus.expired;
    return _isExpiringSoon(now)
        ? MembershipStatus.expiring
        : MembershipStatus.active;
  }

  /// A valid membership that is close to its end, by either mechanism:
  /// `remaining_days <= 7`, **or** `end_date` within 7 days. The end-date term
  /// keeps a member the "expired today" card counts (plan ends today) from
  /// showing a plain green Active pill (member-stats-consistency §3.4).
  bool _isExpiringSoon(DateTime now) {
    final r = remainingDays;
    if (r != null && r <= kExpiringSoonDays) return true;
    final end = membershipEnd;
    if (end == null) return false;
    final today = DateTime(now.year, now.month, now.day);
    final endDay = DateTime(end.year, end.month, end.day);
    final horizon = DateTime(today.year, today.month, today.day + kExpiringSoonDays);
    return !endDay.isAfter(horizon);
  }
}

enum PaymentMethod { cash, card, transfer }

@immutable
class Payment {
  const Payment({
    required this.id,
    required this.memberId,
    required this.memberName,
    required this.amountDzd,
    required this.date,
    this.method,
    this.planName,
    this.seance,
  });

  final String id;
  final String memberId;
  final String memberName;
  final int amountDzd;
  final DateTime date;

  /// Not recorded by the backend (`payments` has no method column); stays null
  /// on live data.
  final PaymentMethod? method;

  /// Set for a membership payment (`payments.plan_id`).
  final String? planName;

  /// Set for a single-session payment (`payments.seance_id`) instead of
  /// [planName] — carries the rate's gender scope, cardio flag and price.
  final Seance? seance;
}

@immutable
class AttendanceEntry {
  const AttendanceEntry({
    required this.id,
    required this.memberId,
    required this.memberName,
    required this.checkIn,
    this.checkOut,
  });

  final String id;
  final String memberId;
  final String memberName;
  final DateTime checkIn;
  final DateTime? checkOut;

  bool get isInside => checkOut == null;
}

/// Aggregated numbers shown on the Home screen.
@immutable
class DashboardSummary {
  const DashboardSummary({
    required this.totalMembers,
    required this.activeMembers,
    required this.expiredToday,
    required this.checkInsToday,
    required this.currentlyInside,
    required this.revenueTodayDzd,
    required this.newMembersToday,
    required this.renewalsToday,
  });

  final int totalMembers;
  final int activeMembers;
  /// Members counted as expired today — see [Member.isExpiredOn]
  /// (desktop `MemberCountType.EXPIRED`).
  final int expiredToday;
  final int checkInsToday;
  final int currentlyInside;
  final int revenueTodayDzd;
  final int newMembersToday;
  final int renewalsToday;
}
