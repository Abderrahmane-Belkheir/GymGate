import 'package:flutter/material.dart';

import '../../data/models.dart';
import '../../data/repository_scope.dart';
import '../../i18n/app_strings.dart';
import '../../realtime/realtime_bus.dart';
import '../../theme/gym_colors.dart';
import '../../theme/gym_tokens.dart';
import '../../widgets/gym_card.dart';
import '../../widgets/gym_empty_state.dart';
import '../../widgets/gym_header_card.dart';
import '../../widgets/gym_pill_button.dart';
import '../../widgets/gym_scaffold.dart';
import '../../widgets/gym_segmented_toggle.dart';
import 'widgets/plan_card.dart';
import 'widgets/plan_editor_sheet.dart';
import 'widgets/seance_card.dart';
import 'widgets/seance_price_editor_sheet.dart';

/// Membership plans — the desktop `Plans` screen adapted to mobile: the
/// accent-bar header, the gender filter, a single column of plan cards, and the
/// single-session ("séance") prices below. Read-only: no "New Plan", no edit /
/// delete.
class PlansScreen extends StatefulWidget {
  const PlansScreen({super.key});

  @override
  State<PlansScreen> createState() => _PlansScreenState();
}

class _PlansScreenState extends State<PlansScreen>
    with RealtimeReload<PlansScreen> {
  Gender _gender = Gender.male;
  Future<List<Plan>>? _future;
  Future<List<Seance>>? _seanceFuture;

  @override
  List<String> get realtimeTables => const ['plans', 'seance'];

  @override
  void onRealtimeChange() {
    final repos = RepositoryScope.of(context);
    final future = repos.plans.fetchPlans();
    final seanceFuture = repos.seance.fetchSeances();
    Future.wait<void>([future, seanceFuture]).whenComplete(() {
      if (mounted) {
        setState(() {
          _future = future;
          _seanceFuture = seanceFuture;
        });
      }
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final repos = RepositoryScope.of(context);
    _future ??= repos.plans.fetchPlans();
    _seanceFuture ??= repos.seance.fetchSeances();
    watchRealtime(context);
  }

  Future<void> _refresh() async {
    final repos = RepositoryScope.of(context);
    final future = repos.plans.fetchPlans();
    final seanceFuture = repos.seance.fetchSeances();
    setState(() {
      _future = future;
      _seanceFuture = seanceFuture;
    });
    await Future.wait<void>([future, seanceFuture]);
  }

  Future<void> _openNewPlan() => _openEditor(null);

  Future<void> _openEditPlan(Plan plan) => _openEditor(plan);

  Future<void> _openEditor(Plan? plan) async {
    final saved = await showPlanEditor(context, plan: plan);
    if (saved != true || !mounted) return;
    await _refresh();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          context.s.t(plan == null ? 'Plan_created' : 'Plan_updated'),
        ),
      ),
    );
  }

  Future<void> _openEditSeance(Seance seance) async {
    final saved = await showSeancePriceEditor(context, seance: seance);
    if (saved != true || !mounted) return;
    await _refresh();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(context.s.t('Session_price_updated'))),
    );
  }

  bool _forGender(Gender? restrictedTo) =>
      restrictedTo == null || restrictedTo == _gender;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<Plan>>(
      future: _future,
      builder: (context, snapshot) {
        final s = context.s;
        final all = snapshot.data ?? const <Plan>[];
        final visible = all.where((p) => _forGender(p.restrictedTo)).toList();
        final loading = snapshot.connectionState == ConnectionState.waiting &&
            !snapshot.hasData;

        return GymScaffold(
          onRefresh: _refresh,
          children: [
            GymHeaderCard(
              title: s.t('Membership_Plans'),
              subtitle: s.t('Create,_edit_and_manage_gym_membership_plans'),
              trailing: GymPillButton(
                label: s.t('New_Plan'),
                icon: Icons.add,
                variant: GymButtonVariant.primary,
                onPressed: _openNewPlan,
              ),
            ),
            GymCard(
              emphasis: GymCardEmphasis.header,
              padding: const EdgeInsets.symmetric(
                horizontal: GymSpacing.md,
                vertical: GymSpacing.md,
              ),
              child: Align(
                alignment: Alignment.centerLeft,
                child: GymSegmentedToggle<Gender>(
                  value: _gender,
                  onChanged: (g) => setState(() => _gender = g),
                  segments: [
                    GymSegment(
                      value: Gender.male,
                      label: s.t('Male'),
                      style: GymSegmentStyle.solidBlue,
                    ),
                    GymSegment(
                      value: Gender.female,
                      label: s.t('Female'),
                      style: GymSegmentStyle.solidPink,
                    ),
                  ],
                ),
              ),
            ),
            if (loading)
              const GymCard(
                padding: EdgeInsets.symmetric(vertical: 56),
                child: Center(
                  child: SizedBox(
                    width: 22,
                    height: 22,
                    child: CircularProgressIndicator(strokeWidth: 2.4),
                  ),
                ),
              )
            else if (snapshot.hasError)
              GymCard(
                child: GymEmptyState(
                  icon: Icons.cloud_off_outlined,
                  title: s.t('Could_not_load'),
                  message: s.t('Pull_to_retry'),
                ),
              )
            else if (visible.isEmpty)
              GymCard(
                child: GymEmptyState(
                  icon: Icons.card_membership_outlined,
                  message: s.t('No_plans_for_selection'),
                ),
              )
            else
              for (final plan in visible)
                PlanCard(plan: plan, onEdit: () => _openEditPlan(plan)),
            if (!loading && !snapshot.hasError)
              _SeanceSection(
                future: _seanceFuture,
                forGender: _forGender,
                onEdit: _openEditSeance,
              ),
          ],
        );
      },
    );
  }
}

/// The "الحصة / La séance" block below the plan cards: a section heading, then a
/// card per single-session price for the selected gender.
class _SeanceSection extends StatelessWidget {
  const _SeanceSection({
    required this.future,
    required this.forGender,
    required this.onEdit,
  });

  final Future<List<Seance>>? future;
  final bool Function(Gender? restrictedTo) forGender;
  final ValueChanged<Seance> onEdit;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = context.s;

    return FutureBuilder<List<Seance>>(
      future: future,
      builder: (context, snapshot) {
        // Stay silent until there is a result — the section is secondary to the
        // plan cards and shouldn't flash its own spinner or error state.
        if (!snapshot.hasData || snapshot.hasError) {
          return const SizedBox.shrink();
        }
        final visible = (snapshot.data ?? const <Seance>[])
            .where((x) => forGender(x.restrictedTo))
            .toList();

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(
                GymSpacing.xs,
                GymSpacing.sm,
                GymSpacing.xs,
                GymSpacing.sm,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    s.t('The_session'),
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    s.t('Single_session_price'),
                    style: theme.textTheme.bodySmall?.copyWith(
                      fontSize: 12,
                      color: GymPalette.textMuted,
                    ),
                  ),
                ],
              ),
            ),
            if (visible.isEmpty)
              GymCard(
                child: GymEmptyState(
                  icon: Icons.sell_outlined,
                  message: s.t('No_session_prices'),
                ),
              )
            else
              for (var i = 0; i < visible.length; i++) ...[
                if (i > 0) const SizedBox(height: GymSpacing.sectionGap),
                SeanceCard(
                  seance: visible[i],
                  onEdit: () => onEdit(visible[i]),
                ),
              ],
          ],
        );
      },
    );
  }
}
