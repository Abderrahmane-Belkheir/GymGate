import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';

/// Plays the short chime that accompanies an in-app notification banner
/// (`assets/sounds/notification.wav`). Best-effort: any failure — no audio
/// device, the plugin missing in a test — is swallowed so the UI never breaks.
class NotificationSound {
  NotificationSound._();

  static AudioPlayer? _player;
  static final AssetSource _source = AssetSource('sounds/notification.wav');

  /// Restart the chime from the beginning (a burst of banners plays one chime
  /// per banner, not overlapping copies).
  static Future<void> play() async {
    try {
      var player = _player;
      if (player == null) {
        player = AudioPlayer();
        await player.setReleaseMode(ReleaseMode.stop);
        _player = player;
      }
      await player.stop();
      await player.play(_source);
    } catch (e) {
      debugPrint('NotificationSound: could not play — $e');
    }
  }
}
