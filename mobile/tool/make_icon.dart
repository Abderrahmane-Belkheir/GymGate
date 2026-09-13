// ignore_for_file: avoid_print
// Generates square app-icon source images from assets/logo.png.
// Run with: dart run tool/make_icon.dart
import 'dart:io';
import 'package:image/image.dart' as img;

void main() {
  final src = img.decodePng(File('assets/logo.png').readAsBytesSync())!;

  // Sample the four corners to guess the logo's background color.
  final corners = [
    src.getPixel(0, 0),
    src.getPixel(src.width - 1, 0),
    src.getPixel(0, src.height - 1),
    src.getPixel(src.width - 1, src.height - 1),
  ];
  num r = 0, g = 0, b = 0, a = 0;
  for (final p in corners) {
    r += p.r;
    g += p.g;
    b += p.b;
    a += p.a;
  }
  final bg = (a / 4) < 8
      ? img.ColorRgb8(255, 255, 255) // transparent corners -> white
      : img.ColorRgb8((r / 4).round(), (g / 4).round(), (b / 4).round());

  // Full-bleed square icon: logo centered on a background-filled square.
  final side = src.width > src.height ? src.width : src.height;
  final full = img.Image(width: side, height: side)..clear(bg);
  img.compositeImage(full, src,
      dstX: (side - src.width) ~/ 2, dstY: (side - src.height) ~/ 2);
  final fullOut = img.copyResize(full, width: 1024, height: 1024);
  File('assets/icon/app_icon.png').writeAsBytesSync(img.encodePng(fullOut));

  // Adaptive foreground: same logo but scaled into the ~66% safe zone,
  // on a transparent canvas.
  final fg = img.Image(width: 1024, height: 1024);
  final target = (1024 * 0.66).round();
  final scale = target /
      (src.width > src.height ? src.width : src.height);
  final resized = img.copyResize(src,
      width: (src.width * scale).round(),
      height: (src.height * scale).round());
  img.compositeImage(fg, resized,
      dstX: (1024 - resized.width) ~/ 2,
      dstY: (1024 - resized.height) ~/ 2);
  File('assets/icon/app_icon_foreground.png')
      .writeAsBytesSync(img.encodePng(fg));

  print('bg color: #${bg.r.toInt().toRadixString(16).padLeft(2, '0')}'
      '${bg.g.toInt().toRadixString(16).padLeft(2, '0')}'
      '${bg.b.toInt().toRadixString(16).padLeft(2, '0')}');
}
