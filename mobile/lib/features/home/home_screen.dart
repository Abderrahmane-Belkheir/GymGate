import 'package:flutter/material.dart';

import '../../core/formatting.dart';
import '../../data/models.dart';
import '../../data/repositories.dart';
import '../../data/repository_scope.dart';
import '../../i18n/app_strings.dart';
import '../../realtime/realtime_bus.dart';
import '../../theme/gym_tokens.dart';
import '../../widgets/gym_card.dart';
import '../../widgets/gym_empty_state.dart';
import '../../widgets/gym_header_card.dart';
import '../../widgets/gym_scaffold.dart';
import 'widgets/member_spotlight_card.dart';
import 'widgets/todays_statistics.dart';

/// The default screen. Composes the mobile-adapted Home: quick actions ->
/// member spotlight -> today's statistics. Data comes through [RepositoryScope]
/// so the mock layer can be swapped for a real one without touching this file.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen>
    with RealtimeReload<HomeScreen> {
  Future<_HomeData>? _future;
  final DateTime _now = DateTime.now();

  @override
  List<String> get realtimeTables => const ['members', 'attendance', 'payments'];

  @override
  void onRealtimeChange() {
    // Not forced: RealtimeService already keeps the member cache in step
    // (check-in decrement / new-member invalidation); the rest is fetched live.
    final future = _load(RepositoryScope.of(context));
    future.whenComplete(() {
      if (mounted) setState(() => _future = future);
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _future ??= _load(RepositoryScope.of(context));
    watchRealtime(context);
  }

  Future<_HomeData> _load(GymRepositories repos, {bool forceRefresh = false}) async {
    final results = await Future.wait([
      repos.dashboard.fetchSummary(forceRefresh: forceRefresh),
      repos.dashboard.fetchSpotlightMember(),
      repos.attendance.findAttendance(
        year: _now.year,
        month: _now.month,
        day: _now.day,
      ),
    ]);
    final summary = results[0] as DashboardSummary;
    final spotlight = results[1] as Member?;
    final todaysAttendance = results[2] as List<AttendanceEntry>;

    DateTime? checkInAt;
    if (spotlight != null) {
      for (final a in todaysAttendance) {
        if (a.memberId == spotlight.id) {
          checkInAt = a.checkIn;
          break;
        }
      }
    }
    return _HomeData(
      summary: summary,
      spotlight: spotlight,
      spotlightCheckIn: checkInAt,
    );
  }

  Future<void> _refresh() async {
    final future = _load(RepositoryScope.of(context), forceRefresh: true);
    setState(() => _future = future);
    await future;
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<_HomeData>(
      future: _future,
      builder: (context, snapshot) {
        final s = context.s;
        final header = GymHeaderCard(
          title: s.t('Overview'),
          subtitle: formatLongWeekday(_now),
        );

        if (snapshot.connectionState == ConnectionState.waiting &&
            !snapshot.hasData) {
          return GymScaffold(
            children: [
              header,
              const _LoadingCard(),
            ],
          );
        }

        if (!snapshot.hasData) {
          return GymScaffold(
            onRefresh: _refresh,
            children: [
              header,
              GymCard(
                child: GymEmptyState(
                  icon: Icons.cloud_off_outlined,
                  title: s.t('Could_not_load'),
                  message: s.t('Pull_to_try_again'),
                ),
              ),
            ],
          );
        }

        final data = snapshot.data!;
        return GymScaffold(
          onRefresh: _refresh,
          sectionGap: GymSpacing.lg,
          children: [
            header,
            MemberSpotlightCard(
              member: data.spotlight,
              checkInAt: data.spotlightCheckIn,
              now: _now,
            ),
            TodaysStatistics(summary: data.summary),
          ],
        );
      },
    );
  }
}

class _LoadingCard extends StatelessWidget {
  const _LoadingCard();

  @override
  Widget build(BuildContext context) {
    return const GymCard(
      padding: EdgeInsets.symmetric(vertical: 56),
      child: Center(
        child: SizedBox(
          width: 22,
          height: 22,
          child: CircularProgressIndicator(strokeWidth: 2.4),
        ),
      ),
    );
  }
}

class _HomeData {
  const _HomeData({
    required this.summary,
    required this.spotlight,
    required this.spotlightCheckIn,
  });

  final DashboardSummary summary;
  final Member? spotlight;
  final DateTime? spotlightCheckIn;
}
