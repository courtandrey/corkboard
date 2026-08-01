import { useState } from "react";
import { api, ApiError } from "../../api/client";
import { useMe } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { toast } from "../../ui/toast";
import { CloseIcon, SendIcon } from "../../ui/icons";

const DISMISSED_KEY = "corkboard.verify-banner-dismissed";

function dismissedFor(email: string): boolean {
  try {
    return localStorage.getItem(DISMISSED_KEY) === email;
  } catch {
    return false;
  }
}

export function VerifyBanner() {
  const { data: me } = useMe();
  const [busy, setBusy] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  if (!me || me.emailVerified || dismissed || dismissedFor(me.email)) return null;

  async function resend() {
    setBusy(true);
    try {
      await api.post("/api/v1/auth/verification/resend");
      toast(strings.verify.resent);
    } catch (error) {
      toast(error instanceof ApiError ? strings.verify.resendFailed : strings.auth.genericError, "info");
    } finally {
      setBusy(false);
    }
  }

  function dismiss() {
    try {
      localStorage.setItem(DISMISSED_KEY, me!.email);
    } catch {
      // a browser refusing storage simply gets the banner again next load
    }
    setDismissed(true);
  }

  return (
    <div className="verify-banner" role="status">
      <span className="verify-text">{strings.verify.banner(me.email)}</span>
      <button type="button" className="sm" onClick={resend} disabled={busy}>
        <SendIcon size={14} /> {strings.verify.resend}
      </button>
      <button
        type="button"
        className="icon-btn verify-dismiss"
        onClick={dismiss}
        aria-label={strings.verify.dismiss}
        title={strings.verify.dismiss}
      >
        <CloseIcon size={15} />
      </button>
    </div>
  );
}
