import { useEffect, useState } from "react";
import Logomark from "./Logomark";
import "./Nav.css";

const LINKS = [
  { href: "#product", label: "Product" },
  { href: "#hardware", label: "Hardware" },
  { href: "#software", label: "Software" },
  { href: "#how-it-works", label: "How it works" },
  { href: "#pricing", label: "Pricing" },
];

export default function Nav() {
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <header className={`nav${scrolled ? " nav-scrolled" : ""}${open ? " nav-open" : ""}`}>
      <div className="nav-inner container">
        <a href="#top" className="nav-brand" onClick={() => setOpen(false)}>
          <Logomark size={26} />
          <span>
            Gym<strong>Gate</strong>
          </span>
        </a>

        <nav className="nav-links" aria-label="Primary">
          {LINKS.map((link) => (
            <a key={link.href} href={link.href}>
              {link.label}
            </a>
          ))}
        </nav>

        <div className="nav-cta">
          <a href="#contact" className="btn btn-primary btn-sm">
            Request a demo
          </a>
        </div>

        <button
          type="button"
          className="nav-toggle"
          aria-expanded={open}
          aria-label="Toggle menu"
          onClick={() => setOpen((v) => !v)}
        >
          <span />
          <span />
        </button>
      </div>

      <div className="nav-mobile" role="dialog" aria-hidden={!open}>
        {LINKS.map((link) => (
          <a key={link.href} href={link.href} onClick={() => setOpen(false)}>
            {link.label}
          </a>
        ))}
        <a href="#contact" className="btn btn-primary" onClick={() => setOpen(false)}>
          Request a demo
        </a>
      </div>
    </header>
  );
}
