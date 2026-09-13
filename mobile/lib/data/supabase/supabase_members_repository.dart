import '../../core/formatting.dart';
import '../../core/self_write_registry.dart';
import '../models.dart';
import '../repositories.dart';
import 'next_id.dart';
import 'row_mappers.dart';
import 'supabase_session.dart';

/// Live [MembersRepository] backed by the Supabase `members` table.
///
/// Table columns (Postgres):
///   id BIGINT, first_name TEXT, last_name TEXT, phone_number TEXT,
///   sexe TEXT ('MALE' | 'FEMALE'), plan_id BIGINT NULL, plan_name TEXT NULL,
///   start_date TEXT NULL, end_date TEXT NULL, remaining_days INT NULL,
///   created_at TEXT, synced INT,  FK plan_id -> plans(id)
///
/// The plan name/details are read via a join on `plans` (not the denormalised
/// `plan_name` column). The profile photo is LEFT-joined from `member_photos`
/// (`member_photos.id` == `members.id`, one-to-one) — a small JPEG per member.
/// Fetches every row; the Members screen filters by `sexe` and validity.
class SupabaseMembersRepository implements MembersRepository {
  const SupabaseMembersRepository();

  /// Embeds the related plan row and the (optional) `member_photos` row so
  /// `memberFromRow` can build `Member.plan` and `Member.photoBytes`.
  static const String _columns =
      'id, first_name, last_name, phone_number, sexe, plan_id, plan_name, '
      'start_date, end_date, remaining_days, created_at, '
      'plan:plans ( id, name, duration_months, days_per_month, price, cardio, sexe ), '
      'photo:member_photos ( image )';

  @override
  Future<List<Member>> fetchMembers({bool forceRefresh = false}) async {
    // Always hits the network; caching (and [forceRefresh]) is the wrapper's job.
    final rows = await SupabaseSession.instance.read(
      (client) => client.from('members').select(_columns).order('id'),
    );
    return [
      for (final row in rows as List)
        memberFromRow(Map<String, dynamic>.from(row as Map)),
    ];
  }

  @override
  Future<Member?> fetchMember(String id) async {
    final row = await SupabaseSession.instance.read(
      (client) =>
          client.from('members').select(_columns).eq('id', id).maybeSingle(),
    );
    return row == null ? null : memberFromRow(Map<String, dynamic>.from(row));
  }

  @override
  Future<void> cancelMembership(String memberId) async {
    await SupabaseSession.instance.write(
      (client) => client.from('members').update({
        'plan_id': null,
        'start_date': null,
        'end_date': null,
        'remaining_days': null,
        // Mobile touched the row — the desktop re-pulls it.
        'synced': 0,
      }).eq('id', memberId),
    );
  }

  @override
  Future<void> renewMembership({
    required String memberId,
    required Plan plan,
  }) async {
    final now = DateTime.now();
    final start = DateTime(now.year, now.month, now.day);
    final end = _addMonths(start, plan.durationMonths);
    final remaining =
        plan.isUnlimited ? null : plan.visitsPerMonth * plan.durationMonths;
    final planId = int.tryParse(plan.id) ?? plan.id;
    final mId = int.tryParse(memberId) ?? memberId;

    await SupabaseSession.instance.write((client) async {
      await client.from('members').update({
        'plan_id': planId,
        'start_date': formatIsoDate(start), // members dates are YYYY-MM-DD
        'end_date': formatIsoDate(end),
        'remaining_days': remaining,
        'synced': 0,
      }).eq('id', memberId);

      // `payments.id` has no DB default; `synced` / `user_id` do.
      final paymentId = await nextId(client, 'payments');
      // Suppress the realtime echo of this payment (renewal isn't "news" to
      // the person who just did it).
      SelfWriteRegistry.instance.mark('payments', paymentId);
      await client.from('payments').insert({
        'id': paymentId,
        'member_id': mId,
        'plan_id': planId,
        'amount': plan.priceDzd,
        'payment_date': now.toIso8601String(),
      });
    });
  }

  /// [start] + [months] calendar months, clamping the day to the target month
  /// (Jan 31 + 1 month -> Feb 28) like Java's `LocalDate.plusMonths`.
  static DateTime _addMonths(DateTime start, int months) {
    final total = start.month - 1 + months;
    final year = start.year + total ~/ 12;
    final month = total % 12 + 1;
    final lastDay = DateTime(year, month + 1, 0).day;
    return DateTime(year, month, start.day > lastDay ? lastDay : start.day);
  }

  @override
  Future<void> applyCheckIn(String memberId) async {} // caching is the wrapper's job

  @override
  Future<void> applyMemberUpsert(String id) async {} // caching is the wrapper's job

  @override
  Future<void> applyMemberRemoval(String id) async {} // caching is the wrapper's job
}
