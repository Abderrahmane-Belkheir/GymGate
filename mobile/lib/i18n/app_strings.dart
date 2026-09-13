import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter/widgets.dart';

/// Loaded translations for every supported language, parsed once at startup from
/// the `.properties` files in `i18n/` (the same files the desktop app uses).
class AppTranslations {
  const AppTranslations(this._byLanguage);

  final Map<String, Map<String, String>> _byLanguage;

  static const List<String> supportedLanguageCodes = ['en', 'fr', 'ar'];

  static Future<AppTranslations> loadAll() async {
    final byLanguage = <String, Map<String, String>>{};
    for (final code in supportedLanguageCodes) {
      try {
        final raw = await rootBundle.loadString('i18n/messages_$code.properties');
        byLanguage[code] = _parse(raw);
      } catch (_) {
        byLanguage[code] = const {};
      }
    }
    return AppTranslations(byLanguage);
  }

  AppStrings forLanguage(String code) => AppStrings(
        code,
        _byLanguage[code] ?? const {},
        _byLanguage['en'] ?? const {},
      );

  /// Parses a Java-style `key=value` properties file. `#` / `!` comment lines and
  /// blank lines are skipped; the value keeps everything after the first `=`
  /// (some entries, like `days= days`, carry an intentional leading space).
  static Map<String, String> _parse(String raw) {
    final map = <String, String>{};
    for (final line in const LineSplitter().convert(raw)) {
      final trimmed = line.trimLeft();
      if (trimmed.isEmpty || trimmed.startsWith('#') || trimmed.startsWith('!')) {
        continue;
      }
      final eq = line.indexOf('=');
      if (eq <= 0) continue;
      map[line.substring(0, eq).trim()] = line.substring(eq + 1);
    }
    return map;
  }
}

/// The translation table for the active language. Look-ups fall back to English,
/// then to the key itself, so a missing entry degrades gracefully.
@immutable
class AppStrings {
  const AppStrings(this.languageCode, this._values, this._fallback);

  final String languageCode;
  final Map<String, String> _values;
  final Map<String, String> _fallback;

  bool get isRtl => languageCode == 'ar';

  String t(String key) => _values[key] ?? _fallback[key] ?? key;

  /// `12 days` / `1 day` — count + the localised (space-trimmed) unit.
  String days(int n) => '$n ${t(n.abs() == 1 ? 'day' : 'days').trim()}';

  static AppStrings of(BuildContext context) => StringsScope.of(context);
}

/// Makes the active [AppStrings] available to the widget tree; dependents rebuild
/// when the language changes.
class StringsScope extends InheritedWidget {
  const StringsScope({
    super.key,
    required this.strings,
    required super.child,
  });

  final AppStrings strings;

  static AppStrings of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<StringsScope>();
    assert(scope != null, 'No StringsScope in the widget tree');
    return scope!.strings;
  }

  @override
  bool updateShouldNotify(StringsScope oldWidget) =>
      oldWidget.strings != strings;
}

extension AppStringsX on BuildContext {
  /// Shorthand: `context.s.t('Members')`.
  AppStrings get s => AppStrings.of(this);
}
