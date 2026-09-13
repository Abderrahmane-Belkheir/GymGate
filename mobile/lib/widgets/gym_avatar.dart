import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../theme/gym_colors.dart';
import 'member_photo_viewer.dart';

/// Circular avatar: blue-soft background with blue initials, or a cover-cropped
/// photo when [imageBytes] is provided (decoded `member_photos.image`). When it
/// has a photo, tapping it opens the full photo over a blurred backdrop.
class GymAvatar extends StatelessWidget {
  const GymAvatar({
    super.key,
    required this.initials,
    this.imageBytes,
    this.size = 44,
    this.enlargeOnTap = true,
  });

  final String initials;
  final Uint8List? imageBytes;
  final double size;

  /// Whether tapping a photo avatar opens the enlarged viewer.
  final bool enlargeOnTap;

  @override
  Widget build(BuildContext context) {
    final bytes = imageBytes;
    final hasPhoto = bytes != null && bytes.isNotEmpty;

    Widget avatar = Container(
      width: size,
      height: size,
      clipBehavior: Clip.antiAlias,
      decoration: const BoxDecoration(
        color: GymPalette.accentSoft,
        shape: BoxShape.circle,
      ),
      child: hasPhoto
          ? Image.memory(
              bytes,
              fit: BoxFit.cover,
              gaplessPlayback: true,
              // The photos are shown tiny — decode them down to roughly the
              // display size (allowing for high-DPR screens).
              cacheWidth: (size * 3).round(),
              errorBuilder: (_, _, _) => _initials(),
            )
          : _initials(),
    );

    if (hasPhoto && enlargeOnTap) {
      avatar = GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => showMemberPhoto(context, bytes),
        child: avatar,
      );
    }
    return avatar;
  }

  Widget _initials() => Center(
        child: Text(
          initials,
          style: TextStyle(
            fontSize: size * 0.34,
            fontWeight: FontWeight.w800,
            letterSpacing: -0.3,
            color: GymPalette.accent,
          ),
        ),
      );
}
