import 'package:flutter/material.dart';

import '../theme/gym_colors.dart';
import '../theme/gym_tokens.dart';

/// Selected-state treatment for a segment. Identity toggles (gender) use a
/// confident solid fill; data/status toggles use a soft tint that reuses the
/// row status colours.
enum GymSegmentStyle { solidBlue, solidPink, softGreen, softRed }

class GymSegment<T> {
  const GymSegment({
    required this.value,
    required this.label,
    required this.style,
  });

  final T value;
  final String label;
  final GymSegmentStyle style;
}

/// A pill-shaped group of mutually exclusive options — neutral at rest, styled
/// per [GymSegmentStyle] when selected. Exactly one option is always selected.
class GymSegmentedToggle<T> extends StatelessWidget {
  const GymSegmentedToggle({
    super.key,
    required this.segments,
    required this.value,
    required this.onChanged,
    this.expanded = false,
  });

  final List<GymSegment<T>> segments;
  final T value;
  final ValueChanged<T> onChanged;

  /// When true the group fills its width and every option is the same width
  /// (a top-level view switch); when false each option hugs its label.
  final bool expanded;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: GymPalette.pageTint,
        borderRadius: BorderRadius.circular(GymRadii.toggleGroup),
        border: Border.all(color: GymPalette.hairline),
      ),
      child: Row(
        mainAxisSize: expanded ? MainAxisSize.max : MainAxisSize.min,
        children: [
          for (final segment in segments)
            if (expanded)
              Expanded(
                child: _Option<T>(
                  segment: segment,
                  selected: segment.value == value,
                  expanded: true,
                  onTap: () => onChanged(segment.value),
                ),
              )
            else
              _Option<T>(
                segment: segment,
                selected: segment.value == value,
                onTap: () => onChanged(segment.value),
              ),
        ],
      ),
    );
  }
}

class _Option<T> extends StatelessWidget {
  const _Option({
    required this.segment,
    required this.selected,
    required this.onTap,
    this.expanded = false,
  });

  final GymSegment<T> segment;
  final bool selected;
  final VoidCallback onTap;
  final bool expanded;

  @override
  Widget build(BuildContext context) {
    final c = context.gymColors;

    Color bg = Colors.transparent;
    Color fg = GymPalette.textMuted;
    List<BoxShadow> shadow = const [];

    if (selected) {
      switch (segment.style) {
        case GymSegmentStyle.solidBlue:
          bg = GymPalette.accent;
          fg = Colors.white;
          shadow = const [
            BoxShadow(
                color: Color(0x402F6BFF), blurRadius: 8, offset: Offset(0, 2)),
          ];
        case GymSegmentStyle.solidPink:
          bg = c.femaleAccent;
          fg = Colors.white;
          shadow = [
            BoxShadow(
                color: c.femaleAccent.withValues(alpha: 0.25),
                blurRadius: 8,
                offset: const Offset(0, 2)),
          ];
        case GymSegmentStyle.softGreen:
          bg = c.successSoft;
          fg = c.success;
        case GymSegmentStyle.softRed:
          bg = c.dangerSoft;
          fg = c.danger;
      }
    }

    return Material(
      type: MaterialType.transparency,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(GymRadii.toggleOption),
        child: AnimatedContainer(
          duration: GymDurations.micro,
          curve: Curves.easeOut,
          alignment: expanded ? Alignment.center : null,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(GymRadii.toggleOption),
            boxShadow: shadow,
          ),
          child: Text(
            segment.label,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: fg,
            ),
          ),
        ),
      ),
    );
  }
}

/// The ~22-tall hairline that separates two adjacent toggle groups so they read
/// as separate dimensions rather than one long button row.
class GymToggleDivider extends StatelessWidget {
  const GymToggleDivider({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 1,
      height: 22,
      margin: const EdgeInsets.symmetric(horizontal: GymSpacing.md),
      color: GymPalette.hairlineStrong,
    );
  }
}
