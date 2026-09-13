import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'app.dart';
import 'config/supabase_config.dart';
import 'data/supabase/supabase_session.dart';
import 'i18n/app_strings.dart';
import 'i18n/locale_controller.dart';
import 'services/fcm_token_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  try {
    await Firebase.initializeApp();
  } catch (e, s) {
    debugPrint('Firebase.initializeApp failed: $e\n$s');
  }

  try {
    await Supabase.initialize(
      url: SupabaseConfig.url,
      publishableKey: SupabaseConfig.publishableKey,
    );

    // Restore the saved JWT (or sign in with the configured email/password if
    // there isn't one, or it has expired) and keep it persisted from here on.
    await SupabaseSession.instance.init();
  } catch (e, s) {
    debugPrint('Supabase.initialize failed: $e\n$s');
  }

  final translations = await AppTranslations.loadAll();
  final localeController = await LocaleController.load();

  runApp(GymGateApp(
    translations: translations,
    localeController: localeController,
  ));

  // Fire-and-forget: never let token work block or crash the UI.
  FcmTokenService().init();
}
