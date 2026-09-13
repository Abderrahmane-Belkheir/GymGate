import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/i18n/app_strings.dart';

/// Loads the real English `i18n/` strings so widget tests assert against the
/// actual copy (and catch missing keys).
Future<AppStrings> loadEnglishStrings() async {
  TestWidgetsFlutterBinding.ensureInitialized();
  final translations = await AppTranslations.loadAll();
  return translations.forLanguage('en');
}

/// Wraps [child] in a [StringsScope] so `context.s` resolves inside tests.
Widget withStrings(AppStrings strings, Widget child) =>
    StringsScope(strings: strings, child: child);
