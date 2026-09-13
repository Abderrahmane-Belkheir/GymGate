import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:gymgate_app/data/mock/mock_repositories.dart';
import 'package:gymgate_app/data/repository_scope.dart';
import 'package:gymgate_app/i18n/app_strings.dart';
import 'package:gymgate_app/i18n/locale_controller.dart';
import 'package:gymgate_app/navigation/app_shell.dart';
import 'package:gymgate_app/theme/gym_theme.dart';

/// Reproduces `app.dart`'s structure: the language-reactive part lives in
/// `MaterialApp.builder`, so the MaterialApp / Navigator / Overlay stay put when
/// the language changes. Guards against the "everything freezes after switching
/// language" regression.
void main() {
  Widget appWith(LocaleController controller, AppTranslations translations) {
    return LocaleScope(
      controller: controller,
      child: RepositoryScope(
        repositories: buildMockRepositories(),
        child: MaterialApp(
          theme: buildGymTheme(),
          builder: (context, child) => ListenableBuilder(
            listenable: controller,
            builder: (context, _) {
              final strings =
                  translations.forLanguage(controller.languageCode);
              return StringsScope(
                strings: strings,
                child: Directionality(
                  textDirection: strings.isRtl
                      ? TextDirection.rtl
                      : TextDirection.ltr,
                  child: child!,
                ),
              );
            },
          ),
          home: const AppShell(),
        ),
      ),
    );
  }

  Future<void> nudge(WidgetTester tester) async {
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
  }

  testWidgets('switching language re-translates and leaves the app responsive',
      (tester) async {
    final translations = await AppTranslations.loadAll();
    final controller = LocaleController('en');

    await tester.pumpWidget(appWith(controller, translations));
    await tester.pump(const Duration(seconds: 1)); // let mock Home resolve

    expect(find.text('Overview'), findsWidgets); // app-bar title, English

    // Pick French from the app-bar language menu.
    await tester.tap(find.byIcon(Icons.translate_rounded));
    await nudge(tester);
    await tester.tap(find.text('French').last);
    await nudge(tester);

    expect(controller.languageCode, 'fr');
    expect(find.text('Aperçu'), findsWidgets); // "Overview" re-translated

    // The app is NOT frozen: the drawer still opens and shows French labels.
    await tester.tap(find.byIcon(Icons.menu));
    await nudge(tester);
    expect(find.text('Membres'), findsWidgets);
  });
}
