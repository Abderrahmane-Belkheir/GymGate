import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/mock/mock_repositories.dart';
import 'package:gymgate_app/data/repository_scope.dart';
import 'package:gymgate_app/features/attendance/attendance_screen.dart';
import 'package:gymgate_app/features/payments/payments_screen.dart';
import 'package:gymgate_app/features/payments/widgets/payment_list_row.dart';
import 'package:gymgate_app/i18n/app_strings.dart';
import 'package:gymgate_app/theme/gym_theme.dart';
import 'package:gymgate_app/widgets/gym_trend_chart.dart';

import 'support.dart';

void main() {
  late AppStrings strings;

  setUpAll(() async {
    strings = await loadEnglishStrings();
  });

  Widget host(Widget screen) => withStrings(
        strings,
        RepositoryScope(
          repositories: buildMockRepositories(),
          child: MaterialApp(
            theme: buildGymTheme(),
            home: Scaffold(body: screen),
          ),
        ),
      );

  testWidgets('Payments: List ↔ Statistics switch renders the trend chart',
      (tester) async {
    await tester.pumpWidget(host(const PaymentsScreen()));
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    expect(find.byType(PaymentListRow), findsWidgets);
    expect(find.byType(GymTrendChart), findsNothing);

    await tester.tap(find.text('Statistics'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    expect(find.byType(GymTrendChart), findsOneWidget);
    expect(find.byType(PaymentListRow), findsNothing);
    expect(find.textContaining('Average'), findsOneWidget);
    expect(find.textContaining('Peak'), findsOneWidget);

    await tester.tap(find.text('List'));
    await tester.pump();
    await tester.pumpAndSettle();
    expect(find.byType(PaymentListRow), findsWidgets);
  });

  testWidgets('Attendance: Statistics view shows the busiest-day line',
      (tester) async {
    await tester.pumpWidget(host(const AttendanceScreen()));
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Statistics'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    expect(find.byType(GymTrendChart), findsOneWidget);
    // Daily mode is the default → the attendance-only pattern line is shown.
    expect(find.textContaining('Busiest day'), findsOneWidget);
  });
}
