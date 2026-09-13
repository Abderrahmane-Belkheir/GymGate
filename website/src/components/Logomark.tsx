interface LogomarkProps {
  size?: number;
  className?: string;
}

// Real crop's aspect ratio (public/logo-mark.png is 324×389).
const ASPECT = 324 / 389;

/**
 * GymGate's actual shield mark — cropped from the real brand logo
 * (src/main/resources/images/logo.png in the app repo) rather than redrawn,
 * so the site shows the same face-recognition shield a gym owner already
 * sees in the product itself.
 */
export default function Logomark({ size = 28, className }: LogomarkProps) {
  return (
    <img
      src="/logo-mark.png"
      alt=""
      aria-hidden="true"
      width={Math.round(size * ASPECT)}
      height={size}
      className={className}
    />
  );
}
