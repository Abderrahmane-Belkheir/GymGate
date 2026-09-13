import 'package:flutter/widgets.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app_strings.dart';

/// Holds the chosen app language and persists it. `main()` restores the saved
/// choice before the first frame; changing it here rebuilds the app.
class LocaleController extends ChangeNotifier {
  LocaleController(this._languageCode);

  static const String _prefsKey = 'app_language';

  String _languageCode;
  String get languageCode => _languageCode;
  Locale get locale => Locale(_languageCode);

  static Future<LocaleController> load() async {
    String code = 'en';
    try {
      final prefs = await SharedPreferences.getInstance();
      final saved = prefs.getString(_prefsKey);
      if (saved != null &&
          AppTranslations.supportedLanguageCodes.contains(saved)) {
        code = saved;
      }
    } catch (_) {
      // No stored preference — fall back to English.
    }
    return LocaleController(code);
  }

  Future<void> setLanguage(String code) async {
    if (code == _languageCode ||
        !AppTranslations.supportedLanguageCodes.contains(code)) {
      return;
    }
    _languageCode = code;
    notifyListeners();
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_prefsKey, code);
    } catch (_) {
      // Persisting is best-effort.
    }
  }
}

/// Exposes the [LocaleController] to the tree; dependents rebuild on change.
class LocaleScope extends InheritedNotifier<LocaleController> {
  const LocaleScope({
    super.key,
    required LocaleController controller,
    required super.child,
  }) : super(notifier: controller);

  static LocaleController of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<LocaleScope>();
    assert(scope != null, 'No LocaleScope in the widget tree');
    return scope!.notifier!;
  }
}
