import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/core/formatting.dart';
import 'package:gymgate_app/data/mock/mock_repositories.dart';
import 'package:gymgate_app/data/models.dart';
import 'package:gymgate_app/data/repository_scope.dart';
import 'package:gymgate_app/features/payments/payments_screen.dart';
import 'package:gymgate_app/features/payments/widgets/payment_list_row.dart';
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
          // No reference → mock data is seeded relative to DateTime.now(),
          // which is also the date the screen defaults to.
          repositories: buildMockRepositories(),
          child: MaterialApp(
            theme: buildGymTheme(),
            home: const Scaffold(body: PaymentsScreen()),
          ),
        ),
      );

  testWidgets("shows today's payments, the count and the revenue total",
      (tester) async {
    await tester.pumpWidget(app());

    expect(find.text('Payments'), findsOneWidget);

    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    // Mock seed: three payments on the current day (2000 + 4000 + 2000).
    expect(find.byType(PaymentListRow), findsNWidgets(3));
    expect(find.text('3 Payments'), findsOneWidget);
    expect(find.text(formatDzd(8000)), findsOneWidget);
  });

  testWidgets('Confirm is disabled until the date selection changes',
      (tester) async {
    await tester.pumpWidget(app());
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    final confirm = tester.widget<InkWell>(
      find.ancestor(of: find.text('Confirm'), matching: find.byType(InkWell)),
    );
    expect(confirm.onTap, isNull);
  });

  testWidgets('a single-session (walk-in) payment row shows the séance detail',
      (tester) async {
    await tester.pumpWidget(
      withStrings(
        strings,
        MaterialApp(
          theme: buildGymTheme(),
          home: Scaffold(
            body: PaymentListRow(
              payment: Payment(
                id: 'x',
                memberId: '',
                memberName: '', // walk-in: no member
                amountDzd: 200,
                date: DateTime(2026, 9, 10, 11),
                seance: const Seance(
                  id: 's1',
                  priceDzd: 200,
                  cardio: true,
                  restrictedTo: Gender.female,
                ),
              ),
            ),
          ),
        ),
      ),
    );

    expect(find.text('Walk-in'), findsOneWidget);
    expect(find.text('Session'), findsOneWidget);
    expect(find.text('Female'), findsOneWidget);
    expect(find.text('With cardio'), findsOneWidget);
    expect(find.text(formatDzd(200)), findsWidgets); // séance price + amount
  });
}
