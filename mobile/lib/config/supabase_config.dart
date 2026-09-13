import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

/// Supabase connection settings.
///
/// The project id and publishable key are public client values (RLS-protected)
/// and are hardcoded below. Only the account credentials used to sign in
/// automatically at startup are loaded from the bundled
/// `config/supabase.properties` file (same Java-style `key=value` format as the
/// `i18n/*.properties` translations).
class SupabaseConfig {
  const SupabaseConfig({required this.email, required this.password});

  /// Project ref / id — the subdomain in `https://<project-id>.supabase.co`.
  static const String projectId = 'fapdvyrtkgwyjjaecpfr';

  /// Public publishable API key (`sb_publishable_...`) — safe to ship in the
  /// client. The legacy anon JWT key also works here.
  static const String publishableKey =
      'sb_publishable_-R3D05EkczCSgRpvGrUNmQ_6AGhO-qf';

  /// Full project URL, derived from [projectId].
  static const String url = 'https://$projectId.supabase.co';

  /// Account email used to sign in at startup.
  final String email;

  /// Account password used to sign in at startup.
  final String password;

  /// Whether both credentials are present so a sign-in can be attempted.
  bool get hasCredentials => email.isNotEmpty && password.isNotEmpty;

  static Future<SupabaseConfig> load() async {
    final raw = await rootBundle.loadString('config/supabase.properties');
    final values = _parse(raw);
    return SupabaseConfig(
      email: values['email'] ?? '',
      password: values['password'] ?? '',
    );
  }

  /// Parses a Java-style `key=value` properties file. `#` / `!` comment lines
  /// and blank lines are skipped; the value keeps everything after the first
  /// `=`, trimmed of surrounding whitespace.
  static Map<String, String> _parse(String raw) {
    final map = <String, String>{};
    for (final line in const LineSplitter().convert(raw)) {
      final trimmed = line.trimLeft();
      if (trimmed.isEmpty || trimmed.startsWith('#') || trimmed.startsWith('!')) {
        continue;
      }
      final eq = line.indexOf('=');
      if (eq <= 0) continue;
      map[line.substring(0, eq).trim()] = line.substring(eq + 1).trim();
    }
    return map;
  }
}
