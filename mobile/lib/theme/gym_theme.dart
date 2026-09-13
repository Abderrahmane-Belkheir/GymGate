import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'gym_colors.dart';
import 'gym_tokens.dart';

/// Builds the single GymGate light theme. The desktop product uses Segoe UI;
/// on mobile we fall back to the platform default sans (closest neutral match)
/// and keep the observed size / weight / colour hierarchy.
ThemeData buildGymTheme() {
  const scheme = ColorScheme(
    brightness: Brightness.light,
    primary: GymPalette.accent,
    onPrimary: Colors.white,
    primaryContainer: GymPalette.accentSoft,
    onPrimaryContainer: GymPalette.accent,
    secondary: GymPalette.success,
    onSecondary: Colors.white,
    secondaryContainer: GymPalette.successSoft,
    onSecondaryContainer: GymPalette.success,
    tertiary: GymPalette.warning,
    onTertiary: Colors.white,
    tertiaryContainer: GymPalette.warningSoft,
    onTertiaryContainer: GymPalette.warning,
    error: GymPalette.danger,
    onError: Colors.white,
    errorContainer: GymPalette.dangerSoft,
    onErrorContainer: GymPalette.danger,
    surface: GymPalette.surface,
    onSurface: GymPalette.textPrimary,
    onSurfaceVariant: GymPalette.textMuted,
    outline: GymPalette.hairline,
    outlineVariant: GymPalette.hairlineStrong,
    surfaceContainerLowest: Colors.white,
    surfaceContainerLow: GymPalette.surfaceLow,
    surfaceContainer: GymPalette.pageTint,
    shadow: GymPalette.shadow,
    inverseSurface: GymPalette.navy,
    onInverseSurface: GymPalette.navyText,
  );

  final textTheme = _buildTextTheme();

  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: GymPalette.pageTint,
    canvasColor: GymPalette.pageTint,
    textTheme: textTheme,
    splashFactory: InkSparkle.splashFactory,
    extensions: const [GymColors.light],
    appBarTheme: const AppBarTheme(
      backgroundColor: GymPalette.pageTint,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
      iconTheme: IconThemeData(color: GymPalette.textPrimary, size: 22),
      titleTextStyle: TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w800,
        letterSpacing: -0.2,
        color: GymPalette.textPrimary,
      ),
      systemOverlayStyle: SystemUiOverlayStyle.dark,
    ),
    dividerTheme: const DividerThemeData(
      color: GymPalette.hairline,
      thickness: 1,
      space: 1,
    ),
    drawerTheme: const DrawerThemeData(
      backgroundColor: GymPalette.navy,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      width: 292,
    ),
    iconTheme: const IconThemeData(color: GymPalette.textPrimary),
    cardTheme: CardThemeData(
      color: Colors.white,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(GymRadii.card),
        side: const BorderSide(color: GymPalette.hairline),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: GymPalette.pageTint,
      isDense: true,
      contentPadding:
          const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
      hintStyle: const TextStyle(
        color: GymPalette.textMuted,
        fontSize: 13,
        fontWeight: FontWeight.w500,
      ),
      border: _inputBorder(GymPalette.hairline),
      enabledBorder: _inputBorder(GymPalette.hairline),
      focusedBorder: _inputBorder(GymPalette.accent),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: GymPalette.navy,
      contentTextStyle: const TextStyle(
        color: Colors.white,
        fontSize: 13,
        fontWeight: FontWeight.w500,
      ),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(GymRadii.card),
      ),
    ),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      showDragHandle: true,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(GymRadii.dialog)),
      ),
    ),
  );
}

OutlineInputBorder _inputBorder(Color color) => OutlineInputBorder(
      borderRadius: BorderRadius.circular(GymRadii.input),
      borderSide: BorderSide(color: color),
    );

TextTheme _buildTextTheme() {
  const primary = GymPalette.textPrimary;
  const muted = GymPalette.textMuted;
  const slate = GymPalette.textSlate;
  const tabular = [FontFeature.tabularFigures()];

  return const TextTheme(
    // Page title
    displaySmall: TextStyle(
      fontSize: 22,
      fontWeight: FontWeight.w800,
      letterSpacing: -0.2,
      color: primary,
      height: 1.15,
    ),
    // Metric / big value / plan price
    headlineMedium: TextStyle(
      fontSize: 26,
      fontWeight: FontWeight.w800,
      letterSpacing: -0.3,
      color: primary,
      fontFeatures: tabular,
      height: 1.1,
    ),
    // Member detail card name
    titleLarge: TextStyle(
      fontSize: 17,
      fontWeight: FontWeight.w800,
      letterSpacing: -0.2,
      color: primary,
    ),
    // Section / panel title
    titleMedium: TextStyle(
      fontSize: 15,
      fontWeight: FontWeight.w700,
      letterSpacing: -0.1,
      color: primary,
    ),
    // Primary list item / member name
    titleSmall: TextStyle(
      fontSize: 14,
      fontWeight: FontWeight.w700,
      letterSpacing: -0.1,
      color: primary,
    ),
    // Body / inputs
    bodyMedium: TextStyle(
      fontSize: 13,
      fontWeight: FontWeight.w500,
      color: primary,
    ),
    // Inline data value
    bodyLarge: TextStyle(
      fontSize: 13,
      fontWeight: FontWeight.w700,
      color: primary,
      fontFeatures: tabular,
    ),
    // Metadata
    bodySmall: TextStyle(
      fontSize: 11,
      fontWeight: FontWeight.w400,
      color: muted,
    ),
    // Button label
    labelLarge: TextStyle(
      fontSize: 13,
      fontWeight: FontWeight.w600,
      color: primary,
    ),
    // Toggle label
    labelMedium: TextStyle(
      fontSize: 12,
      fontWeight: FontWeight.w600,
      color: muted,
    ),
    // Table header label / stat label
    labelSmall: TextStyle(
      fontSize: 11,
      fontWeight: FontWeight.w700,
      letterSpacing: 0.5,
      color: slate,
    ),
  );
}
