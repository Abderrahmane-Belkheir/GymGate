import 'package:flutter/material.dart';

import '../i18n/app_strings.dart';
import '../theme/gym_colors.dart';
import '../theme/gym_tokens.dart';
import '../widgets/gym_empty_state.dart';
import 'gym_notification.dart';
import 'notification_center.dart';

/// Opens the in-memory notification list (from the bell). Nothing is persisted;
/// opening it marks everything read.
Future<void> showNotificationSheet(BuildContext context) {
  final center = NotificationScope.of(context);
  center.markAllRead();
  return showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) => _NotificationSheet(center: center),
  );
}

class _NotificationSheet extends StatelessWidget {
  const _NotificationSheet({required this.center});

  final NotificationCenter center;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = context.s;
    final now = DateTime.now();

    return ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.72,
      ),
      child: ListenableBuilder(
        listenable: center,
        builder: (context, _) {
          final items = center.items;
          return Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(
                    GymSpacing.lg, 0, GymSpacing.lg, GymSpacing.sm),
                child: Text(s.t('Notifications'),
                    style: theme.textTheme.titleMedium),
              ),
              const Divider(height: 1),
              if (items.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: GymSpacing.lg),
                  child: GymEmptyState(
                    icon: Icons.notifications_none_rounded,
                    message: s.t('No_notifications'),
                  ),
                )
              else
                Flexible(
                  child: ListView.separated(
                    shrinkWrap: true,
                    padding: EdgeInsets.only(
                      bottom: GymSpacing.lg +
                          MediaQuery.of(context).padding.bottom,
                    ),
                    itemCount: items.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (context, i) =>
                        _NotificationTile(notification: items[i], now: now),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _NotificationTile extends StatelessWidget {
  const _NotificationTile({required this.notification, required this.now});

  final GymNotification notification;
  final DateTime now;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final p = notification.present(context.s);

    return Padding(
      padding: const EdgeInsets.symmetric(
          horizontal: GymSpacing.lg, vertical: GymSpacing.md),
      child: Row(
        children: [
          notification.leading(size: 38),
          const SizedBox(width: GymSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(p.title, style: theme.textTheme.titleSmall),
                const SizedBox(height: 2),
                // Body carries the member's name — never truncate it.
                Text(
                  p.body,
                  style: theme.textTheme.bodySmall?.copyWith(fontSize: 12),
                ),
              ],
            ),
          ),
          const SizedBox(width: GymSpacing.sm),
          Text(
            notificationAge(notification.time, now),
            style: theme.textTheme.bodySmall
                ?.copyWith(fontSize: 11, color: GymPalette.textMuted),
          ),
        ],
      ),
    );
  }
}
