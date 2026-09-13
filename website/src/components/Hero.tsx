import { useEffect, useRef } from "react";
import { usePrefersReducedMotion } from "../hooks/usePrefersReducedMotion";
import { gsap } from "../lib/smoothScroll";
import "./Hero.css";

const LINES = ["The front desk", "that never", "has to ask."];

export default function Hero() {
  const rootRef = useRef<HTMLElement>(null);
  const lineRefs = useRef<Array<HTMLSpanElement | null>>([]);
  const ledeRef = useRef<HTMLDivElement>(null);
  const reducedMotion = usePrefersReducedMotion();

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;

    const ctx = gsap.context(() => {
      if (reducedMotion) {
        gsap.set(ledeRef.current, { opacity: 1, y: 0 });
        gsap.set(lineRefs.current, { yPercent: 0 });
        return;
      }

      gsap.set(lineRefs.current, { yPercent: 110 });
      gsap.set(ledeRef.current, { opacity: 0, y: 16 });

      const tl = gsap.timeline({ defaults: { ease: "power3.out" } });
      tl.to(lineRefs.current, { yPercent: 0, duration: 0.9, stagger: 0.09 }, 0.15).to(
        ledeRef.current,
        { opacity: 1, y: 0, duration: 0.7 },
        0.55,
      );
    }, root);

    // ctx.revert() also tears down the ScrollTriggers created inside this
    // scope — GSAP contexts track them automatically.
    return () => ctx.revert();
  }, [reducedMotion]);

  return (
    <section id="top" className="hero" ref={rootRef}>
      <div className="container hero-container">
        <div className="hero-inner">
          <p className="eyebrow">Face-recognition check-in</p>

          <h1 className="display-1 hero-headline">
            {LINES.map((line, i) => (
              <span className="hero-line-mask" key={line}>
                <span
                  className={`hero-line${i === LINES.length - 1 ? " hero-line-accent" : ""}`}
                  ref={(el) => {
                    lineRefs.current[i] = el;
                  }}
                >
                  {line}
                </span>
              </span>
            ))}
          </h1>

          <div className="hero-lede-row" ref={ledeRef}>
            <p className="lede hero-lede">
              GymGate is a check-in kiosk and management system built for independent gyms —
              recognizing members at the door, keeping attendance and payments current on its
              own, and giving you a phone app to run the front desk from anywhere.
            </p>
            <div className="hero-actions">
              <a href="#contact" className="btn btn-primary">
                Request a demo
              </a>
              <a href="#how-it-works" className="btn btn-ghost">
                See how it works
              </a>
            </div>
          </div>
        </div>
      </div>

      <div className="hero-scrollcue" aria-hidden="true">
        <span />
      </div>
    </section>
  );
}
