import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { api, ApiError } from "../../api/client";
import type { AuthResponse, UserResponse } from "../../api/client";
import { strings } from "../../i18n/strings";

const s = strings.auth;

export function AuthPanel({ googleAuth }: { googleAuth: boolean }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [booting, setBooting] = useState(true);
  const [mode, setMode] = useState<"signIn" | "register">("signIn");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api
      .get<AuthResponse>("/api/v1/auth/me")
      .then((res) => setUser(res.user))
      .catch(() => setUser(null))
      .finally(() => setBooting(false));
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
      const res =
        mode === "signIn"
          ? await api.post<AuthResponse>("/api/v1/auth/login", { email, password })
          : await api.post<AuthResponse>("/api/v1/auth/register", {
              email,
              password,
              displayName: String(data.get("displayName") ?? ""),
            });
      setUser(res.user);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : s.genericError);
    } finally {
      setBusy(false);
    }
  }

  async function signOut() {
    await api.post("/api/v1/auth/logout");
    setUser(null);
  }

  if (booting) {
    return <p>{strings.loading}</p>;
  }

  if (user) {
    return (
      <section>
        <p>{s.signedInAs(user.displayName)}</p>
        <button type="button" onClick={signOut}>
          {s.signOut}
        </button>
      </section>
    );
  }

  return (
    <section>
      <form onSubmit={submit}>
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
          <input name="password" type="password" required minLength={8} autoComplete="current-password" />
        </label>
        <button type="submit" disabled={busy}>
          {mode === "signIn" ? s.signIn : s.register}
        </button>
      </form>
      {googleAuth && <a href="/api/v1/auth/google">{s.googleSignIn}</a>}
      <button type="button" onClick={() => setMode(mode === "signIn" ? "register" : "signIn")}>
        {mode === "signIn" ? s.switchToRegister : s.switchToSignIn}
      </button>
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
