import { useState } from "react";
import type { FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { api, ApiError } from "../../api/client";
import type { AuthResponse } from "../../api/client";
import { useInvalidateMe } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { HintMark } from "../../ui/HintMark";
import { Modal } from "../../ui/Modal";
import { toast } from "../../ui/toast";

const s = strings.finishSignup;

export function FinishSignup() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const invalidateMe = useInvalidateMe();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const token = params.get("token");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError(null);
    try {
      await api.post<AuthResponse>("/api/v1/auth/google/complete", {
        token,
        displayName: String(data.get("displayName") ?? "").trim(),
        handle: String(data.get("handle") ?? "").trim().toLowerCase(),
      });
      await invalidateMe();
      toast(s.welcome, "ok");
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : strings.auth.genericError);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal onClose={() => navigate("/", { replace: true })} size="sm" labelledBy="finish-signup">
      <div className="modal-head">
        <h2 id="finish-signup">{s.title}</h2>
      </div>
      <div className="modal-body">
        {token ? (
          <form className="form-grid" onSubmit={submit}>
            <p className="form-hint" style={{ margin: 0 }}>
              {s.intro}
            </p>
            <label>
              {strings.auth.displayName}
              <input name="displayName" required maxLength={50} defaultValue={params.get("name") ?? ""} />
            </label>
            <label>
              {strings.auth.handle}
              <HintMark hint={strings.auth.handleHelp} />
              <input
                name="handle"
                title={strings.auth.handleHelp}
                required
                minLength={3}
                maxLength={30}
                pattern="[A-Za-z0-9_]{3,30}"
                autoComplete="username"
                autoCapitalize="none"
                spellCheck={false}
                autoFocus
              />
            </label>
            {error && (
              <p className="error-note" role="alert">
                {error}
              </p>
            )}
            <button type="submit" className="primary block lg" disabled={busy}>
              {s.submit}
            </button>
          </form>
        ) : (
          <p className="empty-state">{s.expired}</p>
        )}
      </div>
    </Modal>
  );
}
