import { useEffect, useRef, useState } from "react";
import Frame from "./Frame";
import RevealOnScroll from "./RevealOnScroll";
import { usePrefersReducedMotion } from "../hooks/usePrefersReducedMotion";
import "./RecognitionMoment.css";

function PlanIcon() {
  return (
    <svg viewBox="0 0 14 14" fill="none">
      <rect x="2" y="2.5" width="10" height="9" rx="1.4" />
      <path d="M4.7 5.3h4.6M4.7 7.5h4.6M4.7 9.7h2.6" />
    </svg>
  );
}

function CalendarIcon() {
  return (
    <svg viewBox="0 0 14 14" fill="none">
      <rect x="2" y="2.8" width="10" height="9" rx="1.4" />
      <path d="M2 5.6h10M4.6 1.6v2M9.4 1.6v2" />
    </svg>
  );
}

function HourglassIcon() {
  return (
    <svg viewBox="0 0 14 14" fill="none">
      <path d="M3.4 1.8h7.2M3.4 12.2h7.2M4 1.8v1.8c0 1 .6 1.7 1.6 2.4.7.5.7.7 0 1.2C4.6 8 4 8.7 4 9.7v2.5M10 1.8v1.8c0 1-.6 1.7-1.6 2.4-.7.5-.7.7 0 1.2 1 .7 1.6 1.4 1.6 2.4v2.5" />
    </svg>
  );
}

function ClockIcon() {
  return (
    <svg viewBox="0 0 14 14" fill="none">
      <circle cx="7" cy="7" r="5.2" />
      <path d="M7 4.2V7l2 1.4" />
    </svg>
  );
}

function PhoneIcon() {
  return (
    <svg viewBox="0 0 14 14" fill="none">
      <path d="M3 2.4h1.9l.9 2.6-1.2 1c.5 1.2 1.4 2.1 2.6 2.6l1-1.2 2.6.9V10.2c0 .7-.6 1.2-1.3 1.1-4-.5-6.8-3.3-7.3-7.3C1.8 3 2.3 2.4 3 2.4Z" />
    </svg>
  );
}

/**
 * The real recognition-result card from GymGate's Home screen, replicated
 * field-for-field (photo, name, status, phone, then plan / expires / days
 * left / last check-in) — sample name and avatar since this scene isn't a
 * real screenshot like SoftwareTour's.
 */
function RecognitionCard() {
  return (
    <div className="rec-card">
      <span className="rec-card-avatar" aria-hidden="true">
        KB
      </span>

      <div className="rec-card-id">
        <div className="rec-card-name-row">
          <p className="rec-card-name">Karim B.</p>
          <span className="rec-card-badge">Success</span>
        </div>
        <p className="rec-card-phone">
          <PhoneIcon />
          0556 12 34 56
        </p>
      </div>

      <span className="rec-card-divider" aria-hidden="true" />

      <div className="rec-card-stat">
        <span className="rec-card-stat-label">
          <PlanIcon />
          Plan
        </span>
        <span className="rec-card-stat-value">Standard</span>
      </div>
      <div className="rec-card-stat">
        <span className="rec-card-stat-label">
          <CalendarIcon />
          Expires
        </span>
        <span className="rec-card-stat-value">2026-10-11</span>
      </div>
      <div className="rec-card-stat">
        <span className="rec-card-stat-label">
          <HourglassIcon />
          Days left
        </span>
        <span className="rec-card-stat-value">12</span>
      </div>
      <div className="rec-card-stat">
        <span className="rec-card-stat-label">
          <ClockIcon />
          Last check-in
        </span>
        <span className="rec-card-stat-value">09:14</span>
      </div>
    </div>
  );
}

/**
 * Scene 4 — deliberately the quietest section on the page. Everything else
 * explains the system; this one just shows the single human moment the
 * whole product exists for. One card, one frame, one line of text.
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

        <div className={`rec-stage${entered ? " rec-stage-in" : ""}`}>
          <Frame animateIn={entered && !reducedMotion} />
          <RecognitionCard />
        </div>

        <RevealOnScroll delay={250} as="h2" className="display-1 rec-statement">
          No card. No code.
          <br />
          Just walking in.
        </RevealOnScroll>
      </div>
    </section>
  );
}
