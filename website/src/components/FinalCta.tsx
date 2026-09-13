import RevealOnScroll from "./RevealOnScroll";
import "./FinalCta.css";

/** TODO: replace with the real inbox / WhatsApp number before this goes live. */
const CONTACT_EMAIL = "hello@gymgate.app";

export default function FinalCta() {
  return (
    <section className="cta section" id="contact">
      <div className="container">
        <RevealOnScroll className="cta-box">
          <p className="eyebrow">Get started</p>
          <h2 className="display-1 cta-heading">Ready to automate your front desk?</h2>
          <p className="lede cta-lede">
            Tell us about your gym and we'll walk you through what installing GymGate looks
            like — the hardware, the setup, and what it costs for your entrance.
          </p>
          <div className="cta-actions">
            <a href={`mailto:${CONTACT_EMAIL}?subject=GymGate%20demo%20request`} className="btn btn-primary">
              Request a demo
            </a>
            <a href={`mailto:${CONTACT_EMAIL}`} className="cta-email mono-tag">
              {CONTACT_EMAIL}
            </a>
          </div>
        </RevealOnScroll>
      </div>
    </section>
  );
}
