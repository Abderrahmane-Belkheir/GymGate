import 'package:flutter/material.dart';

import 'core/connectivity_recovery.dart';
import 'data/repositories.dart';
import 'data/repository_scope.dart';
import 'data/supabase/supabase_repositories.dart';
import 'i18n/app_strings.dart';
import 'i18n/locale_controller.dart';
import 'navigation/app_shell.dart';
import 'notifications/notification_center.dart';
import 'realtime/realtime_bus.dart';
import 'realtime/realtime_service.dart';
import 'theme/gym_theme.dart';

class GymGateApp extends StatefulWidget {
  const GymGateApp({
    super.key,
    required this.translations,
    required this.localeController,
  });

  final AppTranslations translations;
  final LocaleController localeController;

  @override
  State<GymGateApp> createState() => _GymGateAppState();
}

class _GymGateAppState extends State<GymGateApp> with WidgetsBindingObserver {
  // Built once for the app's lifetime so the shared member cache (see
  // `CachingMembersRepository`) survives rebuilds.
  final GymRepositories _repositories = buildSupabaseRepositories();

  final RealtimeBus _realtimeBus = RealtimeBus();
  final NotificationCenter _notifications = NotificationCenter();
  late final RealtimeService _realtime;
  late final ConnectivityRecovery _connectivity;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    _realtime = RealtimeService(
      bus: _realtimeBus,
      notifications: _notifications,
      memberIndex: _repositories.memberIndex,
      members: _repositories.members,
    );
    // Fire-and-forget: start() awaits the JWT internally before subscribing.
    _realtime.start();

    // If the app launched offline, the sign-in above (and the JWT it needs)
    // silently failed, and/or the realtime socket never opened — this retries
    // both the moment the network comes back, instead of waiting on a manual
    // pull-to-refresh. See didChangeAppLifecycleState for the other trigger:
    // a backgrounded socket the OS silently killed.
    _connectivity = ConnectivityRecovery(
      bus: _realtimeBus,
      ensureRealtimeConnected: _realtime.ensureConnected,
    )..start();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // The most common way the realtime socket goes stale: the OS suspends the
    // app in the background (screen off, app switched away) for long enough
    // that it kills the socket without ever telling the app — no connectivity
    // change involved, since the network itself never dropped. Check on every
    // resume, not just on reconnect.
    if (state == AppLifecycleState.resumed) {
      _realtime.ensureConnected();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _connectivity.dispose();
    _realtime.dispose();
    _notifications.dispose();
    _realtimeBus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return LocaleScope(
      controller: widget.localeController,
      child: RealtimeScope(
        bus: _realtimeBus,
        child: NotificationScope(
          center: _notifications,
          child: RepositoryScope(
            repositories: _repositories,
            child: MaterialApp(
              title: 'GymGate',
              debugShowCheckedModeBanner: false,
              theme: buildGymTheme(),
              // Only the wrapper below rebuilds when the language changes — the
              // MaterialApp, its Navigator and Overlay stay put.
              builder: (context, child) => ListenableBuilder(
                listenable: widget.localeController,
                builder: (context, _) {
                  final strings = widget.translations
                      .forLanguage(widget.localeController.languageCode);
                  return StringsScope(
                    strings: strings,
                    child: Directionality(
                      textDirection: strings.isRtl
                          ? TextDirection.rtl
                          : TextDirection.ltr,
                      child: child!,
                    ),
                  );
                },
              ),
              home: const AppShell(),
            ),
          ),
        ),
      ),
    );
  }
}
