import { useCallback, useEffect, useState } from "react";

const KEY = "corkboard.cookie-consent";
const CHANGED = "corkboard:consent";

export type Consent = "essential" | "all";

export function readConsent(): Consent | null {
  try {
    const stored = localStorage.getItem(KEY);
    return stored === "essential" || stored === "all" ? stored : null;
  } catch {
    return null;
  }
}

export function analyticsAllowed(): boolean {
  return readConsent() === "all";
}

export function saveConsent(consent: Consent): void {
  try {
    localStorage.setItem(KEY, consent);
  } catch {
    // a browser refusing storage simply gets asked again next visit
  }
  window.dispatchEvent(new CustomEvent(CHANGED));
}

export function useConsent(): [Consent | null, (consent: Consent) => void] {
  const [consent, setConsent] = useState<Consent | null>(readConsent);

  useEffect(() => {
    const sync = () => setConsent(readConsent());
    window.addEventListener(CHANGED, sync);
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener(CHANGED, sync);
      window.removeEventListener("storage", sync);
    };
  }, []);

  const choose = useCallback((next: Consent) => saveConsent(next), []);
  return [consent, choose];
}
