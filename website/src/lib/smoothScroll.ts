import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import Lenis from "lenis";

gsap.registerPlugin(ScrollTrigger);

let lenis: Lenis | null = null;
let tickerFn: ((time: number) => void) | null = null;

/**
 * Wires Lenis (inertial smooth scroll — the "expensive" feel under every
 * award-site scroll sequence) into GSAP's own ticker, so ScrollTrigger reads
 * Lenis's smoothed position instead of the browser's raw, steppy scrollY.
 * Idempotent and safe to call once at the app root; `stopSmoothScroll`
 * reverts to native scrolling for `prefers-reduced-motion`.
 */
export function startSmoothScroll() {
  if (lenis) return;
  lenis = new Lenis({ duration: 1.05, smoothWheel: true });
  lenis.on("scroll", ScrollTrigger.update);
  tickerFn = (time: number) => lenis?.raf(time * 1000);
  gsap.ticker.add(tickerFn);
  gsap.ticker.lagSmoothing(0);
}

export function stopSmoothScroll() {
  if (tickerFn) gsap.ticker.remove(tickerFn);
  lenis?.destroy();
  lenis = null;
  tickerFn = null;
}

/** Pauses Lenis's own scroll handling (e.g. while a lightbox is open) without
 *  tearing down the instance — `resumePageScroll` picks back up where it left off. */
export function pausePageScroll() {
  lenis?.stop();
}

export function resumePageScroll() {
  lenis?.start();
}

export { gsap, ScrollTrigger };
