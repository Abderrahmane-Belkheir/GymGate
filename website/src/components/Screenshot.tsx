import type { ReactNode } from "react";
import { useAssetExists } from "../hooks/useAssetExists";
import "./Screenshot.css";

interface ScreenshotProps {
  /** e.g. "/assets/screens/members.png" — see public/assets/screens/README.md */
  src: string;
  alt: string;
  /** "window" for a desktop app screen, "phone" for the owner's mobile app. */
  frame?: "window" | "phone";
  /** What to render until the real screenshot is dropped in — the existing
   *  hand-drawn diagram for that row, never a blank gap. */
  fallback: ReactNode;
  className?: string;
}

/**
 * A placeholder for real product screenshots. Checks whether `src` actually
 * exists (see useAssetExists) and shows it framed like a real screen the
 * moment it does — until then, `fallback` (the illustrative diagram already
 * built for that spot) renders untouched, so shipping this page never
 * depends on having the screenshots yet.
 */
export default function Screenshot({ src, alt, frame = "window", fallback, className }: ScreenshotProps) {
  const { exists } = useAssetExists(src, "image/");

  if (!exists) {
    return <>{fallback}</>;
  }

  if (frame === "phone") {
    return (
      <div className={`shot shot-phone${className ? ` ${className}` : ""}`}>
        <div className="shot-phone-notch" />
        <img src={src} alt={alt} className="shot-img" />
      </div>
    );
  }

  return (
    <div className={`shot shot-window${className ? ` ${className}` : ""}`}>
      <div className="shot-chrome">
        <span />
        <span />
        <span />
      </div>
      <img src={src} alt={alt} className="shot-img" />
    </div>
  );
}
