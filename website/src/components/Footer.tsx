import Logomark from "./Logomark";
import "./Footer.css";

export default function Footer() {
  return (
    <footer className="footer">
      <div className="container footer-inner">
        <a href="#top" className="footer-brand">
          <Logomark size={20} />
          <span>GymGate</span>
        </a>
        <p className="footer-note">Check-in kiosk and management system for independent gyms.</p>
        <p className="footer-copyright mono-tag">© {new Date().getFullYear()} GymGate</p>
      </div>
    </footer>
  );
}
