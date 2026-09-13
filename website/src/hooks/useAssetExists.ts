import { useEffect, useState } from "react";

interface AssetCheck {
  /** True once the check has resolved (either way) — lets a caller show a
   *  neutral "checking" state instead of briefly claiming "missing". */
  checked: boolean;
  exists: boolean;
}

/**
 * Checks once whether a static asset genuinely exists — and is really the
 * expected file type — before any component points a real `<img>`/`<video>`
 * at it. A plain `res.ok` HEAD check isn't enough: Vite's dev server (and
 * most static hosts with SPA fallback, e.g. Netlify/Vercel) answer an
 * unmatched path with a 200 + index.html rather than a 404, which would
 * otherwise read as "the file exists." Also avoids the console error a
 * browser logs when an element's own request 404s.
 */
export function useAssetExists(src: string, mimePrefix: string): AssetCheck {
  const [state, setState] = useState<AssetCheck>({ checked: false, exists: false });

  useEffect(() => {
    let cancelled = false;
    fetch(src, { method: "HEAD" })
      .then((res) => {
        const type = res.headers.get("content-type") ?? "";
        if (!cancelled) setState({ checked: true, exists: res.ok && type.startsWith(mimePrefix) });
      })
      .catch(() => {
        if (!cancelled) setState({ checked: true, exists: false });
      });
    return () => {
      cancelled = true;
    };
  }, [src, mimePrefix]);

  return state;
}
