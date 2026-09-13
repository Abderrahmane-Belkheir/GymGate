import '../caching_members_repository.dart';
import '../member_index.dart';
import '../repositories.dart';
import 'supabase_attendance_repository.dart';
import 'supabase_dashboard_repository.dart';
import 'supabase_members_repository.dart';
import 'supabase_payments_repository.dart';
import 'supabase_plans_repository.dart';
import 'supabase_seance_repository.dart';

/// Repository bundle: every source is now live on Supabase. The Home dashboard
/// is aggregated from members + attendance + payments (check-ins, new members
/// today, renewed subs today, expiring today, revenue today).
///
/// Supabase must already be initialized (see `main.dart`). If it isn't, the
/// live fetches throw and those screens show their error state.
GymRepositories buildSupabaseRepositories({DateTime? reference}) {
  // One shared cache: the Members screen and the dashboard read the same list,
  // and a forced refresh on either repopulates it (and the member index) for the
  // other.
  final memberIndex = MemberIndex();
  final members = CachingMembersRepository(
    const SupabaseMembersRepository(),
    memberIndex: memberIndex,
  );
  const attendance = SupabaseAttendanceRepository();
  const payments = SupabasePaymentsRepository();

  return GymRepositories(
    members: members,
    plans: const SupabasePlansRepository(),
    seance: const SupabaseSeanceRepository(),
    payments: payments,
    attendance: attendance,
    dashboard: SupabaseDashboardRepository(
      members: members,
      attendance: attendance,
      payments: payments,
      reference: reference,
    ),
    memberIndex: memberIndex,
  );
}
