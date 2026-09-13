import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/mock/mock_repositories.dart';
import 'package:gymgate_app/data/repository_scope.dart';
import 'package:gymgate_app/features/home/home_screen.dart';
import 'package:gymgate_app/i18n/app_strings.dart';
import 'package:gymgate_app/navigation/gym_drawer.dart';
import 'package:gymgate_app/theme/gym_theme.dart';

import 'support.dart';

void main() {
  late AppStrings strings;

  setUpAll(() async {
    strings = await loadEnglishStrings();
  });

  testWidgets('Home renders and the drawer lists the sections', (tester) async {
    await tester.pumpWidget(
      withStrings(
        strings,
        RepositoryScope(
          repositories: buildMockRepositories(),
          child: MaterialApp(
            theme: buildGymTheme(),
            home: Builder(
              builder: (context) => Scaffold(
                drawer: const GymDrawer(currentIndex: 0, onSelect: _noop),
                appBar: AppBar(
                  leading: Builder(
                    builder: (context) => IconButton(
                      icon: const Icon(Icons.menu),
                      onPressed: Scaffold.of(context).openDrawer,
                    ),
                  ),
                ),
                body: const HomeScreen(),
              ),
            ),
          ),
        ),
      ),
    );

    // Header renders immediately, before mock data resolves.
    expect(find.text('Overview'), findsOneWidget);

    // Mock repositories settle.
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();
    expect(find.text("Today's Statistics"), findsOneWidget);

    // Drawer navigation is present.
    await tester.tap(find.byIcon(Icons.menu));
    await tester.pumpAndSettle();
    expect(find.text('Members'), findsOneWidget);
    expect(find.text('Payments'), findsOneWidget);
    expect(find.text('Attendance'), findsOneWidget);
  });
}

void _noop(int _) {}
