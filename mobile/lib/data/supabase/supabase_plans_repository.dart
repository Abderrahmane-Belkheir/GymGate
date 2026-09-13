import 'package:supabase_flutter/supabase_flutter.dart';

import '../../core/self_write_registry.dart';
import '../models.dart';
import '../repositories.dart';
import 'next_id.dart';
import 'row_mappers.dart';
import 'supabase_session.dart';

/// Live [PlansRepository] backed by the Supabase `plans` table.
///
/// Fetches every row, then the Plans screen filters by the selected `sexe`.
class SupabasePlansRepository implements PlansRepository {
  const SupabasePlansRepository();

  @override
  Future<List<Plan>> fetchPlans() async {
    final rows = await SupabaseSession.instance.read(
      (client) => client.from('plans').select().order('id'),
    );
    return [
      for (final row in rows as List)
        planFromRow(Map<String, dynamic>.from(row as Map)),
    ];
  }

  @override
  Future<Plan> createPlan({
    required String name,
    required int priceDzd,
    required int durationMonths,
    required int? daysPerMonth,
    required bool cardioIncluded,
    required Gender gender,
  }) {
    // `synced` is left to its DB default; `user_id` is filled from the JWT by a
    // column default. `plans.id` has NO default (the desktop assigns ids), so
    // we take `max(id) + 1` — and retry if a concurrent insert takes it first.
    return SupabaseSession.instance.write((client) async {
      for (var attempt = 0;; attempt++) {
        final id = await nextId(client, 'plans');
        try {
          // Suppress our own realtime echo before it can arrive.
          SelfWriteRegistry.instance.mark('plans', id);
          final row = await client.from('plans').insert({
            'id': id,
            'name': name,
            'price': priceDzd,
            'duration_months': durationMonths,
            'days_per_month': daysPerMonth,
            'cardio': cardioIncluded ? 1 : 0,
            'sexe': gender == Gender.female ? 'FEMALE' : 'MALE',
          }).select().single();
          return planFromRow(Map<String, dynamic>.from(row));
        } on PostgrestException catch (e) {
          if (e.code == '23505' && attempt < 3) continue; // id taken — bump it
          rethrow;
        }
      }
    });
  }

  @override
  Future<Plan> updatePlan({
    required String id,
    required String name,
    required int priceDzd,
    required int durationMonths,
    required int? daysPerMonth,
    required bool cardioIncluded,
  }) {
    return SupabaseSession.instance.write((client) async {
      SelfWriteRegistry.instance.mark('plans', int.tryParse(id) ?? id);
      final row = await client
          .from('plans')
          .update({
            'name': name,
            'price': priceDzd,
            'duration_months': durationMonths,
            'days_per_month': daysPerMonth,
            'cardio': cardioIncluded ? 1 : 0,
            // Mobile touched the row — mark it for the desktop to re-pull.
            'synced': 0,
          })
          .eq('id', id)
          .select()
          .single();
      return planFromRow(Map<String, dynamic>.from(row));
    });
  }
}
