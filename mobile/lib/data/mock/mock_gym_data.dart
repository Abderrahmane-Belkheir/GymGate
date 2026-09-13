import '../models.dart';

/// Static, realistic seed data for the UI-only phase. Member names, phones and
/// plan labels mirror the finalized desktop screens. Dates are computed relative
/// to [reference] so membership statuses stay meaningful whenever the app runs.
class MockGymData {
  MockGymData({DateTime? reference})
      : reference = reference ?? DateTime.now() {
    _build();
  }

  final DateTime reference;

  late final List<Plan> plans;
  late final List<Seance> seances;
  late final List<Member> members;
  late final List<Payment> payments;
  late final List<AttendanceEntry> attendance;

  DateTime _rel(int days) {
    final base = DateTime(reference.year, reference.month, reference.day);
    return base.add(Duration(days: days));
  }

  void _build() {
    // Growable — `MockPlansRepository.createPlan` appends to it.
    plans = <Plan>[
      Plan(
        id: 'plan_1fois',
        name: '1 fois',
        priceDzd: 2000,
        visitsPerMonth: 4,
        description: 'One visit per week, billed monthly.',
      ),
      Plan(
        id: 'plan_3fois',
        name: '3 fois',
        priceDzd: 2000,
        visitsPerMonth: 12,
        description: 'Three visits per week, billed monthly.',
      ),
      Plan(
        id: 'plan_open',
        name: 'open',
        priceDzd: 4000,
        visitsPerMonth: 0,
        description: 'Unlimited access.',
      ),
      Plan(
        id: 'plan_plan',
        name: 'plan',
        priceDzd: 3000,
        visitsPerMonth: 0,
        description: 'Unlimited access, standard rate.',
      ),
    ];

    // Growable — `MockSeanceRepository.updatePrice` mutates it in place.
    seances = <Seance>[
      Seance(id: 'seance_std', priceDzd: 150),
      Seance(id: 'seance_cardio', priceDzd: 200, cardio: true),
    ];

    final onefois = plans[0];
    final threefois = plans[1];
    final open = plans[2];

    members = [
      Member(
        id: 'm_01',
        firstName: 'Boulbina',
        lastName: 'Ahmed',
        gender: Gender.male,
        phone: '44444',
        plan: open,
        membershipStart: _rel(-31),
        membershipEnd: _rel(20),
        createdAt: _rel(-31),
      ),
      // Renewed today: existing record (created weeks ago), start moved to today.
      Member(
        id: 'm_02',
        firstName: 'Abdouuuu',
        lastName: 'Mimi',
        gender: Gender.male,
        phone: '100000',
        plan: threefois,
        membershipStart: _rel(0),
        membershipEnd: _rel(30),
        createdAt: _rel(-48),
      ),
      Member(
        id: 'm_03',
        firstName: 'Abdoi',
        lastName: 'Belkheir',
        gender: Gender.male,
        phone: '05555',
        plan: open,
        membershipStart: _rel(-10),
        membershipEnd: _rel(20),
        createdAt: _rel(-10),
      ),
      Member(
        id: 'm_04',
        firstName: 'Ayoub',
        lastName: 'Belkheir',
        gender: Gender.male,
        phone: '0553333',
        plan: open,
        membershipStart: _rel(-5),
        membershipEnd: _rel(20),
        createdAt: _rel(-5),
      ),
      Member(
        id: 'm_05',
        firstName: 'Reda',
        lastName: 'Belkheir',
        gender: Gender.male,
        phone: '08848484',
        plan: open,
        membershipStart: _rel(-3),
        membershipEnd: _rel(20),
        createdAt: _rel(-3),
      ),
      Member(
        id: 'm_06',
        firstName: 'Abderrahmane',
        lastName: 'Belkheir',
        gender: Gender.male,
        phone: '0553702233',
        plan: threefois,
        membershipStart: _rel(-24),
        membershipEnd: _rel(6),
        remainingDays: 0, // 0 days left + checks in today -> "Expired today"
        createdAt: _rel(-24),
      ),
      Member(
        id: 'm_07',
        firstName: 'Halland',
        lastName: 'Erling',
        gender: Gender.male,
        phone: '3333',
        plan: threefois,
        membershipStart: _rel(-19),
        membershipEnd: _rel(11),
        createdAt: _rel(-19),
      ),
      Member(
        id: 'm_08',
        firstName: 'Mbappe',
        lastName: 'Kylian',
        gender: Gender.male,
        phone: '005553',
        plan: onefois,
        membershipStart: _rel(-29),
        membershipEnd: _rel(0), // 1 fois, 0 days left -> "Expiring today"
        createdAt: _rel(-29),
      ),
      Member(
        id: 'm_09',
        firstName: 'Messi',
        lastName: 'Lionel',
        gender: Gender.male,
        phone: '010101',
        plan: threefois,
        membershipStart: _rel(-21),
        membershipEnd: _rel(9),
        createdAt: _rel(-21),
      ),
      Member(
        id: 'm_10',
        firstName: 'Ronaldo',
        lastName: 'Cristiano',
        gender: Gender.male,
        phone: '077777',
        plan: open,
        membershipStart: _rel(-40),
        membershipEnd: _rel(20),
        createdAt: _rel(-40),
      ),
      Member(
        id: 'm_11',
        firstName: 'Lina',
        lastName: 'Haddad',
        gender: Gender.female,
        phone: '0661 98 76 54',
        plan: threefois,
        membershipStart: _rel(-27),
        membershipEnd: _rel(3),
        createdAt: _rel(-27),
      ),
      Member(
        id: 'm_12',
        firstName: 'Sara',
        lastName: 'Meziane',
        gender: Gender.female,
        phone: '0555 33 44 55',
        plan: threefois,
        membershipStart: _rel(-32),
        membershipEnd: _rel(-2),
        createdAt: _rel(-32),
      ),
      Member(
        id: 'm_13',
        firstName: 'Nour',
        lastName: 'Slimani',
        gender: Gender.female,
        phone: '0778 10 20 30',
        plan: open,
        membershipStart: _rel(-15),
        membershipEnd: _rel(20),
        createdAt: _rel(-15),
      ),
      // New today: record created today, membership starts today.
      Member(
        id: 'm_14',
        firstName: 'Imane',
        lastName: 'Toumi',
        gender: Gender.female,
        phone: '0662 44 55 66',
        plan: onefois,
        membershipStart: _rel(0),
        membershipEnd: _rel(30),
        createdAt: _rel(0),
      ),
    ];

    final today = DateTime(reference.year, reference.month, reference.day);
    DateTime at(int hour, int minute) =>
        today.add(Duration(hours: hour, minutes: minute));

    attendance = [
      AttendanceEntry(
        id: 'a_01',
        memberId: 'm_06',
        memberName: 'Abderrahmane Belkheir',
        checkIn: at(18, 50),
      ),
      AttendanceEntry(
        id: 'a_02',
        memberId: 'm_13',
        memberName: 'Nour Slimani',
        checkIn: at(18, 20),
      ),
      AttendanceEntry(
        id: 'a_03',
        memberId: 'm_09',
        memberName: 'Messi Lionel',
        checkIn: at(17, 45),
      ),
      AttendanceEntry(
        id: 'a_04',
        memberId: 'm_01',
        memberName: 'Boulbina Ahmed',
        checkIn: at(17, 05),
        checkOut: at(18, 30),
      ),
      AttendanceEntry(
        id: 'a_05',
        memberId: 'm_07',
        memberName: 'Halland Erling',
        checkIn: at(16, 10),
        checkOut: at(17, 25),
      ),
      AttendanceEntry(
        id: 'a_06',
        memberId: 'm_14',
        memberName: 'Imane Toumi',
        checkIn: at(9, 15),
        checkOut: at(10, 30),
      ),
    ];

    payments = [
      Payment(
        id: 'p_01',
        memberId: 'm_09',
        memberName: 'Messi Lionel',
        amountDzd: 2000,
        date: at(18, 30),
        method: PaymentMethod.cash,
        planName: '3 fois',
      ),
      Payment(
        id: 'p_02',
        memberId: 'm_13',
        memberName: 'Nour Slimani',
        amountDzd: 4000,
        date: at(12, 05),
        method: PaymentMethod.card,
        planName: 'open',
      ),
      Payment(
        id: 'p_03',
        memberId: 'm_14',
        memberName: 'Imane Toumi',
        amountDzd: 2000,
        date: at(10, 40),
        method: PaymentMethod.cash,
        planName: '1 fois',
      ),
      Payment(
        id: 'p_04',
        memberId: 'm_01',
        memberName: 'Boulbina Ahmed',
        amountDzd: 4000,
        date: _rel(-1),
        method: PaymentMethod.transfer,
        planName: 'open',
      ),
      // Single-session ("walk-in") payment: no member, no plan — a `seance`
      // rate instead.
      Payment(
        id: 'p_04b',
        memberId: '',
        memberName: '',
        amountDzd: 200,
        date: _rel(-1).add(const Duration(hours: 11)),
        method: PaymentMethod.cash,
        seance: const Seance(
          id: 'seance_cardio',
          priceDzd: 200,
          cardio: true,
          restrictedTo: Gender.female,
        ),
      ),
      Payment(
        id: 'p_05',
        memberId: 'm_07',
        memberName: 'Halland Erling',
        amountDzd: 2000,
        date: _rel(-2),
        method: PaymentMethod.card,
        planName: '3 fois',
      ),
      Payment(
        id: 'p_06',
        memberId: 'm_10',
        memberName: 'Ronaldo Cristiano',
        amountDzd: 4000,
        date: _rel(-4),
        method: PaymentMethod.cash,
        planName: 'open',
      ),
    ];
  }

