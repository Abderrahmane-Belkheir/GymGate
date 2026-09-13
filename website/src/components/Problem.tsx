import RevealOnScroll from "./RevealOnScroll";
import "./Problem.css";

const LOG = [
  { time: "07:42", line: "Staff steps away — the front desk sits unattended for ten minutes." },
  { time: "08:15", line: "A member's card doesn't scan. The front desk checks the binder instead." },
  { time: "12:30", line: "Someone new signs up. Their details go on paper, to be entered later." },
  { time: "18:04", line: "An expired membership walks in anyway. No one at the door to catch it." },
  { time: "22:00", line: "Closing time. Nobody can say exactly who came in today, or when." },
];

export default function Problem() {
  return (
    <section className="problem section">
      <div className="container">
        <RevealOnScroll className="problem-intro">
          <p className="eyebrow">The problem</p>
          <h2 className="display-1">
            Running the door takes a person.
            <br />
            Running the numbers takes another.
          </h2>
        </RevealOnScroll>

        <div className="problem-log">
          {LOG.map((entry, i) => (
            <RevealOnScroll className="problem-row" delay={i * 90} key={entry.time}>
              <span className="problem-time mono-tag">{entry.time}</span>
              <span className="problem-line">{entry.line}</span>
            </RevealOnScroll>
          ))}
        </div>

        <RevealOnScroll delay={LOG.length * 90} className="problem-close">
          Most gyms run this way — on a spreadsheet, a binder, or memory.
        </RevealOnScroll>
      </div>
    </section>
  );
}
