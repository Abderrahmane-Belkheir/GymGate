import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/mock/mock_repositories.dart';
import 'package:gymgate_app/data/repository_scope.dart';
import 'package:gymgate_app/features/members/members_screen.dart';
import 'package:gymgate_app/features/members/widgets/member_list_row.dart';
import 'package:gymgate_app/i18n/app_strings.dart';
import 'package:gymgate_app/theme/gym_theme.dart';

import 'support.dart';

void main() {
  late AppStrings strings;

  setUpAll(() async {
    strings = await loadEnglishStrings();
  });

  Widget app() => withStrings(
        strings,
        RepositoryScope(
          repositories: buildMockRepositories(reference: DateTime(2026, 9, 10)),
          child: MaterialApp(
            theme: buildGymTheme(),
            home: const Scaffold(body: MembersScreen()),
          ),
        ),
      );

  testWidgets('cancelling an active member drops them off the Active list',
      (tester) async {
    await tester.pumpWidget(app());
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    // Default filter = Male / Active. Boulbina Ahmed is an active male member.
    expect(find.text('Boulbina Ahmed'), findsOneWidget);

    final row = find.ancestor(
      of: find.text('Boulbina Ahmed'),
      matching: find.byType(MemberListRow),
    );
    await tester.tap(
      find.descendant(of: row, matching: find.byIcon(Icons.more_vert)),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Cancel Membership')); // menu item
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel Membership')); // confirm sheet
    await tester.pump();
    await tester.pump(const Duration(seconds: 1)); // cancel write
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 1)); // row re-fetch + reload
    await tester.pumpAndSettle();

    expect(find.widgetWithText(SnackBar, 'Membership cancelled'), findsOneWidget);
    // Moved to the Inactive side — no longer on this (Active) list.
    expect(find.text('Boulbina Ahmed'), findsNothing);
  });

  testWidgets('renewing an inactive member moves them onto the Active list',
      (tester) async {
    await tester.pumpWidget(app());
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    // Switch to the Inactive side — Abderrahmane Belkheir (male) is expired
    // (0 days left + past-ish end).
    await tester.tap(find.text('Inactive'));
    await tester.pumpAndSettle();
    expect(find.text('Abderrahmane Belkheir'), findsOneWidget);

    final row = find.ancestor(
      of: find.text('Abderrahmane Belkheir'),
      matching: find.byType(MemberListRow),
    );
    await tester.tap(
      find.descendant(of: row, matching: find.byIcon(Icons.more_vert)),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Renew'));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(milliseconds: 500)); // plans load
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.radio_button_unchecked).first);
    await tester.pump();
    await tester.tap(find.text('Confirm Renewal'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1)); // renew write
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 1)); // row re-fetch + reload
    await tester.pumpAndSettle();

    expect(find.widgetWithText(SnackBar, 'Membership renewed'), findsOneWidget);
    // No longer inactive.
    expect(find.text('Abderrahmane Belkheir'), findsNothing);
  });
}
