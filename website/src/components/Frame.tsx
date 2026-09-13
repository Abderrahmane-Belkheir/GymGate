interface FrameProps {
  /** Plays the corners snapping inward from outside on mount — the "camera
   *  locking onto its subject" moment used once, in the hero. Skipped
   *  automatically for reduced motion by the caller (don't pass it in). */
  animateIn?: boolean;
}

/**
 * The recurring signature motif: four reticle corners, the same framing a
 * camera draws around a face it's about to recognize. Used on the hero
 * kiosk art, hardware diagrams and the recognition moment so the one visual
 * idea in the whole site is also the truest thing about the product.
 * Absolutely positioned — the parent needs `position: relative`.
 */
export default function Frame({ animateIn = false }: FrameProps) {
  return (
    <span className={`frame-corners${animateIn ? " frame-anim" : ""}`} aria-hidden="true">
      <span />
      <span />
      <span />
      <span />
    </span>
  );
}
