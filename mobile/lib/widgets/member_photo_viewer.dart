import 'dart:typed_data';
import 'dart:ui';

import 'package:flutter/material.dart';

/// Opens [bytes] as a large, rounded photo over a blurred + dimmed backdrop —
/// the social-media "tap the avatar to enlarge" pattern. Tap anywhere or the
/// close button to dismiss.
Future<void> showMemberPhoto(BuildContext context, Uint8List bytes) {
  return Navigator.of(context, rootNavigator: true).push(
    PageRouteBuilder<void>(
      opaque: false,
      barrierColor: Colors.transparent,
      barrierDismissible: true,
      transitionDuration: const Duration(milliseconds: 220),
      reverseTransitionDuration: const Duration(milliseconds: 160),
      pageBuilder: (context, animation, _) =>
          _MemberPhotoViewer(bytes: bytes, animation: animation),
    ),
  );
}

class _MemberPhotoViewer extends StatelessWidget {
  const _MemberPhotoViewer({required this.bytes, required this.animation});

  final Uint8List bytes;
  final Animation<double> animation;

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final curved = CurvedAnimation(parent: animation, curve: Curves.easeOut);

    void dismiss() => Navigator.of(context).maybePop();

    return GestureDetector(
      onTap: dismiss,
      child: Stack(
        children: [
          Positioned.fill(
            child: AnimatedBuilder(
              animation: curved,
              builder: (context, _) => BackdropFilter(
                filter: ImageFilter.blur(
                  sigmaX: 24 * curved.value,
                  sigmaY: 24 * curved.value,
                ),
                child: ColoredBox(
                  color: Colors.black.withValues(alpha: 0.55 * curved.value),
                ),
              ),
            ),
          ),
          Center(
            child: FadeTransition(
              opacity: curved,
              child: ScaleTransition(
                scale: Tween<double>(begin: 0.9, end: 1).animate(curved),
                child: Container(
                  margin: const EdgeInsets.all(24),
                  constraints: BoxConstraints(
                    maxWidth: media.size.width * 0.9,
                    maxHeight: media.size.height * 0.78,
                  ),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(28),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x73000000),
                        blurRadius: 44,
                        offset: Offset(0, 14),
                      ),
                    ],
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(28),
                    child: Image.memory(
                      bytes,
                      fit: BoxFit.contain,
                      errorBuilder: (_, _, _) => const SizedBox(
                        width: 220,
                        height: 220,
                        child: ColoredBox(
                          color: Color(0xFF1B2437),
                          child: Icon(Icons.broken_image_outlined,
                              color: Colors.white38, size: 40),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
          Positioned(
            top: media.padding.top + 8,
            right: 8,
            child: FadeTransition(
              opacity: curved,
              child: IconButton(
                icon: const Icon(Icons.close_rounded, color: Colors.white),
                style: IconButton.styleFrom(
                  backgroundColor: Colors.black.withValues(alpha: 0.35),
                ),
                onPressed: dismiss,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
