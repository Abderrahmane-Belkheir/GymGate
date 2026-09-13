import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/models.dart';
import 'package:gymgate_app/data/supabase/row_mappers.dart';

void main() {
  group('bytesFromHex', () {
    test('decodes a \\x-prefixed PostgREST bytea string', () {
      final bytes = bytesFromHex(r'\xffd8ffe000');
      expect(bytes, isNotNull);
      expect(bytes, [0xff, 0xd8, 0xff, 0xe0, 0x00]);
    });

    test('null / empty / malformed → null', () {
      expect(bytesFromHex(null), isNull);
      expect(bytesFromHex(''), isNull);
      expect(bytesFromHex(42), isNull);
      expect(bytesFromHex(r'\xfff'), isNull); // odd length
      expect(bytesFromHex(r'\xzz'), isNull); // not hex
    });
  });

  group('seanceFromRow', () {
    test('maps price / cardio flag / gender scope', () {
      final s = seanceFromRow({
        'id': 4,
        'user_id': 'u1',
        'price': 200,
        'sexe': 'MALE',
        'cardio': 1,
      });
      expect(s.id, '4');
      expect(s.priceDzd, 200);
      expect(s.cardio, isTrue);
      expect(s.restrictedTo, Gender.male);
    });

    test('cardio 0 / null price / null sexe → sane defaults', () {
      final s = seanceFromRow({'id': 5, 'cardio': 0, 'price': null, 'sexe': null});
      expect(s.priceDzd, 0);
      expect(s.cardio, isFalse);
      expect(s.restrictedTo, isNull);
    });
  });

  group('paymentFromRow — plan vs séance', () {
    Map<String, dynamic> base(Map<String, dynamic> extra) => {
          'id': 9,
          'member_id': 3,
          'amount': 200,
          'payment_date': '2026-09-10T11:00:00',
          'member': {'first_name': 'Lina', 'last_name': 'Haddad'},
          ...extra,
        };

    test('membership payment → planName set, seance null', () {
      final p = paymentFromRow(base({
        'plan_id': 2,
        'plan': {'name': '3 fois'},
      }));
      expect(p.planName, '3 fois');
      expect(p.seance, isNull);
    });

    test('single-session payment → seance decoded from an embedded object', () {
      final p = paymentFromRow(base({
        'seance_id': 4,
        'seance': {'id': 4, 'price': 200, 'sexe': 'FEMALE', 'cardio': 1},
      }));
      expect(p.planName, isNull);
      expect(p.seance, isNotNull);
      expect(p.seance!.priceDzd, 200);
      expect(p.seance!.cardio, isTrue);
      expect(p.seance!.restrictedTo, Gender.female);
      expect(p.amountDzd, 200); // amount is unchanged by the join
    });

    test('séance stitched from seancesById by seance_id (no FK embed)', () {
      final p = paymentFromRow(
        {
          'id': 35,
          'member_id': null, // walk-in: no member
          'amount': 150,
          'payment_date': '2026-09-09T13:31:50',
          'member': null,
          'plan': null,
          'seance_id': 2,
        },
        seancesById: {
          '2': const Seance(id: '2', priceDzd: 150, restrictedTo: Gender.male),
        },
      );
      expect(p.memberId, '');
      expect(p.memberName, '');
      expect(p.planName, isNull);
      expect(p.seance?.priceDzd, 150);
      expect(p.seance?.cardio, isFalse);
      expect(p.seance?.restrictedTo, Gender.male);
    });

    test('neither embed present → both null (LEFT join misses)', () {
      final p = paymentFromRow(base(const {}));
      expect(p.planName, isNull);
      expect(p.seance, isNull);
    });
  });

  group('memberFromRow — member_photos LEFT join', () {
    Map<String, dynamic> base(Object? photo) => {
          'id': 2,
          'first_name': 'Meriem',
          'last_name': 'Belkheir',
          'sexe': 'FEMALE',
          'phone_number': '0',
          'photo': photo,
        };

    test('embedded photo object → decoded bytes', () {
      final m = memberFromRow(base({'image': r'\xffd8ffe0'}));
      expect(m.photoBytes, [0xff, 0xd8, 0xff, 0xe0]);
    });

    test('no photo (LEFT join miss) → null', () {
      expect(memberFromRow(base(null)).photoBytes, isNull);
    });

    test('tolerates a one-element list', () {
      final m = memberFromRow(base([
        {'image': r'\xdeadbeef'}
      ]));
      expect(m.photoBytes, [0xde, 0xad, 0xbe, 0xef]);
    });
  });
}
