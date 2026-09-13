import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/core/self_write_registry.dart';

void main() {
  final reg = SelfWriteRegistry.instance;

  test('a marked row is consumed once, then forgotten', () {
    reg.mark('plans', 11);
    expect(reg.consume('plans', 11), isTrue);
    expect(reg.consume('plans', 11), isFalse); // already consumed
  });

  test('int / string ids for the same row match', () {
    reg.mark('plans', 11);
    expect(reg.consume('plans', '11'), isTrue);
  });

  test('an unmarked row is not suppressed', () {
    expect(reg.consume('payments', 999), isFalse);
  });

  test('table is part of the key', () {
    reg.mark('plans', 5);
    expect(reg.consume('payments', 5), isFalse);
    expect(reg.consume('plans', 5), isTrue);
  });

  test('null id is a no-op', () {
    reg.mark('plans', null);
    expect(reg.consume('plans', null), isFalse);
  });
}
