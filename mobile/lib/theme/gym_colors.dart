import 'package:flutter/material.dart';

/// Raw GymGate colour tokens (see UI guidelines §2). Prefer reading these
/// through [GymColors] / the [ColorScheme] rather than referencing the palette
/// directly in feature code.
class GymPalette {
  const GymPalette._();

  // Accent
  static const Color accent = Color(0xFF2F6BFF);
  static const Color accentSoft = Color(0xFFE7EEFF);

  // Surfaces
  static const Color pageTint = Color(0xFFE4E9F1);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color surfaceLow = Color(0xFFF4F6FA);

  // Borders / dividers
  static const Color hairline = Color(0xFFE6E9F0);
  static const Color hairlineStrong = Color(0xFFD9DEE7);

  // Text
  static const Color textPrimary = Color(0xFF1B2437);
  static const Color textMuted = Color(0xFF8993A6);
  static const Color textSlate = Color(0xFF55607A);

  // Chrome (navy)
  static const Color navy = Color(0xFF0E1526);
  static const Color navyHover = Color(0xFF1B2438);
  static const Color navyText = Color(0xFF9AA5B8);
  static const Color navyTextDim = Color(0xFF6B7688);
  static const Color cameraVoid = Color(0xFF131C33);

  // Table neutrals
  static const Color tableHeaderBg = Color(0xFFEFF1F5);
  static const Color rowHover = Color(0xFFEEF0F3);

  // Semantic
  static const Color success = Color(0xFF16A34A);
  static const Color successSoft = Color(0xFFE4F6EA);
  static const Color warning = Color(0xFFF59E0B);
  static const Color warningSoft = Color(0xFFFDF1DE);
  static const Color danger = Color(0xFFDC2626);
  static const Color dangerSoft = Color(0xFFFEE2E2);
  static const Color femaleAccent = Color(0xFFDB2777);

  static const Color shadow = Color(0xFF141E3C);
}

/// Brand tokens that don't map cleanly onto [ColorScheme]. Access via
/// `Theme.of(context).extension<GymColors>()!` or the `context.gymColors`
/// helper below.
@immutable
class GymColors extends ThemeExtension<GymColors> {
  const GymColors({
    required this.pageTint,
    required this.slateLabel,
    required this.rowHover,
    required this.tableHeaderBg,
    required this.hairlineStrong,
    required this.accentSoft,
    required this.navy,
    required this.navyHover,
    required this.navyText,
    required this.navyTextDim,
    required this.cameraVoid,
    required this.success,
    required this.successSoft,
    required this.warning,
    required this.warningSoft,
    required this.danger,
    required this.dangerSoft,
    required this.femaleAccent,
  });

  final Color pageTint;
  final Color slateLabel;
  final Color rowHover;
  final Color tableHeaderBg;
  final Color hairlineStrong;
  final Color accentSoft;
  final Color navy;
  final Color navyHover;
  final Color navyText;
  final Color navyTextDim;
  final Color cameraVoid;
  final Color success;
  final Color successSoft;
  final Color warning;
  final Color warningSoft;
  final Color danger;
  final Color dangerSoft;
  final Color femaleAccent;

  static const GymColors light = GymColors(
    pageTint: GymPalette.pageTint,
    slateLabel: GymPalette.textSlate,
    rowHover: GymPalette.rowHover,
    tableHeaderBg: GymPalette.tableHeaderBg,
    hairlineStrong: GymPalette.hairlineStrong,
    accentSoft: GymPalette.accentSoft,
    navy: GymPalette.navy,
    navyHover: GymPalette.navyHover,
    navyText: GymPalette.navyText,
    navyTextDim: GymPalette.navyTextDim,
    cameraVoid: GymPalette.cameraVoid,
    success: GymPalette.success,
    successSoft: GymPalette.successSoft,
    warning: GymPalette.warning,
    warningSoft: GymPalette.warningSoft,
    danger: GymPalette.danger,
    dangerSoft: GymPalette.dangerSoft,
    femaleAccent: GymPalette.femaleAccent,
  );

  @override
  GymColors copyWith({
    Color? pageTint,
    Color? slateLabel,
    Color? rowHover,
    Color? tableHeaderBg,
    Color? hairlineStrong,
    Color? accentSoft,
    Color? navy,
    Color? navyHover,
    Color? navyText,
    Color? navyTextDim,
    Color? cameraVoid,
    Color? success,
    Color? successSoft,
    Color? warning,
    Color? warningSoft,
    Color? danger,
    Color? dangerSoft,
    Color? femaleAccent,
  }) {
    return GymColors(
      pageTint: pageTint ?? this.pageTint,
      slateLabel: slateLabel ?? this.slateLabel,
      rowHover: rowHover ?? this.rowHover,
      tableHeaderBg: tableHeaderBg ?? this.tableHeaderBg,
      hairlineStrong: hairlineStrong ?? this.hairlineStrong,
      accentSoft: accentSoft ?? this.accentSoft,
      navy: navy ?? this.navy,
      navyHover: navyHover ?? this.navyHover,
      navyText: navyText ?? this.navyText,
      navyTextDim: navyTextDim ?? this.navyTextDim,
      cameraVoid: cameraVoid ?? this.cameraVoid,
      success: success ?? this.success,
      successSoft: successSoft ?? this.successSoft,
      warning: warning ?? this.warning,
      warningSoft: warningSoft ?? this.warningSoft,
      danger: danger ?? this.danger,
      dangerSoft: dangerSoft ?? this.dangerSoft,
      femaleAccent: femaleAccent ?? this.femaleAccent,
    );
  }

  @override
  GymColors lerp(GymColors? other, double t) {
    if (other == null) return this;
    return GymColors(
      pageTint: Color.lerp(pageTint, other.pageTint, t)!,
      slateLabel: Color.lerp(slateLabel, other.slateLabel, t)!,
      rowHover: Color.lerp(rowHover, other.rowHover, t)!,
      tableHeaderBg: Color.lerp(tableHeaderBg, other.tableHeaderBg, t)!,
      hairlineStrong: Color.lerp(hairlineStrong, other.hairlineStrong, t)!,
      accentSoft: Color.lerp(accentSoft, other.accentSoft, t)!,
      navy: Color.lerp(navy, other.navy, t)!,
      navyHover: Color.lerp(navyHover, other.navyHover, t)!,
      navyText: Color.lerp(navyText, other.navyText, t)!,
      navyTextDim: Color.lerp(navyTextDim, other.navyTextDim, t)!,
      cameraVoid: Color.lerp(cameraVoid, other.cameraVoid, t)!,
      success: Color.lerp(success, other.success, t)!,
      successSoft: Color.lerp(successSoft, other.successSoft, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      warningSoft: Color.lerp(warningSoft, other.warningSoft, t)!,
      danger: Color.lerp(danger, other.danger, t)!,
      dangerSoft: Color.lerp(dangerSoft, other.dangerSoft, t)!,
      femaleAccent: Color.lerp(femaleAccent, other.femaleAccent, t)!,
    );
  }
}

extension GymColorsX on BuildContext {
  GymColors get gymColors =>
      Theme.of(this).extension<GymColors>() ?? GymColors.light;
}
