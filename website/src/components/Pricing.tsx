import RevealOnScroll from "./RevealOnScroll";
import "./Pricing.css";

type FeatureKey = "software" | "camera" | "hardware" | "turnstile";

const FEATURES: { key: FeatureKey; label: string }[] = [
  { key: "software", label: "Management software + owner's app" },
  { key: "camera", label: "Recognition camera (bring your own display)" },
  { key: "hardware", label: "Full kiosk — camera, display, enclosure" },
  { key: "turnstile", label: "Turnstile access control" },
];

interface Plan {
  key: string;
  name: string;
  price: string;
  featured?: boolean;
  included: Record<FeatureKey, boolean>;
}

const PLANS: Plan[] = [
  {
    key: "basic",
    name: "Basic",
    price: "69,000",
    included: { software: true, camera: true, hardware: false, turnstile: false },
  },
  {
    key: "standard",
    name: "Standard",
    price: "99,000",
    featured: true,
    included: { software: true, camera: false, hardware: true, turnstile: false },
  },
  {
    key: "premium",
    name: "Premium",
    price: "109,000",
    included: { software: true, camera: false, hardware: true, turnstile: true },
  },
];

function Mark({ on }: { on: boolean }) {
  if (!on) return <span className="pt-mark pt-mark-off" aria-hidden="true">—</span>;
  return (
    <svg className="pt-mark pt-mark-on" width="15" height="15" viewBox="0 0 15 15" fill="none" aria-hidden="true">
      <path d="M3 8l3 3 6-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function PlanHeader({ plan }: { plan: Plan }) {
  return (
    <>
      {plan.featured && <p className="pt-featured-tag">Most installed</p>}
      <p className="pt-plan-name">{plan.name}</p>
      <p className="pt-price">
        {plan.price}
        <span className="pt-currency">DZD</span>
      </p>
      <p className="mono-tag pt-price-note">One-time · per gym</p>
      <a href="#contact" className={`btn btn-sm pt-cta ${plan.featured ? "btn-primary" : "btn-ghost"}`}>
        Request this plan
      </a>
    </>
  );
}

export default function Pricing() {
  return (
    <section className="pricing section" id="pricing">
      <div className="container">
        <RevealOnScroll className="pricing-intro">
          <p className="eyebrow">Pricing</p>
          <h2 className="display-2">Three ways to install GymGate.</h2>
          <p className="lede pricing-lede">
            A one-time price per gym, not a monthly seat count. Every plan includes setup and
            support — the difference is how much of the hardware you need from us.
          </p>
        </RevealOnScroll>

        {/* Desktop / tablet: a real comparison table. */}
        <RevealOnScroll className="pricing-table" delay={100} role="table" aria-label="GymGate plans compared">
          <div className="pt-row pt-row-head" role="row">
            <div className="pt-cell pt-cell-label" role="columnheader" />
            {PLANS.map((plan) => (
              <div
                key={plan.key}
                className={`pt-cell pt-cell-plan${plan.featured ? " pt-cell-featured" : ""}`}
                role="columnheader"
              >
                <PlanHeader plan={plan} />
              </div>
            ))}
          </div>

          {FEATURES.map((feature) => (
            <div className="pt-row" role="row" key={feature.key}>
              <div className="pt-cell pt-cell-label" role="rowheader">
                {feature.label}
              </div>
              {PLANS.map((plan) => (
                <div
                  key={plan.key}
                  className={`pt-cell pt-cell-mark${plan.featured ? " pt-cell-featured" : ""}`}
                  role="cell"
                >
                  <Mark on={plan.included[feature.key]} />
                </div>
              ))}
            </div>
          ))}
        </RevealOnScroll>

        {/* Mobile: the same data as three stacked plan cards. */}
        <div className="pricing-cards">
          {PLANS.map((plan, i) => (
            <RevealOnScroll
              key={plan.key}
              delay={i * 90}
              className={`pricing-plan-card${plan.featured ? " pricing-plan-card-featured" : ""}`}
            >
              <PlanHeader plan={plan} />
              <ul className="pricing-plan-features">
                {FEATURES.map((feature) => (
                  <li key={feature.key} className={plan.included[feature.key] ? "" : "pricing-plan-feature-off"}>
                    <Mark on={plan.included[feature.key]} />
                    <span>{feature.label}</span>
                  </li>
                ))}
              </ul>
            </RevealOnScroll>
          ))}
        </div>

        <p className="mono-tag pricing-footnote">
          Premium's turnstile is quoted for integration — the turnstile unit itself is
          purchased separately, by the gym.
        </p>
      </div>
    </section>
  );
}
