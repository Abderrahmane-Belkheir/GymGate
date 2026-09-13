import 'dart:async';
import 'dart:ui' show lerpDouble;

import 'package:flutter/material.dart';

import '../i18n/app_strings.dart';
import '../theme/gym_colors.dart';
import '../theme/gym_tokens.dart';
import 'gym_notification.dart';
import 'notification_center.dart';
import 'notification_sound.dart';

/// Sits above the app content. Shows one banner at a time: it slides down from
/// the top and fades in, holds briefly, then shrinks and glides into the
/// notification bell (whose [bellKey] gives the fly target).
class NotificationBannerHost extends StatefulWidget {
  const NotificationBannerHost({super.key, required this.bellKey});

  final GlobalKey bellKey;

  @override
  State<NotificationBannerHost> createState() => _NotificationBannerHostState();
}

class _NotificationBannerHostState extends State<NotificationBannerHost>
    with TickerProviderStateMixin {
  static const _enterDuration = Duration(milliseconds: 380);
  static const _flyDuration = Duration(milliseconds: 720);
  static const _hold = Duration(milliseconds: 3400);
  // Approximate — only used to seed the fly-to-bell animation's start point.
  static const _cardHeight = 76.0;

  NotificationCenter? _center;
  late final AnimationController _enter =
      AnimationController(vsync: this, duration: _enterDuration);
  late final AnimationController _fly =
      AnimationController(vsync: this, duration: _flyDuration);

  GymNotification? _current;
  Timer? _holdTimer;
  Rect? _bellRect;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final center = NotificationScope.maybeOf(context);
    if (center != null && !identical(center, _center)) {
      _center?.removeListener(_onCenterChanged);
      _center = center..addListener(_onCenterChanged);
      _onCenterChanged();
    }
  }

  @override
  void dispose() {
    _holdTimer?.cancel();
    _center?.removeListener(_onCenterChanged);
    _enter.dispose();
    _fly.dispose();
    super.dispose();
  }

  void _onCenterChanged() {
    if (_current == null && (_center?.pendingBanner != null)) _showNext();
  }

  void _showNext() {
    if (!mounted) return;
    final next = _center?.pendingBanner;
    if (next == null) return;
    setState(() => _current = next);
    NotificationSound.play();
    _fly.value = 0;
    _enter.forward(from: 0);
    _holdTimer?.cancel();
    _holdTimer = Timer(_enterDuration + _hold, _flyToBell);
  }

  void _flyToBell() {
    _holdTimer?.cancel();
    if (_current == null || _fly.value > 0) return;
    _bellRect = _resolveBellRect();
    _fly.forward(from: 0).whenComplete(_finish);
  }

  void _finish() {
    _center?.consumeBanner();
    setState(() => _current = null);
    _enter.value = 0;
    _fly.value = 0;
    if (_center?.pendingBanner != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _onCenterChanged());
    }
  }

  Rect? _resolveBellRect() {
    final box = widget.bellKey.currentContext?.findRenderObject() as RenderBox?;
    if (box == null || !box.hasSize) return null;
    return box.localToGlobal(Offset.zero) & box.size;
  }

  @override
  Widget build(BuildContext context) {
    final notification = _current;
    if (notification == null) return const SizedBox.shrink();

    final media = MediaQuery.of(context);
    final topPad = media.padding.top + 8;
    const sideMargin = 12.0;
    final cardWidth = media.size.width - sideMargin * 2;
    final strings = context.s;

    return Positioned.fill(
      child: AnimatedBuilder(
        animation: Listenable.merge([_enter, _fly]),
        builder: (context, _) {
          final card = _BannerCard(notification: notification, strings: strings);
          final flying = _fly.value > 0;

          if (!flying) {
            final e = Curves.easeOutCubic.transform(_enter.value);
            return Stack(children: [
              Positioned(
                top: topPad - (1 - e) * (_cardHeight + topPad + 24),
                left: sideMargin,
                right: sideMargin,
                child: Opacity(
                  opacity: e.clamp(0.0, 1.0),
                  child: GestureDetector(
                    behavior: HitTestBehavior.opaque,
                    onTap: _flyToBell,
                    onVerticalDragEnd: (d) {
                      if ((d.primaryVelocity ?? 0) < -60) _flyToBell();
                    },
                    child: card,
                  ),
                ),
              ),
            ]);
          }

          final f = Curves.easeInCubic.transform(_fly.value);
          final restCenter = Offset(media.size.width / 2, topPad + _cardHeight / 2);
          final target = _bellRect?.center ??
              Offset(media.size.width - 28, topPad + 8);
          final pos = Offset.lerp(restCenter, target, f)!;
          final scale = lerpDouble(1, 0.06, f)!;

          return Stack(children: [
            Positioned(
              left: pos.dx - cardWidth / 2,
              top: pos.dy - _cardHeight / 2,
              width: cardWidth,
              child: IgnorePointer(
                child: Opacity(
                  opacity: (1 - f).clamp(0.0, 1.0),
                  child: Transform.scale(scale: scale, child: card),
                ),
              ),
            ),
          ]);
        },
      ),
    );
  }
}

class _BannerCard extends StatelessWidget {
  const _BannerCard({required this.notification, required this.strings});

  final GymNotification notification;
  final AppStrings strings;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final p = notification.present(strings);

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(GymRadii.card),
        border: Border.all(color: GymPalette.hairline),
        boxShadow: GymShadows.raised,
      ),
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          notification.leading(size: 38),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(p.title, style: theme.textTheme.titleSmall),
                const SizedBox(height: 2),
                // Body carries the member's name — show it in full.
                Text(
                  p.body,
                  style: theme.textTheme.bodySmall?.copyWith(fontSize: 12),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
