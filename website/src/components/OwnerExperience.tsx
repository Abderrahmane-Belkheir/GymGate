import RevealOnScroll from "./RevealOnScroll";
import Screenshot from "./Screenshot";
import "./OwnerExperience.css";

const CAPABILITIES = [
  "View every member and their plan",
  "Add a member or renew a membership",
  "Update or cancel a membership",
  "Changes sync to the front desk automatically",
];

function PhoneMockup() {
  return (
    <div className="owner-phone">
      <div className="owner-phone-notch" />
      <div className="owner-phone-header">
        <span className="ph-bar ph-bar-lg" />
        <span className="ph-pill" />
      </div>
      {[0, 1, 2].map((i) => (
        <div className="ph-row" key={i}>
          <span className="ph-avatar" />
          <span className="ph-bar ph-bar-name" />
          <span className={`ph-dot${i === 0 ? " ph-dot-on" : ""}`} />
        </div>
      ))}
      <div className="ph-action">Renew membership</div>
    </div>
  );
}

export default function OwnerExperience() {
  return (
    <section className="owner section">
      <div className="container owner-grid">
        <RevealOnScroll className="owner-copy">
          <p className="eyebrow">The owner's app</p>
          <h2 className="display-1">You don't have to be at the gym to run it.</h2>
          <p className="lede owner-lede">
            GymGate isn't only what's installed at the front desk. Owners get a companion
            app to manage the gym from a phone — renew a membership from home, add a member
            between appointments, and see it reflected at the desk within a minute.
          </p>
          <ul className="owner-list">
            {CAPABILITIES.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          <p className="mono-tag owner-note">
            Works offline-first, too — the front desk keeps checking members in even when
            the connection drops, and catches up the moment it's back.
          </p>
        </RevealOnScroll>

        <RevealOnScroll className="owner-art" delay={120}>
          <Screenshot
            src="/assets/screens/owner-app.png"
            alt="GymGate owner app"
            frame="phone"
            fallback={<PhoneMockup />}
          />

          <div className="owner-sync" aria-hidden="true">
            <span className="owner-sync-line">
              <span className="owner-sync-pulse" />
            </span>
            <span className="owner-sync-node">Desk</span>
          </div>
        </RevealOnScroll>
      </div>
    </section>
  );
}
