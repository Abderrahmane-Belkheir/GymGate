import { useEffect, useRef, useState, type ReactNode } from "react";
import RevealOnScroll from "./RevealOnScroll";
import Screenshot from "./Screenshot";
import Frame from "./Frame";
import Lightbox from "./Lightbox";
import "./SoftwareTour.css";

/**
 * Frames a fallback diagram exactly like a real screenshot would be framed
 * (see Screenshot.css's `.shot-window`) — so swapping in the real file later
 * doesn't change the size or weight of what's on screen, only its content.
 */
function Chrome({ children }: { children: ReactNode }) {
  return (
    <div className="shot shot-window">
      <div className="shot-chrome">
        <span />
        <span />
        <span />
      </div>
      {children}
    </div>
  );
}

/* ---- Small illustrative stand-ins, one per real screen (or dialog), shown
   until the matching file in public/assets/screens/ exists. Same visual
   language as the rest of the site (hairline strokes, one blue accent) —
   never a fake "photo" of a UI, just a diagram. ---- */

function HomeMainVisual() {
  const tiles = [30, 46, 22, 38];
  return (
    <div className="tv-panel tv-home-main">
      <div className="tv-checkin">
        <Frame />
        <div className="tv-checkin-face" />
        <div className="tv-checkin-card">
          <span className="fx-bar" style={{ width: 54 }} />
          <span className="fx-bar" style={{ width: 38, opacity: 0.5 }} />
        </div>
      </div>
      <div className="tv-home-stats">
        {tiles.map((w, i) => (
          <span key={i} className="fx-bar" style={{ width: w, height: 14 }} />
        ))}
      </div>
    </div>
  );
}

function RegisterDialogVisual() {
  return (
    <div className="tv-panel tv-dialog">
      <span className="tv-dialog-avatar" />
      <div className="tv-dialog-field">
        <span className="fx-bar" style={{ width: 40, height: 7, opacity: 0.5 }} />
        <span className="fx-bar" style={{ width: "100%" }} />
      </div>
      <div className="tv-dialog-field">
        <span className="fx-bar" style={{ width: 40, height: 7, opacity: 0.5 }} />
        <span className="fx-bar" style={{ width: "100%" }} />
      </div>
      <span className="tv-dialog-btn" />
    </div>
  );
}

function SeanceDialogVisual() {
  return (
    <div className="tv-panel tv-seance">
      {[0, 1].map((col) => (
        <div className="tv-seance-col" key={col}>
          <span className="fx-bar" style={{ width: 30, height: 8, opacity: 0.5 }} />
          <span className="tv-seance-tile" />
          <span className="tv-seance-tile" />
        </div>
      ))}
    </div>
  );
}

function MemberRowsVisual() {
  const rows = [true, true, false];
  return (
    <div className="tv-panel">
      {rows.map((on, i) => (
        <div className="fx-row" key={i}>
          <span className="fx-avatar" />
          <span className="fx-bar fx-bar-name" />
          <span className={`fx-status ${on ? "fx-status-on" : "fx-status-off"}`} />
        </div>
      ))}
    </div>
  );
}

function MemberProfileVisual() {
  return (
    <div className="tv-panel tv-profile">
      <div className="tv-profile-head">
        <span className="tv-profile-avatar" />
        <div>
          <span className="fx-bar" style={{ width: 100 }} />
          <span className="fx-bar" style={{ width: 60, opacity: 0.5, marginTop: 6 }} />
        </div>
      </div>
      <div className="tv-profile-rows">
        <span className="fx-bar" style={{ width: "100%" }} />
        <span className="fx-bar" style={{ width: "80%" }} />
        <span className="fx-bar" style={{ width: "90%" }} />
      </div>
    </div>
  );
}

function PlansVisual() {
  return (
    <div className="tv-panel tv-plans">
      {[0, 1].map((i) => (
        <div className="tv-plan-card" key={i}>
          <span className="fx-bar" style={{ width: 50 }} />
          <span className="tv-plan-price" />
        </div>
      ))}
    </div>
  );
}

