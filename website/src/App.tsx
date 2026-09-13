import { useEffect } from "react";
import Nav from "./components/Nav";
import Hero from "./components/Hero";
import CinematicVideo from "./components/CinematicVideo";
import Problem from "./components/Problem";
import RecognitionMoment from "./components/RecognitionMoment";
import Solution from "./components/Solution";
import SoftwareTour from "./components/SoftwareTour";
import HardwareShowcase from "./components/HardwareShowcase";
import OwnerExperience from "./components/OwnerExperience";
import HowItWorks from "./components/HowItWorks";
import Pricing from "./components/Pricing";
import Trust from "./components/Trust";
import FinalCta from "./components/FinalCta";
import Footer from "./components/Footer";
import { usePrefersReducedMotion } from "./hooks/usePrefersReducedMotion";
import { startSmoothScroll, stopSmoothScroll } from "./lib/smoothScroll";

function App() {
  const reducedMotion = usePrefersReducedMotion();

  useEffect(() => {
    if (reducedMotion) {
      stopSmoothScroll();
      return;
    }
    startSmoothScroll();
    return () => stopSmoothScroll();
  }, [reducedMotion]);

  return (
    <>
      <Nav />
      <main>
        <Hero />
        <CinematicVideo />
        <Problem />
        <HardwareShowcase />
        <RecognitionMoment />
        <SoftwareTour />
        <OwnerExperience />
        <Solution />
        <HowItWorks />
        <Pricing />
        <Trust />
        <FinalCta />
      </main>
      <Footer />
    </>
  );
}

export default App;
