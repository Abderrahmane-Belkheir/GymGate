import RevealOnScroll from "./RevealOnScroll";
import "./HowItWorks.css";

const STEPS = [
  { title: "A member walks up", body: "No card, no code, nothing to hand over." },
  { title: "GymGate recognizes them", body: "The camera identifies the member at the door." },
  { title: "Status is checked instantly", body: "Active, expiring soon, or expired — confirmed in place." },
  { title: "The visit is logged", body: "Attendance is recorded automatically, no sign-in sheet." },
  { title: "The owner can see it happen", body: "From the front desk, or from a phone, anywhere." },
];

export default function HowItWorks() {
  return (
    <section className="hiw section" id="how-it-works">
      <div className="container">
        <RevealOnScroll className="hiw-intro">
          <p className="eyebrow">How it works</p>
          <h2 className="display-2">From the door to the ledger, in one motion.</h2>
        </RevealOnScroll>

        <div className="hiw-steps">
          {STEPS.map((step, i) => (
            <RevealOnScroll className="hiw-step" delay={i * 90} key={step.title}>
              <span className="hiw-index">{String(i + 1).padStart(2, "0")}</span>
              <h3 className="hiw-title">{step.title}</h3>
              <p className="hiw-body">{step.body}</p>
            </RevealOnScroll>
          ))}
        </div>
      </div>
    </section>
  );
}
