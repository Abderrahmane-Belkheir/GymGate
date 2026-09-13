import { useEffect, useRef, useState } from "react";
import { usePrefersReducedMotion } from "../hooks/usePrefersReducedMotion";
import { useAssetExists } from "../hooks/useAssetExists";
import Frame from "./Frame";
import KioskDiagram from "./KioskDiagram";
import "./CinematicVideo.css";

/**
 * Drop the final clip here (public/assets/gymgate-product-video.mp4) and this
 * section activates automatically — nothing else in this file needs to
 * change. Until then it shows a designed stand-in, not a broken player.
 */
const VIDEO_SRC = "/assets/gymgate-product-video.mp4";

export default function CinematicVideo() {
  const wrapRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const { checked, exists: hasVideo } = useAssetExists(VIDEO_SRC, "video/");
  const [progressPct, setProgressPct] = useState(0);
  const reducedMotion = usePrefersReducedMotion();

  useEffect(() => {
    if (!hasVideo || reducedMotion) return;
    const wrap = wrapRef.current;
    const video = videoRef.current;
    if (!wrap || !video) return;

    video.pause();
    let frame = 0;

    const apply = () => {
      const rect = wrap.getBoundingClientRect();
      const total = rect.height - window.innerHeight;
      const progress = total > 0 ? clamp((-rect.top) / total, 0, 1) : 0;
      setProgressPct(progress * 100);
      if (video.duration) {
        video.currentTime = progress * video.duration;
      }
    };

    const onScroll = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(apply);
    };

    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    apply();
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      cancelAnimationFrame(frame);
    };
  }, [hasVideo, reducedMotion]);

  // Reduced motion or no footage yet: a normal, short, static section —
  // never a tall scroll-jacked one with nothing to show for it.
  if (!hasVideo || reducedMotion) {
    return (
      <section className="cine cine-static" id="product">
        <div className="cine-stage">
          <div className="cine-fallback-frame">
            <Frame />
            <KioskDiagram className="cine-fallback-art" />
          </div>
          <p className="mono-tag cine-slate">
            {checked ? "REEL PENDING — 00:00 / 00:10" : "LOADING —"}
          </p>
        </div>
      </section>
    );
  }

  return (
    <section className="cine" ref={wrapRef} id="product">
      <div className="cine-sticky">
        <div className="cine-stage">
          <video
            ref={videoRef}
            src={VIDEO_SRC}
            muted
            playsInline
            preload="auto"
            aria-label="GymGate product film — controlled by scroll position"
          />
          <Frame />
          <p className="mono-tag cine-slate">
            REEL — 00:{((progressPct / 100) * 10).toFixed(1).padStart(4, "0")} / 00:10.0
          </p>
        </div>
      </div>
    </section>
  );
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}
