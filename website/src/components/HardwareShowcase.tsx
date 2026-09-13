import { useEffect, useRef, useState } from "react";
import Frame from "./Frame";
import KioskDiagram, { type KioskStage } from "./KioskDiagram";
import RevealOnScroll from "./RevealOnScroll";
import { usePrefersReducedMotion } from "../hooks/usePrefersReducedMotion";
import { gsap, ScrollTrigger } from "../lib/smoothScroll";
import "./HardwareShowcase.css";

const STEPS: { key: KioskStage; tag: string; title: string; body: string }[] = [
  {
    key: "camera",
    tag: "Camera",
    title: "Built for recognition, not surveillance.",
    body: "Positioned for the angle a member actually approaches from, and tuned for the kind of mixed, changing light a gym entrance gets — not a repurposed security camera.",
  },
  {
    key: "display",
    tag: "5\" display",
    title: "Confirmation, not just a green light.",
    body: "Every member sees their own name and status the moment they're recognized — active, expiring, or otherwise — so there's never a doubt about whether it worked.",
  },
  {
    key: "enclosure",
    tag: "Enclosure",
    title: "A fixture, not a laptop on a desk.",
    body: "A purpose-built enclosure houses the camera and display as one unit — nothing exposed, nothing that looks improvised at the front of the gym.",
  },
  {
    key: "mount",
    tag: "Mount",
    title: "Fits the entrance you already have.",
    body: "Desk-standing or wall-mounted, GymGate is placed to suit your layout — it doesn't ask you to redesign your entrance around it.",
  },
];

// Assembly order and starting offset per part — mount goes down first, the
// enclosure grows around it, then the camera and display drop into the
// finished shell. Independent from STEPS' narrative order above.
const ASSEMBLY_ORDER: KioskStage[] = ["mount", "enclosure", "camera", "display"];
const OFFSETS: Partial<Record<KioskStage, { x?: number; y?: number; scale?: number }>> = {
  mount: { y: 20 },
  enclosure: { scale: 0.88 },
  camera: { y: -16 },
  display: { y: 14 },
};

export default function HardwareShowcase() {
  const [active, setActive] = useState<KioskStage>("camera");
  const stepRefs = useRef<Array<HTMLDivElement | null>>([]);
  const scrollerRef = useRef<HTMLDivElement>(null);
  const artWrapRef = useRef<HTMLDivElement>(null);
  const reducedMotion = usePrefersReducedMotion();

  // Which caption is highlighted — independent of the assembly animation
  // below, driven by simple viewport intersection.
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            const key = entry.target.getAttribute("data-stage") as KioskStage | null;
            if (key) setActive(key);
          }
        }
      },
      { rootMargin: "-45% 0px -45% 0px", threshold: 0 },
    );
    stepRefs.current.forEach((node) => node && observer.observe(node));
    return () => observer.disconnect();
  }, []);

  // The kiosk assembling itself, piece by piece, across the section's whole
  // scroll range — a continuous build, not a per-caption snap, so scrubbing
  // up or down always looks smooth regardless of which caption is active.
  useEffect(() => {
    const wrap = artWrapRef.current;
    const scroller = scrollerRef.current;
    if (!wrap || !scroller || reducedMotion) return;

    // Only on the layout where the diagram is actually sticky (≥861px, see
    // HardwareShowcase.css) — below that the diagram is static and scrolls
    // out of view with its caption, so scrubbing its assembly to a container
    // it's no longer inside of would just be wasted, half-finished motion.
    const ctx = gsap.context(() => {
      ScrollTrigger.matchMedia({
        "(min-width: 861px)": () => {
          const parts = Array.from(wrap.querySelectorAll<SVGGElement>("[data-part]"));
          gsap.set(parts, { transformOrigin: "50% 50%" });

          const segments = ASSEMBLY_ORDER.length;
          const update = (progress: number) => {
            parts.forEach((part) => {
              const key = part.getAttribute("data-part") as KioskStage;
              const idx = ASSEMBLY_ORDER.indexOf(key);
              if (idx === -1) return;
              const segStart = idx / segments;
              const segEnd = segStart + 1 / segments + 0.12; // slight overlap, less mechanical
              const local = gsap.utils.clamp(0, 1, (progress - segStart) / (segEnd - segStart));
              const off = OFFSETS[key] ?? {};
              gsap.set(part, {
                opacity: 0.15 + local * 0.85,
                x: (off.x ?? 0) * (1 - local),
                y: (off.y ?? 0) * (1 - local),
                scale: off.scale ? off.scale + (1 - off.scale) * local : 1,
              });
            });
          };

          const st = ScrollTrigger.create({
            trigger: scroller,
            start: "top top",
            end: "bottom bottom",
            scrub: 0.4,
            onUpdate: (self) => update(self.progress),
          });
          update(st.progress);
        },
      });
    }, wrap);

    return () => ctx.revert();
  }, [reducedMotion]);

  return (
    <section className="hw section" id="hardware">
      <div className="container">
        <RevealOnScroll className="hw-intro">
          <p className="eyebrow">The hardware</p>
          <h2 className="display-1 hw-heading">A kiosk engineered for one job.</h2>
          <p className="lede hw-lede">
            GymGate's differentiator starts before the software: a purpose-built device at
            the door, not a tablet in a stand. Watch it come together.
          </p>
        </RevealOnScroll>
      </div>

      <div className="hw-scroller container" ref={scrollerRef}>
        <div className="hw-sticky-col">
          <div className="hw-sticky">
            <div className="hw-art-frame" ref={artWrapRef}>
              <Frame />
              <KioskDiagram />
            </div>
            <p className="mono-tag hw-active-tag">{STEPS.find((s) => s.key === active)?.tag}</p>
          </div>
        </div>

        <div className="hw-steps-col">
          {STEPS.map((step, i) => (
            <div
              key={step.key}
              data-stage={step.key}
              ref={(el) => {
                stepRefs.current[i] = el;
              }}
              className={`hw-step${active === step.key ? " hw-step-active" : ""}`}
            >
              <p className="mono-tag hw-step-tag">{step.tag}</p>
              <h3 className="display-3">{step.title}</h3>
              <p className="lede hw-step-body">{step.body}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
