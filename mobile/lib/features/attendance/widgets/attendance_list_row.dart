import 'package:flutter/material.dart';

import '../../../core/formatting.dart';
import '../../../data/models.dart';
import '../../../data/repository_scope.dart';
import '../../../i18n/app_strings.dart';
import '../../../theme/gym_colors.dart';
import '../../../theme/gym_tokens.dart';
import '../../../widgets/gym_avatar.dart';
import '../../../widgets/gym_badges.dart';

/// One check-in, adapted from the desktop `Présence` table row: avatar + member
/// name on the left, check-in time + a small "Present" pill on the right.
class AttendanceListRow extends StatelessWidget {
  const AttendanceListRow({super.key, required this.entry});

  final AttendanceEntry entry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final name = entry.memberName.trim();
    final photo =
        RepositoryScope.maybeOf(context)?.memberIndex.photoFor(entry.memberId);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        children: [
          GymAvatar(initials: initialsFrom(name), imageBytes: photo, size: 44),
          const SizedBox(width: GymSpacing.md),
          Expanded(
            child: Text(
              name.isEmpty ? context.s.t('Unknown_member') : name,
              style: theme.textTheme.titleSmall,
            ),
          ),
          const SizedBox(width: GymSpacing.sm),
          const Icon(Icons.schedule, size: 13, color: GymPalette.textMuted),
          const SizedBox(width: 5),
          Text(
            formatTime(entry.checkIn),
            style: theme.textTheme.bodyLarge?.copyWith(fontSize: 12),
          ),
          const SizedBox(width: GymSpacing.sm),
          GymSuccessPill(context.s.t('Present')),
        ],
      ),
    );
  }
}
