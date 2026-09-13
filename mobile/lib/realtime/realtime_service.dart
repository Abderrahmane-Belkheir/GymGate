import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../core/self_write_registry.dart';
import '../data/member_index.dart';
import '../data/repositories.dart';
import '../data/supabase/row_mappers.dart' show boolFromInt, genderFromSexe;
import '../data/supabase/supabase_session.dart';
import '../notifications/gym_notification.dart';
import '../notifications/notification_center.dart';
import 'realtime_bus.dart';

/// Subscribes to Supabase Realtime for `attendance`, `members`, `payments`,
/// `plans` and `seance`. A *relevant* change updates the shared member cache as
/// needed, bumps [bus] (so live screens re-query with no user action), and —
/// for a subset — raises a [GymNotification] (the member's name/photo come
/// from [memberIndex]).
///
/// **Account scoping — two gates.** Every subscription carries a server-side
/// `user_id=eq.<current user>` filter, so Realtime only sends this account's
/// rows (RLS already does this for INSERT/UPDATE, but **not for DELETE** — the
/// filter is what scopes deletes, and it needs `REPLICA IDENTITY FULL`). On top
/// of that, [_onChange] re-checks `row['user_id']` against the current user (see
/// [belongsToUser]) — covering the window before sign-in completes and any row
/// that somehow arrives without a `user_id`.
///
/// **Live-refresh** (the [bus] bump — the list screens re-read with no refresh):
/// `attendance` / `members` / `payments` / `plans` react to **every event** —
/// insert, update and delete — so a screen never needs a manual
/// pull-to-refresh. `seance` reacts to **update only**: the only thing that
/// ever changes on a séance rate is its price (created/removed server-side,
/// not from this app), so insert/delete are ignored there. A `members` change
/// is spliced into the cache surgically ([MembersRepository.applyMemberUpsert]
/// / `applyMemberRemoval`), so an insert/update/delete on Members shows up on
/// the Members screen *and* the Home "new members" stat immediately. An
/// `attendance` **insert** also **decrements that member's `remaining_days`**
/// in the cache (optimistic / display-only) so every screen reflects it live.
/// The Plans screen watches `seance` too, so an update refetches and shows the
/// new price immediately, same as a `plans` change.
///
/// **Notifications** (the banner + bell) are raised only for: `attendance`
/// insert, `payments` insert, `members` insert, `plans` insert + update, and
/// `seance` update (its only relevant event). Every DELETE (`members` and
/// `plans` alike) — and a `members` *update* — still updates the shared cache
/// and bumps [bus] so the lists stay live, but raises no banner (deletes
/// aren't reliably nameable — see `supabase-auth-jwt` memory on
/// `REPLICA IDENTITY` — and a `members` update fires on every check-in's
/// `remaining_days` tick, which would be noise). A change **this device just
/// made** (see [SelfWriteRegistry]) also refreshes the screens but raises no
/// banner.
///
/// **A single edit still produces two UPDATE echoes.** Writing `synced: 0`
/// (any repository's convention for "desktop hasn't seen this yet") is one
/// realtime UPDATE; the desktop processing it and writing `synced: 1` back is
/// a *second*, independent one on the same row. [SelfWriteRegistry] only
/// swallows the first (the device's own write) — the second still arrives and,
/// for `plans`/`seance`, would otherwise banner a change nobody actually made.
/// [onlyBookkeepingChanged] catches that case directly, by diffing the
/// UPDATE's old vs. new row: if nothing but `synced` differs, it's a sync ack,
/// not an edit, no matter which device sent it or how many arrive.
///
/// [start] first awaits [SupabaseSession] so the user's JWT is on the realtime
/// socket before any channel joins. supabase_flutter keeps the socket's token in
/// step afterwards (token refresh / re-auth raise auth events that call
/// `realtime.setAuth`).
///
/// Server side, each table must be in the `supabase_realtime` publication and
/// set to `REPLICA IDENTITY FULL` (for the DELETE old-row `user_id` + the
/// UPDATE old-row payload) — see `supabase/realtime_setup.sql`.
class RealtimeService {
  RealtimeService({
    required this.bus,
    required this.notifications,
    required this.memberIndex,
    required this.members,
  });

  final RealtimeBus bus;
  final NotificationCenter notifications;
  final MemberIndex memberIndex;
  final MembersRepository members;

  final List<RealtimeChannel> _channels = <RealtimeChannel>[];
  bool _started = false;

  /// De-dupes concurrent [start] calls (e.g. the app-launch call racing an
  /// [ensureConnected] triggered by a very-early connectivity event).
  Future<void>? _starting;

  static const List<String> tables = [
    'attendance',
    'members',
    'payments',
    'plans',
    'seance',
  ];

  Future<void> start() => _starting ??= _doStart().whenComplete(() {
        _starting = null;
      });

