import 'package:supabase_flutter/supabase_flutter.dart';

/// Next free integer id for [table] — `max(id) + 1`.
///
/// `plans`, `payments` (and others synced from the desktop) have an `id` column
/// with **no DB default**: the desktop assigns ids, so a mobile insert must
/// too. PostgREST aggregate functions are disabled on this project, so we read
/// the top row rather than `max(id)`.
Future<int> nextId(SupabaseClient client, String table) async {
  final rows = await client
      .from(table)
      .select('id')
      .order('id', ascending: false)
      .limit(1);
  final list = rows as List;
  if (list.isEmpty) return 1;
  return ((list.first as Map)['id'] as num).toInt() + 1;
}
