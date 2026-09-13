import 'package:flutter/material.dart';

import '../features/attendance/attendance_screen.dart';
import '../features/home/home_screen.dart';
import '../features/members/members_screen.dart';
import '../features/payments/payments_screen.dart';
import '../features/plans/plans_screen.dart';

/// A single primary section reachable from the side drawer.
class AppDestination {
  const AppDestination({
    required this.labelKey,
    required this.icon,
    required this.builder,
  });

  /// i18n key (also the English text) — resolve with `context.s.t(labelKey)`.
  final String labelKey;
  final IconData icon;
  final WidgetBuilder builder;
}

/// Drawer order. Home is index 0 and opens by default.
const List<AppDestination> kAppDestinations = [
  AppDestination(
    labelKey: 'Home',
    icon: Icons.grid_view_outlined,
    builder: _home,
  ),
  AppDestination(
    labelKey: 'Members',
    icon: Icons.people_alt_outlined,
    builder: _members,
  ),
  AppDestination(
    labelKey: 'Plans',
    icon: Icons.card_membership_outlined,
    builder: _plans,
  ),
  AppDestination(
    labelKey: 'Attendance',
    icon: Icons.how_to_reg_outlined,
    builder: _attendance,
  ),
  AppDestination(
    labelKey: 'Payments',
    icon: Icons.payments_outlined,
    builder: _payments,
  ),
];

Widget _home(BuildContext _) => const HomeScreen();
Widget _members(BuildContext _) => const MembersScreen();
Widget _plans(BuildContext _) => const PlansScreen();
Widget _attendance(BuildContext _) => const AttendanceScreen();
Widget _payments(BuildContext _) => const PaymentsScreen();