  /// Restarts the whole realtime layer if the socket isn't actually open.
  ///
  /// A dead connection doesn't always self-report: mobile OSes can silently
  /// kill a backgrounded app's socket without ever delivering a close/error
  /// event, so [RealtimeClient] keeps believing it's still connected and never
  /// runs its own reconnect logic — no more events arrive, but nothing looks
  /// wrong from inside the app. Call this after regaining connectivity or
  /// resuming from the background to catch and fix exactly that.
  Future<void> ensureConnected() async {
    // If a start() is already in flight, let it finish before judging health.
    if (_starting != null) await _starting;

    final SupabaseClient client;
    try {
      client = Supabase.instance.client;
    } catch (e) {
      debugPrint('RealtimeService: Supabase not initialised — $e');
      return;
    }
    if (client.realtime.isConnected) return;

    debugPrint(
      'RealtimeService: socket not connected '
      '(${client.realtime.connectionState}) — restarting',
    );
    dispose();
    await start();
  }

  Future<void> _doStart() async {
    if (_started) return;
    _started = true;
    final SupabaseClient client;
    try {
      client = Supabase.instance.client;
    } catch (e) {
      debugPrint('RealtimeService: Supabase not initialised — $e');
      _started = false;
      return;
    }

    // Make sure a user JWT is on the socket *before* the channels join, so the
    // very first `phx_join` carries `access_token` and the change stream is
    // filtered by RLS from the start (not briefly with the anon key).
    // `client()` signs in with email/password if there is no valid session yet.
    try {
      await SupabaseSession.instance.client();
      final token = SupabaseSession.instance.accessToken;
      if (token != null) {
        await client.realtime.setAuth(token);
      } else {
        debugPrint(
          'RealtimeService: no JWT yet — channels will join with the anon key '
          'and upgrade once sign-in succeeds',
        );
      }
    } catch (e) {
      debugPrint('RealtimeService: could not attach JWT before subscribe — $e');
    }

    // Only receive this account's rows. Covers DELETE too (RLS doesn't), given
    // `REPLICA IDENTITY FULL`. If we somehow have no user id yet, subscribe
    // unfiltered — `_onChange`'s [belongsToUser] check is then the only gate.
    final uid = SupabaseSession.instance.currentUserId;
    final filter = uid == null
        ? null
        : PostgresChangeFilter(
            type: PostgresChangeFilterType.eq,
            column: 'user_id',
            value: uid,
          );

    for (final table in tables) {
      _channels.add(
        client
            .channel('public:$table')
            .onPostgresChanges(
              event: PostgresChangeEvent.all,
              schema: 'public',
              table: table,
              filter: filter,
              callback: (payload) => _onChange(table, payload),
            )
            .subscribe(),
      );
    }
  }

  void dispose() {
    if (!_started) return;
    try {
      final client = Supabase.instance.client;
      for (final channel in _channels) {
        client.removeChannel(channel);
      }
    } catch (_) {}
    _channels.clear();
    _started = false;
  }

  Future<void> _onChange(String table, PostgresChangePayload payload) async {
    final event = payload.eventType;
    if (!_isRelevant(table, event)) return;

    final isDelete = event == PostgresChangeEvent.delete;
    // A DELETE carries only the old row; INSERT/UPDATE the new one.
    final row = isDelete ? payload.oldRecord : payload.newRecord;

    // Every event — insert / update / delete — must be for the signed-in
    // account. The server-side `user_id` filter already enforces this; this
    // second check covers the pre-sign-in window and any row with no `user_id`
    // (e.g. a DELETE when the table isn't `REPLICA IDENTITY FULL`).
    if (!belongsToUser(row, SupabaseSession.instance.currentUserId)) return;

    // Keep the shared member cache in step *before* the screens re-read it.
    if (table == 'attendance' && event == PostgresChangeEvent.insert) {
      // Only a new check-in decrements remaining_days — an update (attendance
      // rows normally aren't edited, but the table is now watched for every
      // event) must not re-decrement.
      final memberId = _str(row['member_id']);
      if (memberId != null) await members.applyCheckIn(memberId);
    } else if (table == 'members') {
      final id = _str(row['id']);
      if (id != null) {
        if (isDelete) {
          await members.applyMemberRemoval(id);
        } else {
          // Splice just this row (with its joins) into the cache so the list
          // screens see it on the bump below.
          await members.applyMemberUpsert(id);
        }
      }
    }

    bus.bump(table); // screens re-query — see also the 'members' bump for check-ins
    if (table == 'attendance') bus.bump('members'); // the Members list too

    // Don't banner a change this device just made (the screen already updated).
    // `consume` also *removes* the entry, so a later desktop edit of the same
    // row still notifies.
    if (SelfWriteRegistry.instance.consume(table, row['id'])) return;

    // The desktop's follow-up "synced: 0 -> 1" ack write is a second, separate
    // UPDATE on the same row — SelfWriteRegistry already fired on the first
    // one, so this one would otherwise slip through. It never touches
    // anything but `synced`, so it's not a real edit either way.
    if (event == PostgresChangeEvent.update &&
        onlyBookkeepingChanged(payload.oldRecord, row)) {
      return;
    }

    final notification = notificationFor(table, event, row);
    if (notification != null) notifications.add(notification);
  }

