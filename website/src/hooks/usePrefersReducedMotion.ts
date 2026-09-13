import { useEffect, useState } from "react";

/**
 * Tracks the `prefers-reduced-motion` media query live, so components can
 * skip GSAP/Lenis-driven motion for anyone who has it set — including a
 * change mid-session, not just the value at first render.
 */
export function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(
    () => typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches,
  );

  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const onChange = () => setReduced(mq.matches);
    onChange();
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);

  return reduced;
}
