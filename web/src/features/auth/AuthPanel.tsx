import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { api, ApiError } from "../../api/client";
import type { AuthResponse } from "../../api/client";
import { useInvalidateMe, useMe, useMeta } from "../../api/hooks";
import { strings } from "../../i18n/strings";

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
    return <p>{s.signedInAs(me.displayName)}</p>;
  }

  return (
    <section>
      <form className="form-grid" onSubmit={submit}>
        <label>
          {s.email}
          <input name="email" type="email" required autoComplete="email" />
        </label>
        {mode === "register" && (
          <label>
            {s.displayName}
            <input name="displayName" required maxLength={50} />
          </label>
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
        <button type="submit" className="primary" disabled={busy}>
          {mode === "signIn" ? s.signIn : s.register}
        </button>
      </form>
      {meta?.googleAuth && (
        <p>
          <a href="/api/v1/auth/google">{s.googleSignIn}</a>
        </p>
      )}
      <button
        type="button"
        onClick={() => setMode(mode === "signIn" ? "register" : "signIn")}
        style={{ marginTop: 8 }}
      >
        {mode === "signIn" ? s.switchToRegister : s.switchToSignIn}
      </button>
      {error && (
        <p className="error-note" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