  /// `seance` only ever has its price edited (rows are created/removed
  /// server-side, not from this app) — so only an update is relevant there.
  /// Every other table reacts to every event (insert / update / delete) so
  /// the shared caches and live screens stay in sync — [notificationFor] is
  /// the one that decides, separately, whether an event is banner-worthy.
  bool _isRelevant(String table, PostgresChangeEvent event) => switch (table) {
        'seance' => event == PostgresChangeEvent.update,
        _ => true,
      };

  /// Whether a realtime change's row belongs to the signed-in account: it must
  /// carry a `user_id` equal to [currentUserId]. A missing `user_id` or no
  /// session -> dropped (better to miss a change than surface someone else's).
  @visibleForTesting
  static bool belongsToUser(Map<String, dynamic> row, String? currentUserId) {
    if (currentUserId == null) return false;
    return _str(row['user_id']) == currentUserId;
  }

  /// Whether an UPDATE's before/after rows differ in nothing but the
  /// `synced` bookkeeping column — i.e. it's a sync-status ack, not a real
  /// edit (see the "two UPDATE echoes" class doc above). No `oldRecord` (not
  /// every table is `REPLICA IDENTITY FULL`) means we can't tell, so treat it
  /// as a real change rather than risk swallowing one.
  @visibleForTesting
  static bool onlyBookkeepingChanged(
    Map<String, dynamic>? oldRow,
    Map<String, dynamic> newRow,
  ) {
    if (oldRow == null || oldRow.isEmpty) return false;
    for (final key in newRow.keys) {
      if (key == 'synced') continue;
      if (oldRow[key] != newRow[key]) return false;
    }
    return true;
  }

  /// Builds the notification for one (already-filtered) realtime change.
  @visibleForTesting
  GymNotification? notificationFor(
    String table,
    PostgresChangeEvent event,
    Map<String, dynamic> row,
  ) {
    if (!_isRelevant(table, event)) return null;
    final isInsert = event == PostgresChangeEvent.insert;

    switch (table) {
      // Insert-only banners: attendance / payments deletes still bump the bus
      // above (list refresh) but are never "news" worth a banner.
      case 'attendance':
        if (!isInsert) return null;
        return _memberNotification(
            GymNotificationKind.checkIn, row['member_id']);

      case 'payments':
        if (!isInsert) return null;
        return _memberNotification(
          GymNotificationKind.payment,
          row['member_id'],
          amountDzd: (row['amount'] as num?)?.round(),
        );

      case 'members':
        // Only a new member is "news" — an edit is silent, and a delete isn't
        // banner-worthy either (see class doc).
        if (!isInsert) return null;
        final id = row['id']?.toString();
        return GymNotification(
          kind: GymNotificationKind.memberAdded,
          // Name from the index if we have it, otherwise from the row payload.
          memberName:
              (id != null ? memberIndex.nameFor(id) : null) ?? _nameFromRow(row),
          // A brand-new member isn't in the photo index yet — don't show one.
          photoBytes: null,
        );

      case 'plans':
        // Insert + update banner; delete does not (see class doc).
        if (event == PostgresChangeEvent.delete) return null;
        return GymNotification(
          kind: event == PostgresChangeEvent.update
              ? GymNotificationKind.planUpdated
              : GymNotificationKind.planAdded,
          planName: (row['name'] as String?)?.trim(),
        );

      case 'seance':
        // _isRelevant already restricts this table to update only.
        return GymNotification(
          kind: GymNotificationKind.seancePriceUpdated,
          seanceGender: genderFromSexe(row['sexe']),
          seanceCardio: boolFromInt(row['cardio']),
          amountDzd: (row['price'] as num?)?.round(),
        );

      default:
        return null;
    }
  }

  GymNotification _memberNotification(
    GymNotificationKind kind,
    Object? memberId, {
    int? amountDzd,
  }) {
    final entry = memberId == null ? null : memberIndex[memberId.toString()];
    return GymNotification(
      kind: kind,
      memberName: entry?.name,
      photoBytes: entry?.photoBytes,
      amountDzd: amountDzd,
    );
  }

  String? _nameFromRow(Map<String, dynamic> row) {
    final first = (row['first_name'] as String?)?.trim() ?? '';
    final last = (row['last_name'] as String?)?.trim() ?? '';
    final name = [first, last].where((s) => s.isNotEmpty).join(' ');
    return name.isEmpty ? null : name;
  }

  static String? _str(Object? value) {
    final s = value?.toString().trim();
    return (s == null || s.isEmpty) ? null : s;
  }
}
