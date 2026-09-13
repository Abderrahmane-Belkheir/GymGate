import 'package:flutter/material.dart';

import '../../data/models.dart';
import '../../data/repository_scope.dart';
import '../../i18n/app_strings.dart';
import '../../realtime/realtime_bus.dart';
import '../../theme/gym_colors.dart';
import '../../theme/gym_tokens.dart';
import '../../widgets/gym_badges.dart';
import '../../widgets/gym_card.dart';
import '../../widgets/gym_empty_state.dart';
import '../../widgets/gym_header_card.dart';
import '../../widgets/gym_pill_button.dart';
import '../../widgets/gym_scaffold.dart';
import '../../widgets/gym_segmented_toggle.dart';
import 'widgets/member_list_row.dart';

/// Members directory — the desktop `Members` screen adapted to mobile: the
/// accent-bar header + count badge, gender / status filters, a search toolbar,
/// and the framed member list. Mock data only; the toggles, search and refresh
/// operate on the in-memory list.
class MembersScreen extends StatefulWidget {
  const MembersScreen({super.key});

  @override
  State<MembersScreen> createState() => _MembersScreenState();
}

class _MembersScreenState extends State<MembersScreen>
    with RealtimeReload<MembersScreen> {
  final DateTime _now = DateTime.now();
  final TextEditingController _firstCtrl = TextEditingController();
  final TextEditingController _lastCtrl = TextEditingController();

  Gender _gender = Gender.male;
  bool _activeSide = true;
  String _firstQuery = '';
  String _lastQuery = '';

  Future<List<Member>>? _future;

  @override
  // 'attendance' too: a check-in decrements a member's remaining_days.
  List<String> get realtimeTables => const ['members', 'attendance'];

  @override
  void onRealtimeChange() {
    // Not forced — RealtimeService already invalidated / adjusted the cache.
    final future = RepositoryScope.of(context).members.fetchMembers();
    future.whenComplete(() {
      if (mounted) setState(() => _future = future);
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _future ??= RepositoryScope.of(context).members.fetchMembers();
    watchRealtime(context);
  }

  @override
  void dispose() {
    _firstCtrl.dispose();
    _lastCtrl.dispose();
    super.dispose();
  }

  List<Member> _filter(List<Member> all) {
    return all.where((m) {
      if (m.gender != _gender) return false;
      if (m.isValidAt(_now) != _activeSide) return false;
      if (_firstQuery.isNotEmpty &&
          !m.firstName.toLowerCase().contains(_firstQuery.toLowerCase())) {
        return false;
      }
      if (_lastQuery.isNotEmpty &&
          !m.lastName.toLowerCase().contains(_lastQuery.toLowerCase())) {
        return false;
      }
      return true;
    }).toList();
  }

  void _confirm() {
    FocusScope.of(context).unfocus();
    setState(() {
      _firstQuery = _firstCtrl.text.trim();
      _lastQuery = _lastCtrl.text.trim();
    });
  }

  void _cancel() {
    FocusScope.of(context).unfocus();
    _firstCtrl.clear();
    _lastCtrl.clear();
    setState(() {
      _firstQuery = '';
      _lastQuery = '';
      _gender = Gender.male;
      _activeSide = true;
    });
  }

  /// Re-read the (already-updated) cache after a row-level change — e.g. a
  /// cancelled membership — so the Active / Inactive filter re-runs at once.
  void _reloadFromCache() {
    if (!mounted) return;
    setState(() {
      _future = RepositoryScope.of(context).members.fetchMembers();
    });
  }

  Future<void> _refresh() async {
    // Forced: refetch from Supabase and repopulate the shared cache so the Home
    // stat cards pick up the same data next time they read it.
    final future =
        RepositoryScope.of(context).members.fetchMembers(forceRefresh: true);
    setState(() => _future = future);
    await future;
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<Member>>(
      future: _future,
      builder: (context, snapshot) {
        final s = context.s;
        final all = snapshot.data ?? const <Member>[];
        final visible = _filter(all);
        final loading = snapshot.connectionState == ConnectionState.waiting &&
            !snapshot.hasData;

        return GymScaffold(
          onRefresh: _refresh,
          children: [
            GymHeaderCard(
              title: s.t('Members'),
              subtitle: s.t('View_and_manage_all_gym_members'),
              trailing: GymCountBadge('${visible.length} ${s.t('Members')}'),
            ),
            _FiltersCard(
              gender: _gender,
              activeSide: _activeSide,
              onGender: (g) => setState(() => _gender = g),
              onStatus: (a) => setState(() => _activeSide = a),
            ),
            _ToolbarCard(
              firstCtrl: _firstCtrl,
              lastCtrl: _lastCtrl,
              onConfirm: _confirm,
              onCancel: _cancel,
              onRefresh: _refresh,
            ),
            _MemberTable(
              members: visible,
              now: _now,
              loading: loading,
              hasError: snapshot.hasError,
              onMemberChanged: _reloadFromCache,
            ),
          ],
        );
      },
    );
  }
}

class _FiltersCard extends StatelessWidget {
  const _FiltersCard({
    required this.gender,
    required this.activeSide,
    required this.onGender,
    required this.onStatus,
  });

  final Gender gender;
  final bool activeSide;
  final ValueChanged<Gender> onGender;
  final ValueChanged<bool> onStatus;

  @override
  Widget build(BuildContext context) {
    final s = context.s;
    return GymCard(
      emphasis: GymCardEmphasis.header,
      padding: const EdgeInsets.symmetric(
          horizontal: GymSpacing.md, vertical: GymSpacing.md),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            GymSegmentedToggle<Gender>(
              value: gender,
              onChanged: onGender,
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
            const GymToggleDivider(),
            GymSegmentedToggle<bool>(
              value: activeSide,
              onChanged: onStatus,
              segments: [
                GymSegment(
                  value: true,
                  label: s.t('Active'),
                  style: GymSegmentStyle.softGreen,
                ),
                GymSegment(
                  value: false,
                  label: s.t('Inactive'),
                  style: GymSegmentStyle.softRed,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ToolbarCard extends StatelessWidget {
  const _ToolbarCard({
    required this.firstCtrl,
    required this.lastCtrl,
    required this.onConfirm,
    required this.onCancel,
    required this.onRefresh,
  });

  final TextEditingController firstCtrl;
  final TextEditingController lastCtrl;
  final VoidCallback onConfirm;
  final VoidCallback onCancel;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    final s = context.s;
    return GymCard(
      emphasis: GymCardEmphasis.header,
      child: Column(
        children: [
          TextField(
            controller: firstCtrl,
            textInputAction: TextInputAction.next,
            decoration: InputDecoration(hintText: s.t('First_Name')),
          ),
          const SizedBox(height: GymSpacing.sm),
          TextField(
            controller: lastCtrl,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => onConfirm(),
            decoration: InputDecoration(hintText: s.t('Last_Name')),
          ),
          const SizedBox(height: GymSpacing.md),
          GymPillButton(
            label: s.t('Confirm'),
            icon: Icons.search,
            variant: GymButtonVariant.primary,
            expand: true,
            onPressed: onConfirm,
          ),
          const SizedBox(height: GymSpacing.sm),
          Row(
            children: [
              Expanded(
                child: GymPillButton(
                  label: s.t('Cancel'),
                  icon: Icons.close,
                  expand: true,
                  onPressed: onCancel,
                ),
              ),
              const SizedBox(width: GymSpacing.sm),
              Expanded(
                child: GymPillButton(
                  label: s.t('Refresh'),
                  icon: Icons.refresh,
                  expand: true,
                  onPressed: () => onRefresh(),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _MemberTable extends StatelessWidget {
  const _MemberTable({
    required this.members,
    required this.now,
    required this.loading,
    required this.hasError,
    this.onMemberChanged,
  });

  final List<Member> members;
  final DateTime now;
  final bool loading;
  final bool hasError;
  final VoidCallback? onMemberChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = context.s;

    Widget body;
    if (loading) {
      body = const Padding(
        padding: EdgeInsets.symmetric(vertical: 56),
        child: Center(
          child: SizedBox(
            width: 22,
            height: 22,
            child: CircularProgressIndicator(strokeWidth: 2.4),
          ),
        ),
      );
    } else if (hasError) {
      body = GymEmptyState(
        icon: Icons.cloud_off_outlined,
        title: s.t('Could_not_load'),
        message: s.t('Pull_to_retry'),
      );
    } else if (members.isEmpty) {
      body = GymEmptyState(
        icon: Icons.search_off_outlined,
        message: s.t('No_members_match_filters'),
      );
    } else {
      body = Column(
        children: [
          for (var i = 0; i < members.length; i++) ...[
            MemberListRow(
              member: members[i],
              now: now,
              onChanged: onMemberChanged,
            ),
            if (i != members.length - 1) const Divider(height: 1),
          ],
        ],
      );
    }

    return GymCard(
      padding: EdgeInsets.zero,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(GymRadii.card),
        child: Column(
          children: [
            Container(
              height: 30,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              decoration: const BoxDecoration(
                color: GymPalette.tableHeaderBg,
                border: Border(
                  bottom: BorderSide(color: GymPalette.hairlineStrong),
                ),
              ),
              child: Row(
                children: [
                  Text(s.t('Member'), style: theme.textTheme.labelSmall),
                  const Spacer(),
                  Text(s.t('Membership'), style: theme.textTheme.labelSmall),
                ],
              ),
            ),
            body,
          ],
        ),
      ),
    );
  }
}
