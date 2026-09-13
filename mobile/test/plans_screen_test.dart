import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/mock/mock_repositories.dart';
import 'package:gymgate_app/data/repository_scope.dart';
import 'package:gymgate_app/features/plans/plans_screen.dart';
import 'package:gymgate_app/features/plans/widgets/plan_card.dart';
import 'package:gymgate_app/features/plans/widgets/seance_card.dart';
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
          repositories: buildMockRepositories(),
          child: MaterialApp(
            theme: buildGymTheme(),
            home: const Scaffold(body: PlansScreen()),
          ),
        ),
      );

  testWidgets('renders plan cards and the single-session price section',
      (tester) async {
    await tester.pumpWidget(app());
    expect(find.text('Membership Plans'), findsOneWidget);

    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    // Male is the default filter — the mock plans are offered to everyone.
    expect(find.byType(PlanCard), findsWidgets);

    // Scroll the lazy list down to the séance block below the plan cards.
    await tester.scrollUntilVisible(find.text('Session'), 300);
    expect(find.text('Price for a single session'), findsOneWidget);

    await tester.scrollUntilVisible(find.byType(SeanceCard).last, 300);
    expect(find.byType(SeanceCard), findsNWidgets(2));
    expect(find.text('With cardio'), findsOneWidget);
    expect(find.text('Standard'), findsOneWidget);
  });

  testWidgets('New Plan sheet inserts a plan and the list shows it',
      (tester) async {
    await tester.pumpWidget(app());
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    await tester.tap(find.text('New Plan'));
    await tester.pumpAndSettle();

    final fields = find.byType(TextField);
    await tester.enterText(fields.at(0), 'Weekend pass'); // name
    await tester.enterText(fields.at(1), '3000'); // price
    // duration defaults to 1; days-per-month left blank (unlimited).
    await tester.pump();

    await tester.tap(find.text('Create Plan'));
    await tester.pump(); // kick off _submit
    await tester.pump(const Duration(seconds: 1)); // mock createPlan latency
    await tester.pumpAndSettle(); // sheet closes
    await tester.pump(const Duration(seconds: 1)); // _refresh fetch latency
    await tester.pumpAndSettle();

    expect(find.text('Plan created'), findsOneWidget); // snackbar
    await tester.scrollUntilVisible(find.text('Weekend pass'), 300);
    expect(find.text('Weekend pass'), findsOneWidget);
  });

  testWidgets('the pencil opens the pre-filled editor and saves the change',
      (tester) async {
    await tester.pumpWidget(app());
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    // Edit the first plan card.
    await tester.tap(find.byIcon(Icons.edit_outlined).first);
    await tester.pumpAndSettle();

    expect(find.text('Edit Plan'), findsOneWidget);
    // Pre-filled: the name field carries the plan's name, gender is hidden.
    final name = tester.widget<TextField>(find.byType(TextField).at(0));
    expect(name.controller!.text, isNotEmpty);
    expect(find.text('Male / Female'), findsNothing);

    await tester.enterText(find.byType(TextField).at(1), '2500'); // price
    await tester.pump();

    await tester.tap(find.text('Save Changes'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1)); // mock updatePlan latency
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 1)); // _refresh latency
    await tester.pumpAndSettle();

    expect(find.text('Plan updated'), findsOneWidget); // snackbar
    expect(find.text('2,500'), findsWidgets); // the edited price block
  });

  testWidgets('a séance pencil updates that rate\'s price in real time',
      (tester) async {
    await tester.pumpWidget(app());
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(find.text('Session'), 300);
    await tester.scrollUntilVisible(find.byType(SeanceCard).last, 300);
    expect(find.byType(SeanceCard), findsNWidgets(2));

    // The last card is the "With cardio" (200 DZD) rate — open its editor.
    // `ensureVisible` (rather than trusting the scroll above alone) guarantees
    // the pencil itself is fully in the viewport and actually hittable.
    final cardioCard = find.ancestor(
      of: find.text('With cardio'),
      matching: find.byType(SeanceCard),
    );
    final pencil =
        find.descendant(of: cardioCard, matching: find.byIcon(Icons.edit_outlined));
    await tester.ensureVisible(pencil);
    await tester.pumpAndSettle();
    await tester.tap(pencil);
    await tester.pumpAndSettle();

    expect(find.text('Update Price'), findsOneWidget);
    // Pre-filled with the current price; gender/cardio aren't editable here.
    final priceField = tester.widget<TextField>(find.byType(TextField).first);
    expect(priceField.controller!.text, '200');

    await tester.enterText(find.byType(TextField).first, '225');
    await tester.pump();

    await tester.tap(find.text('Save Changes'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1)); // mock updatePrice latency
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 1)); // _refresh latency
    await tester.pumpAndSettle();

    expect(find.text('Session price updated'), findsOneWidget); // snackbar
    expect(find.text('225'), findsOneWidget);
  });
}