  DashboardSummary buildSummary() {
    final today = DateTime(reference.year, reference.month, reference.day);

    bool isToday(DateTime d) =>
        d.year == today.year && d.month == today.month && d.day == today.day;

    final checkInsToday = attendance.where((a) => isToday(a.checkIn)).length;
    final inside = attendance.where((a) => a.isInside).length;
    final revenueToday = payments
        .where((p) => isToday(p.date))
        .fold<int>(0, (sum, p) => sum + p.amountDzd);

    final attendedToday = <String>{
      for (final a in attendance)
        if (isToday(a.checkIn)) a.memberId,
    };

    final active = members
        .where((m) => m.statusFrom(reference) == MembershipStatus.active)
        .length;
    final expiredToday = members
        .where((m) =>
            m.isExpiredOn(today, attendedToday: attendedToday.contains(m.id)))
        .length;
    final newToday = members.where((m) => m.isNewOn(today)).length;
    final renewalsToday = members.where((m) => m.isRenewedOn(today)).length;

    return DashboardSummary(
      totalMembers: members.length,
      activeMembers: active,
      expiredToday: expiredToday,
      checkInsToday: checkInsToday,
      currentlyInside: inside,
      revenueTodayDzd: revenueToday,
      newMembersToday: newToday,
      renewalsToday: renewalsToday,
    );
  }

  /// The member behind the most recent check-in **today** (null if nobody has
  /// checked in today).
  Member? spotlightMember() {
    final today = DateTime(reference.year, reference.month, reference.day);
    bool isToday(DateTime d) =>
        d.year == today.year && d.month == today.month && d.day == today.day;

    AttendanceEntry? latest;
    for (final a in attendance) {
      if (!isToday(a.checkIn)) continue;
      if (latest == null || a.checkIn.isAfter(latest.checkIn)) latest = a;
    }
    if (latest == null) return null;

    for (final m in members) {
      if (m.id == latest.memberId) return m;
    }
    return null;
  }
}
