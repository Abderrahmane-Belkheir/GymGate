import 'package:flutter/material.dart';

/// Corner radii. Surfaces use 12; pills/badges/avatars are fully round.
class GymRadii {
  const GymRadii._();

  static const double card = 12;
  static const double input = 12;
  static const double toggleGroup = 10;
  static const double toggleOption = 8;
  static const double priceBlock = 14;
  static const double dialog = 20;
  static const double menu = 14;
  static const double accentBar = 3;
  static const double pill = 999;
}

/// Spacing scale: 4 · 8 · 12 · 16 · 20 · 24 · 28.
class GymSpacing {
  const GymSpacing._();

  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 20;
  static const double xl = 24;
  static const double xxl = 28;

  /// Mobile page padding (desktop uses ~28 horizontal).
  static const double pageH = 16;
  static const double pageV = 20;

  /// Gap between the stacked surfaces of a screen (header -> controls -> content).
  static const double sectionGap = 12;

  static const double rowHeight = 64;
  static const double cardPad = 16;
}

class GymDurations {
  const GymDurations._();

  static const Duration micro = Duration(milliseconds: 120);
  static const Duration standard = Duration(milliseconds: 200);
}

/// Soft, low-spread, slightly-downward elevation. Three levels only.
class GymShadows {
  const GymShadows._();

  static const List<BoxShadow> card = [
    BoxShadow(color: Color(0x0D141E3C), blurRadius: 10, offset: Offset(0, 3)),
  ];

  static const List<BoxShadow> header = [
    BoxShadow(color: Color(0x0A141E3C), blurRadius: 8, offset: Offset(0, 2)),
  ];

  static const List<BoxShadow> raised = [
    BoxShadow(color: Color(0x1A141E3C), blurRadius: 16, offset: Offset(0, 4)),
  ];

  static const List<BoxShadow> primaryButton = [
    BoxShadow(color: Color(0x592F6BFF), blurRadius: 12, offset: Offset(0, 4)),
  ];
}
