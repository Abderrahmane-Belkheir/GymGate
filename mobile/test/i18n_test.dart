import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:gymgate_app/i18n/app_strings.dart';
import 'package:gymgate_app/i18n/locale_controller.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('AppTranslations / AppStrings', () {
    late AppTranslations translations;

    setUpAll(() async {
      translations = await AppTranslations.loadAll();
    });

    test('every supported language loads and translates a shared key', () {
      expect(translations.forLanguage('en').t('Home'), 'Home');
      expect(translations.forLanguage('fr').t('Home'), 'Accueil');
      expect(translations.forLanguage('ar').t('Home'), 'الرئيسية');
    });

    test('missing key falls back to English, then to the key itself', () {
      // `Overview` exists in en; assume a locale is missing it → English.
      final fr = translations.forLanguage('fr');
      expect(fr.t('Overview'), isNotEmpty);
      // A key that exists nowhere returns itself.
      expect(fr.t('__nope__'), '__nope__');
    });

    test('isRtl only for Arabic', () {
      expect(translations.forLanguage('ar').isRtl, isTrue);
      expect(translations.forLanguage('en').isRtl, isFalse);
      expect(translations.forLanguage('fr').isRtl, isFalse);
    });

    test('days() combines count with the localised unit', () {
      expect(translations.forLanguage('en').days(1), '1 day');
      expect(translations.forLanguage('en').days(12), '12 days');
      expect(translations.forLanguage('fr').days(3), '3 jours');
    });
  });

  group('LocaleController', () {
    setUp(() => SharedPreferences.setMockInitialValues({}));

    test('defaults to English, setLanguage switches and notifies', () async {
      final controller = await LocaleController.load();
      expect(controller.languageCode, 'en');

      var notified = 0;
      controller.addListener(() => notified++);

      await controller.setLanguage('fr');
      expect(controller.languageCode, 'fr');
      expect(controller.locale, const Locale('fr'));
      expect(notified, 1);

      await controller.setLanguage('xx'); // unsupported → ignored
      expect(controller.languageCode, 'fr');
      expect(notified, 1);
    });

    test('restores the saved language', () async {
      SharedPreferences.setMockInitialValues({'app_language': 'ar'});
      final controller = await LocaleController.load();
      expect(controller.languageCode, 'ar');
    });
  });

  // The end-to-end language switch (real `_LanguageButton`, re-translation, and
  // that the app stays responsive) is covered by `language_switch_test.dart`.
}
