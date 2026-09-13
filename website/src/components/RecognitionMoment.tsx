import { useEffect, useRef, useState } from "react";
import Frame from "./Frame";
import RevealOnScroll from "./RevealOnScroll";
import { usePrefersReducedMotion } from "../hooks/usePrefersReducedMotion";
import "./RecognitionMoment.css";

/**
 * Scene 4 — deliberately the quietest section on the page. Everything else
 * explains the system; this one just shows the single human moment the
 * whole product exists for. One figure, one frame, one line of text.
 */
export default function RecognitionMoment() {
  const sectionRef = useRef<HTMLElement>(null);
  const [entered, setEntered] = useState(false);
  const reducedMotion = usePrefersReducedMotion();

  useEffect(() => {
    const node = sectionRef.current;
    if (!node) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setEntered(true);
          observer.disconnect();
        }
      },
      { threshold: 0.45 },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  return (
    <section className="rec" ref={sectionRef}>
      <div className="container rec-inner">
        <p className="eyebrow rec-eyebrow">The moment of check-in</p>

        <div className="rec-stage">
          <Frame animateIn={entered && !reducedMotion} />
          <svg className={`rec-figure${entered ? " rec-figure-in" : ""}`} viewBox="0 0 120 140" aria-hidden="true">
            <circle cx="60" cy="46" r="26" />
            <path d="M14 132c4-34 26-52 46-52s42 18 46 52" />
          </svg>
        </div>

        <RevealOnScroll className="rec-readout" delay={450}>
          <span className="rec-readout-dot" />
          <div>
            <p className="rec-readout-name">Sara K.</p>
            <p className="rec-readout-status">Active · 6 days left</p>
          </div>
        </RevealOnScroll>

        <RevealOnScroll delay={250} as="h2" className="display-1 rec-statement">
          No card. No code.
          <br />
          Just walking in.
        </RevealOnScroll>
      </div>
    </section>
  );
}
