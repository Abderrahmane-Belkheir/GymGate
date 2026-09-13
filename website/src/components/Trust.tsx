import RevealOnScroll from "./RevealOnScroll";
import "./Trust.css";

const POINTS = [
  {
    tag: "Built, not assembled",
    body: "The kiosk, the desk software and the owner's app are built as one system by the same person who installs it — not stitched together from off-the-shelf parts.",
  },
  {
    tag: "Works without the internet",
    body: "The front desk runs on a local database first. A dropped connection doesn't stop check-ins — it just catches up once you're back online.",
  },
  {
    tag: "Hands-on, from day one",
    body: "Setup happens on site, with your staff, not over a support ticket. Early customers deal directly with the person who built the system.",
  },
];

export default function Trust() {
  return (
    <section className="trust section">
      <div className="container">
        <RevealOnScroll className="trust-intro">
          <p className="eyebrow">Why trust it</p>
          <h2 className="display-2">An early product — built to be reliable regardless.</h2>
        </RevealOnScroll>

        <div className="trust-grid">
          {POINTS.map((point, i) => (
            <RevealOnScroll className="trust-item" delay={i * 100} key={point.tag}>
              <p className="mono-tag trust-item-tag">{point.tag}</p>
              <p className="trust-item-body">{point.body}</p>
            </RevealOnScroll>
          ))}
        </div>
      </div>
    </section>
  );
}
