import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../core/formatting.dart';
import '../data/models.dart';
import '../i18n/app_strings.dart';
import '../widgets/gym_avatar.dart';
import '../widgets/gym_icon_tile.dart';

enum GymNotificationKind {
  checkIn,
  payment,
  memberAdded,
  memberRemoved,
  planAdded,
  planUpdated,
  planRemoved,
  seancePriceUpdated,
}

/// An in-memory notification raised from a Supabase Realtime event. Not
/// persisted — the list is cleared on app restart. Text is built at render time
/// from [kind] + the data below so it follows the current language.
class GymNotification {
  GymNotification({
    required this.kind,
    this.memberName,
    this.amountDzd,
    this.planName,
    this.photoBytes,
    this.seanceGender,
    this.seanceCardio,
  })  : id = '${DateTime.now().microsecondsSinceEpoch}',
        time = DateTime.now();

  final String id;
  final DateTime time;
  final GymNotificationKind kind;
  final String? memberName;

  /// A payment amount, or (for [GymNotificationKind.seancePriceUpdated]) the
  /// séance's new price — both are "an amount in DZD".
  final int? amountDzd;
  final String? planName;

  /// Which séance rate changed, for [GymNotificationKind.seancePriceUpdated].
  /// `null` restrictedTo/cardio renders as "everyone" / omitted — see [present].
  final Gender? seanceGender;
  final bool? seanceCardio;

  /// The member's photo (from the shared member index), when known. Absent for
  /// a member-INSERT — that member isn't in the index yet.
  final Uint8List? photoBytes;

  bool read = false;

  IconData get icon => switch (kind) {
        GymNotificationKind.checkIn => Icons.login_rounded,
        GymNotificationKind.payment => Icons.payments_outlined,
        GymNotificationKind.memberAdded => Icons.person_add_alt_1_outlined,
        GymNotificationKind.memberRemoved => Icons.person_remove_alt_1_outlined,
        GymNotificationKind.planAdded => Icons.card_membership_outlined,
        GymNotificationKind.planUpdated => Icons.card_membership_outlined,
        GymNotificationKind.planRemoved => Icons.card_membership_outlined,
        GymNotificationKind.seancePriceUpdated => Icons.sell_outlined,
      };

  GymTileTone get tone => switch (kind) {
        GymNotificationKind.payment => GymTileTone.success,
        GymNotificationKind.memberRemoved ||
        GymNotificationKind.planRemoved =>
          GymTileTone.danger,
        GymNotificationKind.checkIn ||
        GymNotificationKind.memberAdded ||
        GymNotificationKind.planAdded ||
        GymNotificationKind.planUpdated ||
        GymNotificationKind.seancePriceUpdated =>
          GymTileTone.accent,
      };

  /// Leading widget: the member's photo when known, otherwise a kind icon tile.
  Widget leading({double size = 38}) {
    final bytes = photoBytes;
    if (bytes != null && bytes.isNotEmpty) {
      return GymAvatar(
        initials: initialsFrom(memberName ?? ''),
        imageBytes: bytes,
        size: size,
        enlargeOnTap: false,
      );
    }
    return GymIconTile(icon: icon, tone: tone, size: size);
  }

  ({String title, String body}) present(AppStrings s) {
    final name = (memberName == null || memberName!.isEmpty)
        ? s.t('notif_someone')
        : memberName!;
    return switch (kind) {
      GymNotificationKind.checkIn =>
        (title: s.t('notif_check_in'), body: '$name ${s.t('notif_visited')}'),
      GymNotificationKind.payment => (
          title: s.t('notif_payment'),
          body: amountDzd != null
              ? '$name ${s.t('notif_paid')} ${formatDzd(amountDzd!)}'
              : name,
        ),
      GymNotificationKind.memberAdded =>
        (title: s.t('notif_new_member'), body: name),
      GymNotificationKind.memberRemoved =>
        (title: s.t('notif_member_removed'), body: name),
      GymNotificationKind.planAdded => (
          title: s.t('notif_new_plan'),
          body: planName ?? s.t('Plans'),
        ),
      GymNotificationKind.planUpdated => (
          title: s.t('notif_plan_updated'),
          body: planName ?? s.t('Plans'),
        ),
      GymNotificationKind.planRemoved => (
          title: s.t('notif_plan_removed'),
          body: planName ?? s.t('Plans'),
        ),
      GymNotificationKind.seancePriceUpdated => (
          title: s.t('notif_seance_price_updated'),
          body: [
            if (seanceGender != null)
              s.t(seanceGender == Gender.female ? 'Female' : 'Male'),
            if (seanceCardio != null)
              s.t(seanceCardio! ? 'With_cardio' : 'Standard'),
            if (amountDzd != null) formatDzd(amountDzd!),
          ].join(' · '),
        ),
    };
  }
}

/// `now` / `5m` / `2h` / `3d` — compact relative age.
String notificationAge(DateTime time, DateTime now) {
  final d = now.difference(time);
  if (d.inMinutes < 1) return 'now';
  if (d.inHours < 1) return '${d.inMinutes}m';
  if (d.inDays < 1) return '${d.inHours}h';
  return '${d.inDays}d';
}
