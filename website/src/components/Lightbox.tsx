import { useEffect, type ReactNode } from "react";
import Screenshot from "./Screenshot";
import { pausePageScroll, resumePageScroll } from "../lib/smoothScroll";
import "./Lightbox.css";

export interface LightboxItem {
  src: string;
  alt: string;
  fallback: ReactNode;
}

interface LightboxProps {
  items: LightboxItem[];
  index: number;
  label?: string;
  onClose: () => void;
  onIndexChange: (index: number) => void;
}

/**
 * A focused view of the screenshot(s) for whichever section triggered it —
 * never the full set of twelve, just the `items` that section was already
 * showing (see SoftwareTour, which passes only its active step's shots).
 */
export default function Lightbox({ items, index, label, onClose, onIndexChange }: LightboxProps) {
  const item = items[index];
  const hasMultiple = items.length > 1;

  useEffect(() => {
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    pausePageScroll();
    return () => {
      document.body.style.overflow = prevOverflow;
      resumePageScroll();
    };
  }, []);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
      if (hasMultiple && e.key === "ArrowRight") onIndexChange((index + 1) % items.length);
      if (hasMultiple && e.key === "ArrowLeft") onIndexChange((index - 1 + items.length) % items.length);
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [index, items.length, hasMultiple, onClose, onIndexChange]);

  if (!item) return null;

  return (
    <div className="lightbox" role="dialog" aria-modal="true" aria-label={label ?? "Screenshot"} onClick={onClose}>
      <button type="button" className="lightbox-close" onClick={onClose} aria-label="Close">
        <span />
        <span />
      </button>

      {hasMultiple && (
        <button
          type="button"
          className="lightbox-nav lightbox-prev"
          aria-label="Previous screenshot"
          onClick={(e) => {
            e.stopPropagation();
            onIndexChange((index - 1 + items.length) % items.length);
          }}
        >
          <svg viewBox="0 0 24 24">
            <path d="M15 4l-8 8 8 8" />
          </svg>
        </button>
      )}

      <div className="lightbox-content" onClick={(e) => e.stopPropagation()}>
        <Screenshot src={item.src} alt={item.alt} fallback={item.fallback} />
        {label && <p className="mono-tag lightbox-tag">{label}</p>}
      </div>

      {hasMultiple && (
        <button
          type="button"
          className="lightbox-nav lightbox-next"
          aria-label="Next screenshot"
          onClick={(e) => {
            e.stopPropagation();
            onIndexChange((index + 1) % items.length);
          }}
        >
          <svg viewBox="0 0 24 24">
            <path d="M9 4l8 8-8 8" />
          </svg>
        </button>
      )}

      {hasMultiple && (
        <div className="lightbox-dots" onClick={(e) => e.stopPropagation()}>
          {items.map((shot, i) => (
            <button
              key={shot.src}
              type="button"
              className={`lightbox-dot${i === index ? " lightbox-dot-active" : ""}`}
              aria-label={`Go to screenshot ${i + 1}`}
              onClick={() => onIndexChange(i)}
            />
          ))}
        </div>
      )}
    </div>
  );
}
