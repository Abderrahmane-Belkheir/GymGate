import { useEffect, useRef } from "react";
import RevealOnScroll from "./RevealOnScroll";
import { usePrefersReducedMotion } from "../hooks/usePrefersReducedMotion";
import { gsap } from "../lib/smoothScroll";
import "./Solution.css";

const NODES = [
  { x: 90, label: "Entrance", detail: "Recognizes the member" },
  { x: 400, label: "Software", detail: "Logs the visit, updates the plan" },
  { x: 710, label: "Owner", detail: "Sees it happen, from anywhere" },
];

export default function Solution() {
  const sectionRef = useRef<HTMLElement>(null);
  const pathRef = useRef<SVGPathElement>(null);
  const reducedMotion = usePrefersReducedMotion();

  useEffect(() => {
    const section = sectionRef.current;
    const path = pathRef.current;
    if (!section || !path) return;

    const length = path.getTotalLength();

    if (reducedMotion) {
      path.style.strokeDasharray = "none";
      return;
    }

    const ctx = gsap.context(() => {
      gsap.set(path, { strokeDasharray: length, strokeDashoffset: length });
      gsap.to(path, {
        strokeDashoffset: 0,
        ease: "none",
        scrollTrigger: {
          trigger: section,
          start: "top 75%",
          end: "bottom 65%",
          scrub: 0.6,
        },
      });
    }, section);

    return () => ctx.revert();
  }, [reducedMotion]);

  return (
    <section className="solution section" ref={sectionRef}>
      <div className="container">
        <RevealOnScroll className="solution-copy">
          <p className="eyebrow">The idea</p>
          <h2 className="display-1 solution-heading">
            One system connects
            <br />
            the door to the business.
          </h2>
        </RevealOnScroll>

        <div className="solution-diagram">
          <svg viewBox="0 0 800 140" className="solution-svg" aria-hidden="true">
            <line x1="90" y1="46" x2="710" y2="46" className="solution-track" />
            <path ref={pathRef} d="M90 46 L710 46" className="solution-draw" />
            {NODES.map((node) => (
              <circle key={node.label} cx={node.x} cy="46" r="6" className="solution-dot" />
            ))}
          </svg>
          <div className="solution-labels">
            {NODES.map((node) => (
              <div className="solution-label" key={node.label} style={{ left: `${(node.x / 800) * 100}%` }}>
                <p className="solution-label-name">{node.label}</p>
                <p className="solution-label-detail">{node.detail}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
