import 'package:flutter/material.dart';

import '../theme/gym_colors.dart';

/// Transparent circular icon button (row overflow "⋯", header actions).
/// Hover/press → faint grey fill.
class GymGhostIconButton extends StatelessWidget {
  const GymGhostIconButton({
    super.key,
    required this.icon,
    this.onPressed,
    this.tooltip,
    this.size = 32,
    this.color = GymPalette.textSlate,
  });

  final IconData icon;
  final VoidCallback? onPressed;
  final String? tooltip;
  final double size;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final button = SizedBox(
      width: size,
      height: size,
      child: Material(
        color: Colors.transparent,
        shape: const CircleBorder(),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onPressed,
          hoverColor: GymPalette.rowHover,
          child: Icon(icon, size: 18, color: color),
        ),
      ),
    );

    if (tooltip == null) return button;
    return Tooltip(message: tooltip!, child: button);
  }
}
