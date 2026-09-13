import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/widgets/gym_avatar.dart';

/// A real 1×1 PNG so `Image.memory` decodes without logging errors.
final _png = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQ'
  'DwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
);

void main() {
  testWidgets('tapping a photo avatar opens the blurred enlarged viewer',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(child: GymAvatar(initials: 'AB', imageBytes: _png)),
        ),
      ),
    );

    expect(find.byType(BackdropFilter), findsNothing);

    await tester.tap(find.byType(GymAvatar));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.byType(BackdropFilter), findsOneWidget); // blurred backdrop
    expect(find.byIcon(Icons.close_rounded), findsOneWidget);

    // Tap the backdrop → dismiss.
    await tester.tapAt(const Offset(8, 8));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.byType(BackdropFilter), findsNothing);
  });

  testWidgets('an avatar with no photo has no tap target', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: Center(child: GymAvatar(initials: 'AB'))),
      ),
    );

    expect(
      find.descendant(
        of: find.byType(GymAvatar),
        matching: find.byType(GestureDetector),
      ),
      findsNothing,
    );
  });

  testWidgets('enlargeOnTap: false disables the viewer', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: GymAvatar(
              initials: 'AB',
              imageBytes: _png,
              enlargeOnTap: false,
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byType(GymAvatar));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.byType(BackdropFilter), findsNothing);
  });
}
