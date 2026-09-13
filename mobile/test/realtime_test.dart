import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/realtime/realtime_bus.dart';

void main() {
  group('RealtimeBus', () {
    test('bump advances per-table revisions', () {
      final bus = RealtimeBus();
      expect(bus.revisionOf('members'), 0);
      bus.bump('members');
      bus.bump('members');
      bus.bump('plans');
      expect(bus.revisionOf('members'), 2);
      expect(bus.revisionOf('plans'), 1);
      expect(bus.revisionOfAll(['members', 'plans']), 3);
      expect(bus.revisionOfAll(['attendance']), 0);
    });
  });

  testWidgets('RealtimeReload re-queries only when a watched table changes',
      (tester) async {
    final bus = RealtimeBus();
    var reloads = 0;

    await tester.pumpWidget(
      RealtimeScope(
        bus: bus,
        child: MaterialApp(
          home: _Probe(
            tables: const ['members'],
            onReload: () => reloads++,
          ),
        ),
      ),
    );

    expect(reloads, 0); // first build just records the baseline

    bus.bump('plans'); // not watched
    await tester.pump();
    expect(reloads, 0);

    bus.bump('members'); // watched
    await tester.pump();
    expect(reloads, 1);

    bus.bump('members');
    await tester.pump();
    expect(reloads, 2);
  });

  testWidgets('RealtimeReload is inert without a RealtimeScope', (tester) async {
    var reloads = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: _Probe(tables: const ['members'], onReload: () => reloads++),
      ),
    );
    await tester.pump();
    expect(reloads, 0);
  });
}

class _Probe extends StatefulWidget {
  const _Probe({required this.tables, required this.onReload});

  final List<String> tables;
  final VoidCallback onReload;

  @override
  State<_Probe> createState() => _ProbeState();
}

class _ProbeState extends State<_Probe> with RealtimeReload<_Probe> {
  @override
  List<String> get realtimeTables => widget.tables;

  @override
  void onRealtimeChange() => widget.onReload();

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    watchRealtime(context);
  }

  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
}
