import { strings } from "../../i18n/strings";
import { useConsent } from "../../ui/consent";

const s = strings.consent;

export function CookieBanner() {
  const [consent, choose] = useConsent();

  if (consent) return null;

  return (
    <div className="cookie-banner" role="region" aria-label={s.title}>
      <p className="cookie-text">
        <strong>{s.title}</strong> {s.body}
      </p>
      <div className="cookie-actions">
        <button type="button" className="ghost sm" onClick={() => choose("essential")}>
          {s.essentialOnly}
        </button>
        <button type="button" className="primary sm" onClick={() => choose("all")}>
          {s.acceptAll}
        </button>
      </div>
    </div>
  );
}
