import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/mock/mock_repositories.dart';
import 'package:gymgate_app/data/repository_scope.dart';
import 'package:gymgate_app/features/attendance/attendance_screen.dart';
import 'package:gymgate_app/features/attendance/widgets/attendance_list_row.dart';
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
            home: const Scaffold(body: AttendanceScreen()),
          ),
        ),
      );

  testWidgets("shows the header, then today's check-ins once loaded",
      (tester) async {
    await tester.pumpWidget(app());

    expect(find.text('Attendance'), findsOneWidget);
    expect(find.text('Confirm'), findsOneWidget);

    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();

    // The mock seed has six check-ins on the current day.
    expect(find.byType(AttendanceListRow), findsNWidgets(6));
    expect(find.text('6 present'), findsOneWidget);
    expect(find.text('Present'), findsNWidgets(6));
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
}
