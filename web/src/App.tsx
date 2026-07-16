import { useEffect, useState } from "react";
import { api } from "./api/client";
import type { MetaResponse } from "./api/client";
import { AuthPanel } from "./features/auth/AuthPanel";
import { strings } from "./i18n/strings";

export function App() {
  const [meta, setMeta] = useState<MetaResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<MetaResponse>("/api/v1/meta")
      .then(setMeta)
      .catch((e: Error) => setError(e.message));
  }, []);

  return (
    <main style={{ fontFamily: "Tahoma, Verdana, sans-serif", padding: 24 }}>
      <h1>{strings.appName}</h1>
      {error && <p>{strings.apiUnreachable(error)}</p>}
      {!meta && !error && <p>{strings.loading}</p>}
      {meta && (
        <>
          <AuthPanel googleAuth={meta.googleAuth} />
          <ul>
            {meta.types.map((t) => (
              <li key={t.key} style={{ color: t.color }}>
                {t.label}
              </li>
            ))}
          </ul>
        </>
      )}
    </main>
  );
}
