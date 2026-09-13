import "./KioskDiagram.css";

export type KioskStage = "full" | "camera" | "display" | "enclosure" | "mount";

interface KioskDiagramProps {
  stage?: KioskStage;
  className?: string;
}

/**
 * A technical line-drawing of the GymGate kiosk rather than a product photo —
 * there isn't a finished enclosure shot worth putting in front of a gym
 * owner yet, and a spec-sheet illustration reads as more deliberate than a
 * stretched phone photo would. `stage` dims every group except the one being
 * called out, which is how the hardware section narrates part by part
 * without needing four separate illustrations.
 */
export default function KioskDiagram({ stage = "full", className }: KioskDiagramProps) {
  const cls = (group: KioskStage) =>
    "kd-group" + (stage !== "full" && stage !== group ? " kd-dim" : "") + (stage === group ? " kd-active" : "");

  return (
    <svg
      viewBox="0 0 360 420"
      className={`kiosk-diagram${className ? ` ${className}` : ""}`}
      role="img"
      aria-label="Line diagram of the GymGate check-in kiosk: camera, display, enclosure and mount"
    >
      {/* camera — a clip-on bar camera perched above the case, not a lens
          built into the bezel */}
      <g className={cls("camera")} data-part="camera">
        <path d="M167 84 L193 84 L199 122 L161 122 Z" />
        <rect x="105" y="40" width="150" height="44" rx="22" />
        <circle cx="180" cy="62" r="14" />
        <circle cx="180" cy="62" r="5.5" className="kd-solid" />
        <circle cx="228" cy="62" r="4" />
      </g>

      {/* enclosure — a squat, near-square case rather than a tall phone-like
          body */}
      <g className={cls("enclosure")} data-part="enclosure">
        <rect x="60" y="122" width="240" height="230" rx="20" />
        <rect x="60" y="122" width="240" height="230" rx="20" className="kd-fill" />
        <circle cx="180" cy="316" r="7" />
      </g>

      {/* display — a landscape panel inset in the case, not a full-height
          screen */}
      <g className={cls("display")} data-part="display">
        <rect x="88" y="162" width="184" height="118" rx="8" />
        <rect x="88" y="162" width="184" height="118" rx="8" className="kd-screen" />

        {/* recognition frame, mirroring <Frame/> at kiosk scale */}
        <g className="kd-recog">
          <path d="M145 185v-12h12" />
          <path d="M215 185v-12h-12" />
          <path d="M145 215v12h12" />
          <path d="M215 215v12h-12" />
          <circle cx="180" cy="200" r="14" />
        </g>

        {/* status readout */}
        <line x1="150" y1="245" x2="210" y2="245" className="kd-text-bar kd-text-bar-strong" />
        <line x1="158" y1="260" x2="202" y2="260" className="kd-text-bar" />
        <line x1="166" y1="272" x2="194" y2="272" className="kd-text-bar" />
      </g>

      {/* mount — a compact kickstand under the case */}
      <g className={cls("mount")} data-part="mount">
        <path d="M135 352 L225 352 L215 392 L145 392 Z" />
        <line x1="135" y1="352" x2="225" y2="352" />
      </g>
    </svg>
  );
}
