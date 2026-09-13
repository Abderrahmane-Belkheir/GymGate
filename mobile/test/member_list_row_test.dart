import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/mock/mock_repositories.dart';
import 'package:gymgate_app/data/models.dart';
import 'package:gymgate_app/data/repository_scope.dart';
import 'package:gymgate_app/features/members/widgets/member_list_row.dart';
import 'package:gymgate_app/i18n/app_strings.dart';
import 'package:gymgate_app/theme/gym_theme.dart';

import 'support.dart';

void main() {
  late AppStrings strings;
  final now = DateTime(2026, 9, 10);

  setUpAll(() async {
    strings = await loadEnglishStrings();
  });

  const plan = Plan(id: 'p', name: '3 fois', priceDzd: 2000, visitsPerMonth: 12);

  Member member({required DateTime end, required int remainingDays}) => Member(
        id: 'm',
        firstName: 'Test',
        lastName: 'Member',
        gender: Gender.male,
        phone: '0',
        plan: plan,
        membershipStart: DateTime(2026, 1, 1),
        membershipEnd: end,
        remainingDays: remainingDays,
      );

  Future<void> pumpRow(WidgetTester tester, Member m) => tester.pumpWidget(
        withStrings(
          strings,
          RepositoryScope(
            repositories: buildMockRepositories(),
            child: MaterialApp(
              theme: buildGymTheme(),
              home: Scaffold(body: MemberListRow(member: m, now: now)),
            ),
          ),
        ),
      );

  testWidgets('active member → ⋯ offers Cancel Membership; it fires the write',
      (tester) async {
    await pumpRow(tester, member(end: DateTime(2026, 12, 31), remainingDays: 30));

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    expect(find.text('Cancel Membership'), findsOneWidget);
    expect(find.text('Renew'), findsNothing);

    // Menu item -> confirm sheet.
    await tester.tap(find.text('Cancel Membership'));
    await tester.pumpAndSettle();
    expect(find.text('Cancel membership ?'), findsOneWidget);

    // Confirm.
    await tester.tap(find.text('Cancel Membership'));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(milliseconds: 500)); // mock latency
    await tester.pump();

    expect(find.widgetWithText(SnackBar, 'Membership cancelled'), findsOneWidget);
  });

  testWidgets('dismissing the confirm card does not cancel', (tester) async {
    await pumpRow(tester, member(end: DateTime(2026, 12, 31), remainingDays: 30));

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel Membership')); // menu item
    await tester.pumpAndSettle();

    expect(find.text('Cancel membership ?'), findsOneWidget);
    await tester.tap(find.text('Cancel')); // dismiss button
    await tester.pumpAndSettle();
    await tester.pump(const Duration(milliseconds: 500));

    expect(find.widgetWithText(SnackBar, 'Membership cancelled'), findsNothing);
    expect(find.text('Cancel membership ?'), findsNothing); // sheet closed
  });

  testWidgets('expired member → ⋯ Renew opens the picker; confirm renews',
      (tester) async {
    await pumpRow(tester, member(end: DateTime(2026, 8, 1), remainingDays: 0));

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    expect(find.text('Renew'), findsOneWidget);

    await tester.tap(find.text('Renew'));
    await tester.pumpAndSettle(); // sheet opens
    await tester.pump(const Duration(milliseconds: 500)); // plans load
    await tester.pumpAndSettle();

    expect(find.text('Choose a Plan'), findsOneWidget);
    // Male member → the male-or-shared mock plans are listed.
    expect(find.byIcon(Icons.radio_button_unchecked), findsWidgets);

    // Confirm is disabled until a plan is picked.
    var confirm = tester.widget<InkWell>(
      find.ancestor(of: find.text('Confirm Renewal'), matching: find.byType(InkWell)),
    );
    expect(confirm.onTap, isNull);

    await tester.tap(find.byIcon(Icons.radio_button_unchecked).first);
    await tester.pump();
    confirm = tester.widget<InkWell>(
      find.ancestor(of: find.text('Confirm Renewal'), matching: find.byType(InkWell)),
    );
    expect(confirm.onTap, isNotNull);

    await tester.tap(find.text('Confirm Renewal'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1)); // renew write
    await tester.pumpAndSettle();

    expect(find.widgetWithText(SnackBar, 'Membership renewed'), findsOneWidget);
  });
}
