import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { api, ApiError } from "../../api/client";
import type { AuthResponse } from "../../api/client";
import { useInvalidateMe, useMe, useMeta } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { HintMark } from "../../ui/HintMark";

const s = strings.auth;

export function AuthPanel({ onSignedIn }: { onSignedIn?: () => void }) {
  const { data: me } = useMe();
  const { data: meta } = useMeta();
  const invalidateMe = useInvalidateMe();
  const [mode, setMode] = useState<"signIn" | "register">("signIn");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (new URLSearchParams(window.location.search).get("authError") === "google") {
      setError(s.googleFailed);
    }
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const email = String(data.get("email") ?? "");
    const password = String(data.get("password") ?? "");
    setBusy(true);
    setError(null);
    try {
      if (mode === "signIn") {
        await api.post<AuthResponse>("/api/v1/auth/login", { email, password });
      } else {
        await api.post<AuthResponse>("/api/v1/auth/register", {
          email,
          password,
          displayName: String(data.get("displayName") ?? ""),
          handle: String(data.get("handle") ?? "").trim().toLowerCase(),
        });
      }
      await invalidateMe();
      onSignedIn?.();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : s.genericError);
    } finally {
      setBusy(false);
    }
  }

  if (me) {
    return <p className="meta-row" style={{ margin: 0 }}>{s.signedInAs(me.displayName)}</p>;
  }

  return (
    <section>
      <form className="form-grid" onSubmit={submit}>
        <label>
          {s.email}
          <input name="email" type="email" required autoComplete="email" />
        </label>
        {mode === "register" && (
          <>
            <label>
              {s.displayName}
              <input name="displayName" required maxLength={50} />
            </label>
            <label>
              {s.handle}
              <HintMark hint={s.handleHelp} />
              <input
                name="handle"
                title={s.handleHelp}
                required
                minLength={3}
                maxLength={30}
                pattern="[A-Za-z0-9_]{3,30}"
                autoComplete="username"
                autoCapitalize="none"
                spellCheck={false}
              />
            </label>
          </>
        )}
        <label>
          {s.password}
          <input
            name="password"
            type="password"
            required
            minLength={8}
            autoComplete={mode === "register" ? "new-password" : "current-password"}
          />
        </label>
        {error && (
          <p className="error-note" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="primary block lg" disabled={busy}>
          {mode === "signIn" ? s.signIn : s.register}
        </button>
      </form>
      {meta?.googleAuth && (
        <a href="/api/v1/auth/google" className="google-btn" role="button">
          {s.googleSignIn}
        </a>
      )}
      <button
        type="button"
        className="quiet block"
        onClick={() => setMode(mode === "signIn" ? "register" : "signIn")}
        style={{ marginTop: 10 }}
      >
        {mode === "signIn" ? s.switchToRegister : s.switchToSignIn}
      </button>
    </section>
  );
}
