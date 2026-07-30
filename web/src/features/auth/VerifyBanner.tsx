import { useState } from "react";
import { api, ApiError } from "../../api/client";
import { useMe } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { toast } from "../../ui/toast";
import { SendIcon } from "../../ui/icons";

export function VerifyBanner() {
  const { data: me } = useMe();
  const [busy, setBusy] = useState(false);

  if (!me || me.emailVerified) return null;

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

  return (
    <div className="verify-banner" role="status">
      <span className="verify-text">{strings.verify.banner(me.email)}</span>
      <button type="button" className="sm" onClick={resend} disabled={busy}>
        <SendIcon size={14} /> {strings.verify.resend}
      </button>
    </div>
  );
}
