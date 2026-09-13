import '../models.dart';
import '../repositories.dart';
import '../trend.dart';
import 'date_text_filter.dart';
import 'row_mappers.dart';
import 'supabase_session.dart';

/// Live [PaymentsRepository] backed by the Supabase `payments` table.
///
/// Table columns (Postgres):
///   id BIGINT, member_id BIGINT (FK -> members(id)), amount INT,
///   payment_date TEXT (ISO-8601 local date-time), plan_id BIGINT
///   (FK -> plans(id)), seance_id BIGINT (FK -> seance(id)), synced INT
///
/// A payment is for a membership *or* a single session: exactly one of
/// `plan_id` / `seance_id` is set — and a séance ("walk-in") payment has
/// `member_id = null` too. So `member` and `plan` are both LEFT joins, or those
/// rows would be dropped. There is no `payments → seance` foreign key, so
/// PostgREST can't embed the séance; [findPayments] fetches the referenced
/// `seance` rows in a second query and stitches them in.
///
/// Mirrors the desktop `PaymentDao`:
///  - [findPayments] ↔ `find(year, month, day)` — rows with the member name and
///    the plan name / séance rate joined, newest first.
///  - [sumAmount] ↔ `sumAmount(year, month, day)` — `COALESCE(SUM(amount), 0)`.
///    PostgREST aggregate functions are disabled on this project, so the sum is
///    done client-side over the filtered `amount` column (payment volume is a
///    day / month of one gym). Unaffected by the séance stitch — it counts every
///    payment row for the date, plan and séance alike.
class SupabasePaymentsRepository implements PaymentsRepository {
  const SupabasePaymentsRepository();

  static const String _columns =
      'id, member_id, amount, payment_date, plan_id, seance_id, '
      'member:members ( id, first_name, last_name ), '
      'plan:plans ( name )';

  @override
  Future<List<Payment>> fetchPayments() => findPayments();

  @override
  Future<List<Payment>> findPayments({int? year, int? month, int? day}) async {
    final pattern = isoDateLikePattern(year, month, day);
    final rows = await SupabaseSession.instance.read((client) {
      var query = client.from('payments').select(_columns);
      if (pattern != null) query = query.like('payment_date', pattern);
      return query.order('payment_date', ascending: false);
    });
    final maps = [
      for (final row in rows as List) Map<String, dynamic>.from(row as Map),
    ];
    final seancesById = await _seancesByIdFor(maps);
    return [
      for (final row in maps) paymentFromRow(row, seancesById: seancesById),
    ];
  }

  /// Fetches the `seance` rows referenced by any `seance_id` in [rows] (one
  /// round trip, or none when no payment is a séance) → `id → Seance`.
  Future<Map<String, Seance>> _seancesByIdFor(
    List<Map<String, dynamic>> rows,
  ) async {
    final ids = <String>{
      for (final row in rows)
        if (row['seance_id'] != null) row['seance_id'].toString(),
    };
    if (ids.isEmpty) return const {};
    final seanceRows = await SupabaseSession.instance.read(
      (client) => client
          .from('seance')
          .select('id, price, sexe, cardio')
          .inFilter('id', ids.toList()),
    );
    return {
      for (final row in seanceRows as List)
        (row as Map)['id'].toString():
            seanceFromRow(Map<String, dynamic>.from(row)),
    };
  }

  @override
  Future<int> sumAmount({int? year, int? month, int? day}) async {
    final pattern = isoDateLikePattern(year, month, day);
    final rows = await SupabaseSession.instance.read((client) {
      var query = client.from('payments').select('amount');
      if (pattern != null) query = query.like('payment_date', pattern);
      return query;
    });
    var total = 0;
    for (final row in rows as List) {
      total += ((row as Map)['amount'] as num?)?.round() ?? 0;
    }
    return total;
  }

  @override
  Future<TrendSeries> revenueTrend({
    required TrendMode mode,
    required int year,
    required int month,
  }) async {
    // One slim query — just the date + amount, no member / plan joins.
    final pattern = switch (mode) {
      TrendMode.daily => isoDateLikePattern(year, month, null),
      TrendMode.monthly => isoDateLikePattern(year, null, null),
      TrendMode.allTime => null,
    };
    final rows = await SupabaseSession.instance.read((client) {
      var query = client.from('payments').select('payment_date, amount');
      if (pattern != null) query = query.like('payment_date', pattern);
      return query;
    });
    final events = <TrendEvent>[];
    for (final row in rows as List) {
      final map = Map<String, dynamic>.from(row as Map);
      final when = dateFromText(map['payment_date']);
      if (when == null) continue;
      events.add((when: when, weight: (map['amount'] as num?)?.round() ?? 0));
    }
    return bucketTrend(mode, events, year: year, month: month);
  }
}
