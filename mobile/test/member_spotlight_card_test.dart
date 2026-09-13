import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/models.dart';
import 'package:gymgate_app/features/home/widgets/member_spotlight_card.dart';
import 'package:gymgate_app/i18n/app_strings.dart';
import 'package:gymgate_app/theme/gym_theme.dart';

import 'support.dart';

/// The last recognised member passed the gate, so the card always shows a
/// granted check-in — never their current Active/Expiring/Expired state.
void main() {
  final now = DateTime(2026, 8, 29, 19);
  late AppStrings strings;

  setUpAll(() async {
    strings = await loadEnglishStrings();
  });

  Widget host(Member? m, {DateTime? checkInAt}) => withStrings(
        strings,
        MaterialApp(
          theme: buildGymTheme(),
          home: Scaffold(
            body: MemberSpotlightCard(member: m, now: now, checkInAt: checkInAt),
          ),
        ),
      );

  testWidgets('shows "Checked in", not a status pill, even when expired',
      (tester) async {
    final expired = Member(
      id: 'm',
      firstName: 'Reda',
      lastName: 'B',
      gender: Gender.male,
      phone: '0',
      plan: const Plan(id: 'p', name: 'std', priceDzd: 2000, visitsPerMonth: 12),
      membershipEnd: DateTime(2026, 8, 20), // already passed
      remainingDays: 0,
    );

    await tester.pumpWidget(host(expired, checkInAt: now));

    expect(find.text('Checked in'), findsOneWidget);
    expect(find.text('Expired'), findsNothing);
    expect(find.text('Active'), findsNothing);

    // Remaining is the raw count, never "Expires today" / "Expired…".
    expect(find.text('0 days'), findsOneWidget);
    expect(find.text('Expires today'), findsNothing);
  });

  testWidgets('with no member, shows the "No Person" placeholder card',
      (tester) async {
    await tester.pumpWidget(host(null));

    expect(find.text('No Person'), findsOneWidget);
    expect(find.text('0xxxxxxxxx'), findsOneWidget);
    expect(find.text('demo'), findsOneWidget); // plan chip value
    expect(find.text('Checked in'), findsOneWidget);
  });

  testWidgets('long names are shown in full (not truncated)', (tester) async {
    final longName = Member(
      id: 'm',
      firstName: 'Abderrahmane',
      lastName: 'Belkheir-Boumediene',
      gender: Gender.male,
      phone: '0555 11 22 33',
    );
    await tester.pumpWidget(host(longName, checkInAt: now));

    final text = tester.widget<Text>(
      find.text('Abderrahmane Belkheir-Boumediene'),
    );
    expect(text.maxLines, isNull); // wraps instead of ellipsizing
    expect(text.overflow, anyOf(isNull, TextOverflow.clip));
  });
}