function PlanCreateVisual() {
  return (
    <div className="tv-panel tv-dialog">
      <div className="tv-dialog-field">
        <span className="fx-bar" style={{ width: 64, height: 7, opacity: 0.5 }} />
        <span className="fx-bar" style={{ width: "100%" }} />
      </div>
      <div className="tv-dialog-field-row">
        <div className="tv-dialog-field">
          <span className="fx-bar" style={{ width: 46, height: 7, opacity: 0.5 }} />
          <span className="fx-bar" style={{ width: "100%" }} />
        </div>
        <div className="tv-dialog-field">
          <span className="fx-bar" style={{ width: 36, height: 7, opacity: 0.5 }} />
          <span className="fx-bar" style={{ width: "100%" }} />
        </div>
      </div>
      <span className="tv-dialog-btn" />
    </div>
  );
}

function AttendanceVisual() {
  const cells = Array.from({ length: 28 }, (_, i) => [3, 4, 10, 11, 12, 17, 18, 24, 25].includes(i));
  return (
    <div className="tv-panel">
      <div className="fx-cal">
        {cells.map((filled, i) => (
          <span key={i} className={filled ? "fx-cell fx-cell-on" : "fx-cell"} />
        ))}
      </div>
    </div>
  );
}

function ReceiptVisual() {
  return (
    <div className="tv-panel">
      {[0, 1].map((i) => (
        <div className="fx-receipt-row" key={i}>
          <span className="fx-bar fx-bar-sm" />
          <span className="fx-bar fx-bar-price" />
        </div>
      ))}
      <div className="fx-receipt-divider" />
      <div className="fx-receipt-row">
        <span className="fx-bar fx-bar-sm" style={{ background: "var(--muted)" }} />
        <span className="fx-bar fx-bar-price" style={{ width: 56, background: "var(--signal-strong)" }} />
      </div>
    </div>
  );
}

function ChartVisual() {
  const bars = [38, 62, 44, 80, 58, 71, 90];
  return (
    <div className="tv-panel">
      <div className="fx-chart">
        {bars.map((h, i) => (
          <span
            key={i}
            className={i === bars.length - 1 ? "fx-chart-bar fx-chart-bar-on" : "fx-chart-bar"}
            style={{ height: `${h}%` }}
          />
        ))}
      </div>
    </div>
  );
}

function KpiGridVisual() {
  const tiles = [
    { w: 30 },
    { w: 26 },
    { w: 34 },
    { w: 22 },
  ];
  return (
    <div className="tv-panel tv-kpis">
      {tiles.map((t, i) => (
        <div className="tv-kpi" key={i}>
          <span className="fx-bar" style={{ width: t.w, height: 16 }} />
          <span className="fx-bar" style={{ width: 54, opacity: 0.45, marginTop: 8 }} />
        </div>
      ))}
    </div>
  );
}

interface Shot {
  src: string;
  alt: string;
  fallback: ReactNode;
}

interface Step {
  key: string;
  tag: string;
  title: string;
  body: string;
  shots: Shot[];
}

const STEPS: Step[] = [
  {
    key: "home",
    tag: "Home",
    title: "Where every check-in happens.",
    body: "The front desk's main screen: a live camera feed, the day's recognition results and running stats — plus one tap to register a new member or sell a single session to someone just visiting.",
    shots: [
      { src: "/assets/screens/home-main.png", alt: "GymGate home screen", fallback: <Chrome><HomeMainVisual /></Chrome> },
      { src: "/assets/screens/home-register.png", alt: "GymGate register member dialog", fallback: <Chrome><RegisterDialogVisual /></Chrome> },
      { src: "/assets/screens/home-seance.png", alt: "GymGate single-session sale dialog", fallback: <Chrome><SeanceDialogVisual /></Chrome> },
    ],
  },
  {
    key: "members",
    tag: "Members",
    title: "Every member, one record.",
    body: "A searchable table of every member — plan, status, remaining days — filterable by gender and by active, expiring or expired. Open any member for their full history: payments, visits, renewals.",
    shots: [
      { src: "/assets/screens/members-list.png", alt: "GymGate members list", fallback: <Chrome><MemberRowsVisual /></Chrome> },
      { src: "/assets/screens/member-detail.png", alt: "GymGate member detail view", fallback: <Chrome><MemberProfileVisual /></Chrome> },
    ],
  },
  {
    key: "plans",
    tag: "Plans",
    title: "Membership plans, and single-session pricing.",
    body: "Create and edit membership plans — duration, visit allowance, price — plus separate drop-in pricing for members who just want one session, split by gender and cardio access.",
    shots: [
      { src: "/assets/screens/plans.png", alt: "GymGate plans screen", fallback: <Chrome><PlansVisual /></Chrome> },
      { src: "/assets/screens/plans-create.png", alt: "GymGate create plan dialog", fallback: <Chrome><PlanCreateVisual /></Chrome> },
    ],
  },
  {
    key: "attendance",
    tag: "Attendance",
    title: "Every visit, logged automatically.",
    body: "No sign-in sheet — attendance is recorded the instant a member is recognized at the door. Browse by day, or see attendance patterns across the month.",
    shots: [
      { src: "/assets/screens/attendance.png", alt: "GymGate attendance screen", fallback: <Chrome><AttendanceVisual /></Chrome> },
      { src: "/assets/screens/attendance-stats.png", alt: "GymGate attendance statistics", fallback: <Chrome><ChartVisual /></Chrome> },
    ],
  },
  {
    key: "payments",
    tag: "Payments",
    title: "Renewals and drop-ins, without a ledger.",
    body: "Record a renewal or a single session in a few clicks. Switch to the statistics view for daily, monthly or yearly revenue, split by plan or by single session.",
    shots: [
      { src: "/assets/screens/payments-list.png", alt: "GymGate payments list", fallback: <Chrome><ReceiptVisual /></Chrome> },
      { src: "/assets/screens/payments-stats.png", alt: "GymGate payments statistics", fallback: <Chrome><ChartVisual /></Chrome> },
    ],
  },
  {
    key: "reports",
    tag: "Reports",
    title: "The numbers a gym owner actually checks.",
    body: "New members, renewals, visits and revenue — compared to the previous period, not just totals — so you can tell if the gym is actually growing.",
    shots: [{ src: "/assets/screens/reports.png", alt: "GymGate reports screen", fallback: <Chrome><KpiGridVisual /></Chrome> }],
  },
];

