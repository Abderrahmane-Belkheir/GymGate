import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/mock/mock_repositories.dart';
import 'package:gymgate_app/data/models.dart';
import 'package:gymgate_app/data/repositories.dart';

/// Locks in the desktop `PaymentDao.find` / `.sumAmount` semantics the Supabase
/// repository mirrors: optional year / month / day filter, newest first,
/// `sumAmount` is 0 when nothing matches.
void main() {
  final reference = DateTime(2026, 8, 29, 19);
  late PaymentsRepository payments;

  setUp(() {
    payments = buildMockRepositories(reference: reference).payments;
  });

  test('no filter returns every payment, newest first', () async {
    final rows = await payments.findPayments();
    expect(rows, isNotEmpty);
    for (var i = 1; i < rows.length; i++) {
      expect(rows[i - 1].date.isAfter(rows[i].date), isTrue);
    }
    expect(
      await payments.sumAmount(),
      rows.fold<int>(0, (s, Payment p) => s + p.amountDzd),
    );
  });

  test('year + month + day filters to that calendar day', () async {
    final rows = await payments.findPayments(year: 2026, month: 8, day: 29);
    expect(
      rows,
      everyElement(predicate<Payment>((p) {
        final d = p.date;
        return d.year == 2026 && d.month == 8 && d.day == 29;
      })),
    );
    expect(await payments.sumAmount(year: 2026, month: 8, day: 29), 8000);
    expect(await payments.sumAmount(year: 2026, month: 8, day: 1), 0);
    expect(await payments.sumAmount(year: 2020), 0);
  });
}
