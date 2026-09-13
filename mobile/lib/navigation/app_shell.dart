import 'package:flutter/material.dart';

import '../i18n/app_strings.dart';
import '../i18n/locale_controller.dart';
import '../notifications/notification_banner_host.dart';
import '../notifications/notification_center.dart';
import '../notifications/notification_sheet.dart';
import '../theme/gym_colors.dart';
import 'app_destinations.dart';
import 'gym_drawer.dart';

/// The mobile app shell: a top bar with a hamburger button, the navy side
/// drawer, and the active section. Home (index 0) is shown first.
class AppShell extends StatefulWidget {
  const AppShell({super.key});

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  final _scaffoldKey = GlobalKey<ScaffoldState>();
  final _bellKey = GlobalKey();
  int _index = 0;

  void _select(int index) {
    Navigator.of(context).pop(); // close the drawer
    if (index != _index) setState(() => _index = index);
  }

  @override
  Widget build(BuildContext context) {
    final destination = kAppDestinations[_index];
    final s = context.s;

    return Stack(
      children: [
        Scaffold(
          key: _scaffoldKey,
          drawer: GymDrawer(currentIndex: _index, onSelect: _select),
          drawerEdgeDragWidth: 48,
          appBar: AppBar(
            titleSpacing: 0,
            leading: IconButton(
              icon: const Icon(Icons.menu),
              tooltip: s.t('Menu'),
              onPressed: () => _scaffoldKey.currentState?.openDrawer(),
            ),
            title: Text(s.t(destination.labelKey)),
            actions: [
              _NotificationBell(bellKey: _bellKey),
              const _LanguageButton(),
              const SizedBox(width: 4),
            ],
          ),
          body: AnimatedSwitcher(
            duration: const Duration(milliseconds: 200),
            switchInCurve: Curves.easeOut,
            switchOutCurve: Curves.easeIn,
            child: KeyedSubtree(
              key: ValueKey(_index),
              child: Builder(builder: destination.builder),
            ),
          ),
        ),
        NotificationBannerHost(bellKey: _bellKey),
      ],
    );
  }
}

/// App-bar bell with an unread badge; opens the in-memory notification list.
class _NotificationBell extends StatelessWidget {
  const _NotificationBell({required this.bellKey});

  final GlobalKey bellKey;

  @override
  Widget build(BuildContext context) {
    final center = NotificationScope.maybeOf(context);
    if (center == null) {
      return IconButton(
        key: bellKey,
        icon: const Icon(Icons.notifications_none_rounded),
        onPressed: () {},
      );
    }
    return ListenableBuilder(
      listenable: center,
      builder: (context, _) {
        final count = center.unreadCount;
        return IconButton(
          key: bellKey,
          tooltip: context.s.t('Notifications'),
          onPressed: () => showNotificationSheet(context),
          icon: Badge(
            isLabelVisible: count > 0,
            label: Text('$count'),
            child: const Icon(Icons.notifications_none_rounded),
          ),
        );
      },
    );
  }
}

/// Language picker in the app bar — replaces the old reception-desk avatar.
/// The active language carries a check mark.
class _LanguageButton extends StatelessWidget {
  const _LanguageButton();

  @override
  Widget build(BuildContext context) {
    final controller = LocaleScope.of(context);
    final s = context.s;
    final current = controller.languageCode;

    return PopupMenuButton<String>(
      tooltip: s.t('Language'),
      icon: const Icon(Icons.translate_rounded),
      position: PopupMenuPosition.under,
      onSelected: controller.setLanguage,
      itemBuilder: (context) => [
        for (final code in AppTranslations.supportedLanguageCodes)
          PopupMenuItem<String>(
            value: code,
            child: Row(
              children: [
                Expanded(child: Text(s.t('language_name_$code'))),
                if (code == current)
                  const Icon(Icons.check_rounded,
                      size: 18, color: GymPalette.accent),
              ],
            ),
          ),
      ],
    );
  }
}