export default function SoftwareTour() {
  const [active, setActive] = useState(STEPS[0].key);
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);
  const stepRefs = useRef<Array<HTMLDivElement | null>>([]);
  const activeStep = STEPS.find((s) => s.key === active);

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            const key = entry.target.getAttribute("data-stage");
            if (key) setActive(key);
          }
        }
      },
      { rootMargin: "-45% 0px -45% 0px", threshold: 0 },
    );
    stepRefs.current.forEach((node) => node && observer.observe(node));
    return () => observer.disconnect();
  }, []);

  return (
    <section className="tour section" id="software">
      <div className="container">
        <RevealOnScroll className="tour-intro">
          <p className="eyebrow">The software</p>
          <h2 className="display-1">Everything the front desk used to track by hand.</h2>
          <p className="lede tour-lede">
            One screen at a time — this is the actual desk software, not a mockup.
          </p>
        </RevealOnScroll>
      </div>

      <div className="tour-scroller container">
        <div className="tour-sticky-col">
          <div className="tour-sticky">
            <div className="tour-shots-wrap">
              {STEPS.map((step) => {
                const isActive = active === step.key;
                return (
                  <div
                    key={step.key}
                    className={`tour-shots${isActive ? " tour-shots-active" : ""}`}
                    aria-hidden={!isActive}
                  >
                    {step.shots.map((shot, i) => (
                      <button
                        type="button"
                        className={`tour-shot tour-shot-${i}`}
                        key={shot.src}
                        tabIndex={isActive ? 0 : -1}
                        aria-label={`Expand ${shot.alt}`}
                        onClick={() => setLightboxIndex(i)}
                      >
                        <Screenshot src={shot.src} alt={shot.alt} fallback={shot.fallback} />
                      </button>
                    ))}
                  </div>
                );
              })}
            </div>
            <p className="mono-tag tour-active-tag">{activeStep?.tag}</p>
          </div>
        </div>

        <div className="tour-steps-col">
          {STEPS.map((step, i) => (
            <div
              key={step.key}
              data-stage={step.key}
              ref={(el) => {
                stepRefs.current[i] = el;
              }}
              className={`tour-step${active === step.key ? " tour-step-active" : ""}`}
            >
              <p className="mono-tag tour-step-tag">{step.tag}</p>
              <h3 className="display-3">{step.title}</h3>
              <p className="lede tour-step-body">{step.body}</p>
            </div>
          ))}
        </div>
      </div>

      {lightboxIndex !== null && activeStep && (
        <Lightbox
          items={activeStep.shots}
          index={lightboxIndex}
          label={activeStep.tag}
          onClose={() => setLightboxIndex(null)}
          onIndexChange={setLightboxIndex}
        />
      )}
    </section>
  );
}
