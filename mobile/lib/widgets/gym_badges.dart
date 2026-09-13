import 'package:flutter/material.dart';

import '../data/models.dart';
import '../i18n/app_strings.dart';
import '../theme/gym_colors.dart';
import '../theme/gym_tokens.dart';

/// Blue-soft pill with blue 11–12/700 text, for "N members / N results" context.
class GymCountBadge extends StatelessWidget {
  const GymCountBadge(this.label, {super.key, this.icon});

  final String label;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: GymPalette.accentSoft,
        borderRadius: BorderRadius.circular(GymRadii.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 12, color: GymPalette.accent),
            const SizedBox(width: 5),
          ],
          Text(
            label,
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w700,
              color: GymPalette.accent,
            ),
          ),
        ],
      ),
    );
  }
}

/// Small, quiet status pill. Saturated colour stays in the text/dot, never a
/// large fill.
class GymStatusPill extends StatelessWidget {
  const GymStatusPill(this.status, {super.key});

  final MembershipStatus status;

  @override
  Widget build(BuildContext context) {
    final c = context.gymColors;
    final s = context.s;
    final (String label, Color fg, Color bg) = switch (status) {
      MembershipStatus.active => (s.t('Active'), c.success, c.successSoft),
      MembershipStatus.expiring => (s.t('Expiring'), c.warning, c.warningSoft),
      MembershipStatus.expired => (s.t('Expired'), c.danger, c.dangerSoft),
      MembershipStatus.none =>
        (s.t('No_Plan'), GymPalette.textMuted, GymPalette.pageTint),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(GymRadii.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: BoxDecoration(color: fg, shape: BoxShape.circle),
          ),
          const SizedBox(width: 6),
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: fg,
            ),
          ),
        ],
      ),
    );
  }
}

/// Blue-soft pill naming a membership plan (a check-circle glyph + the plan
/// name). Used in the Members and Payments rows.
class GymPlanChip extends StatelessWidget {
  const GymPlanChip(this.label, {super.key});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: GymPalette.accentSoft,
        borderRadius: BorderRadius.circular(GymRadii.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.check_circle, size: 12, color: GymPalette.accent),
          const SizedBox(width: 5),
          Text(
            label,
            style: const TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: GymPalette.accent,
            ),
          ),
        ],
      ),
    );
  }
}

/// Soft-green pill for a positive outcome — a granted check-in. Same shape as
/// [GymStatusPill] but always success-toned, for cases where the event itself
/// means "allowed in" (a recorded attendance row, the last recognised member).
class GymSuccessPill extends StatelessWidget {
  const GymSuccessPill(this.label, {super.key});

  final String label;

  @override
  Widget build(BuildContext context) {
    final c = context.gymColors;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: c.successSoft,
        borderRadius: BorderRadius.circular(GymRadii.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: BoxDecoration(color: c.success, shape: BoxShape.circle),
          ),
          const SizedBox(width: 6),
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: c.success,
            ),
          ),
        ],
      ),
    );
  }
}
