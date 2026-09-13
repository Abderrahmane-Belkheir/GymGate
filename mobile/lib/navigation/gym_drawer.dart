import 'package:flutter/material.dart';

import '../i18n/app_strings.dart';
import '../theme/gym_colors.dart';
import '../theme/gym_tokens.dart';
import 'app_destinations.dart';

/// Deep-navy side drawer — the mobile stand-in for the desktop left sidebar.
/// Not permanently visible; opened from the header menu button.
class GymDrawer extends StatelessWidget {
  const GymDrawer({
    super.key,
    required this.currentIndex,
    required this.onSelect,
  });

  final int currentIndex;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    final padding = MediaQuery.paddingOf(context);

    return Drawer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: EdgeInsets.fromLTRB(
              GymSpacing.lg,
              padding.top + GymSpacing.xl,
              GymSpacing.lg,
              GymSpacing.lg,
            ),
            child: Row(
              children: [
                Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: GymPalette.accent,
                    borderRadius: BorderRadius.circular(GymRadii.card),
                  ),
                  child: const Icon(Icons.fitness_center,
                      color: Colors.white, size: 18),
                ),
                const SizedBox(width: GymSpacing.md),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'GymGate',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -0.2,
                      ),
                    ),
                    Text(
                      context.s.t('Reception'),
                      style: const TextStyle(
                        color: GymPalette.navyText,
                        fontSize: 12,
                        fontWeight: FontWeight.w400,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const Divider(color: GymPalette.navyHover, height: 1),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.symmetric(
                horizontal: GymSpacing.md,
                vertical: GymSpacing.md,
              ),
              children: [
                for (var i = 0; i < kAppDestinations.length; i++)
                  _DrawerItem(
                    destination: kAppDestinations[i],
                    selected: i == currentIndex,
                    onTap: () => onSelect(i),
                  ),
              ],
            ),
          ),
          const Divider(color: GymPalette.navyHover, height: 1),
          Padding(
            padding: EdgeInsets.fromLTRB(
              GymSpacing.lg,
              GymSpacing.md,
              GymSpacing.lg,
              padding.bottom + GymSpacing.lg,
            ),
            child: Row(
              children: [
                const Icon(Icons.logout,
                    size: 15, color: GymPalette.navyTextDim),
                const SizedBox(width: GymSpacing.sm),
                Text(
                  context.s.t('Sign_out'),
                  style: const TextStyle(
                    color: GymPalette.navyTextDim,
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _DrawerItem extends StatelessWidget {
  const _DrawerItem({
    required this.destination,
    required this.selected,
    required this.onTap,
  });

  final AppDestination destination;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final fg = selected ? Colors.white : GymPalette.navyText;
    final label = context.s.t(destination.labelKey);

    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Material(
        color: selected ? GymPalette.accent : Colors.transparent,
        borderRadius: BorderRadius.circular(GymRadii.card),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          hoverColor: GymPalette.navyHover,
          child: Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: GymSpacing.md,
              vertical: 12,
            ),
            child: Row(
              children: [
                Icon(destination.icon, size: 18, color: fg),
                const SizedBox(width: GymSpacing.md),
                Text(
                  label,
                  style: TextStyle(
                    color: fg,
                    fontSize: 14,
                    fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
