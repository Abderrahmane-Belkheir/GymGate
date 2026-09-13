import 'package:flutter/material.dart';

import '../theme/gym_colors.dart';
import '../theme/gym_tokens.dart';

enum GymButtonVariant { primary, secondary, danger }

/// Pill-padded button, radius 12, icon + label with an 8px gap.
/// `primary` = solid blue with a blue glow; `secondary` = white with a hairline
/// border; `danger` = white with a pink border.
class GymPillButton extends StatelessWidget {
  const GymPillButton({
    super.key,
    required this.label,
    this.icon,
    this.onPressed,
    this.variant = GymButtonVariant.secondary,
    this.expand = false,
  });

  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;
  final GymButtonVariant variant;
  final bool expand;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final enabled = onPressed != null;

    final (Color bg, Color fg, Color border, List<BoxShadow> shadow) =
        switch (variant) {
      GymButtonVariant.primary => (
          GymPalette.accent,
          Colors.white,
          GymPalette.accent,
          enabled ? GymShadows.primaryButton : const <BoxShadow>[],
        ),
      GymButtonVariant.secondary => (
          Colors.white,
          GymPalette.textPrimary,
          GymPalette.hairline,
          const <BoxShadow>[],
        ),
      GymButtonVariant.danger => (
          Colors.white,
          GymPalette.danger,
          const Color(0xFFF3C9D5),
          const <BoxShadow>[],
        ),
    };

    final child = Row(
      mainAxisSize: expand ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (icon != null) ...[
          Icon(icon, size: 16, color: fg),
          const SizedBox(width: GymSpacing.sm),
        ],
        Text(
          label,
          style: theme.textTheme.labelLarge?.copyWith(
            color: fg,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );

    return Opacity(
      opacity: enabled ? 1 : 0.5,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(GymRadii.card),
          border: Border.all(color: border),
          boxShadow: shadow,
        ),
        child: Material(
          type: MaterialType.transparency,
          child: InkWell(
            onTap: onPressed,
            borderRadius: BorderRadius.circular(GymRadii.card),
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: GymSpacing.lg,
                vertical: 12,
              ),
              child: child,
            ),
          ),
        ),
      ),
    );
  }
}
