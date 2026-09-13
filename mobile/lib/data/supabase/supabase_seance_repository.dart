import '../../core/self_write_registry.dart';
import '../models.dart';
import '../repositories.dart';
import 'row_mappers.dart';
import 'supabase_session.dart';

/// Live [SeanceRepository] backed by the Supabase `seance` table.
///
/// Fetches every row, then the Plans screen filters by the selected `sexe`.
class SupabaseSeanceRepository implements SeanceRepository {
  const SupabaseSeanceRepository();

  @override
  Future<List<Seance>> fetchSeances() async {
    final rows = await SupabaseSession.instance.read(
      (client) => client.from('seance').select().order('id'),
    );
    return [
      for (final row in rows as List)
        seanceFromRow(Map<String, dynamic>.from(row as Map)),
    ];
  }

  @override
  Future<Seance> updatePrice({required String id, required int priceDzd}) {
    return SupabaseSession.instance.write((client) async {
      // Suppress our own realtime echo before it can arrive — this is our own
      // change, not "news" worth a banner.
      SelfWriteRegistry.instance.mark('seance', int.tryParse(id) ?? id);
      final row = await client
          .from('seance')
          .update({
            'price': priceDzd,
            // Mobile touched the row — the desktop re-pulls it.
            'synced': 0,
          })
          .eq('id', id)
          .select()
          .single();
      return seanceFromRow(Map<String, dynamic>.from(row));
    });
  }
}
