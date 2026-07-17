import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../../api/client";
import type { EventDetail } from "../../api/client";
import { useMe, useMeta } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { useBoardStore } from "../../stores/boardStore";
import { AuthPanel } from "../auth/AuthPanel";
import { TagInput } from "../tags/TagInput";

const s = strings.create;

function isoDate(daysFromNow: number): string {
  const d = new Date(Date.now() + daysFromNow * 86_400_000);
  return d.toISOString().slice(0, 10);
}

export function CreateEventFlow() {
  const { data: me, isLoading: meLoading } = useMe();
  const { data: meta } = useMeta();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const setCrosshair = useBoardStore((st) => st.setCrosshair);
  const draftLocation = useBoardStore((st) => st.draftLocation);

  const [placing, setPlacing] = useState(true);
  const [type, setType] = useState<string | null>(null);
  const [applyable, setApplyable] = useState(false);
  const [tags, setTags] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const signedIn = !!me;

  useEffect(() => {
    if (!signedIn) return;
    setCrosshair(true);
    return () => setCrosshair(false);
  }, [signedIn, setCrosshair]);

  useEffect(() => {
    if (meta && type === null) {
      setType(meta.types[0].key);
      setApplyable(meta.types[0].applyableDefault);
    }
  }, [meta, type]);

  if (meLoading) {
    return (
      <section className="drawer">
        <p>{strings.loading}</p>
      </section>
    );
  }

  if (!signedIn) {
    return (
      <section className="drawer">
        <button type="button" className="close" onClick={() => navigate("/")}>
          {s.cancel}
        </button>
        <h2>{s.title}</h2>
        <p>{strings.auth.signInToPin}</p>
        <AuthPanel />
      </section>
    );
  }

  function pickType(key: string) {
    setType(key);
    const t = meta?.types.find((x) => x.key === key);
    if (t) setApplyable(t.applyableDefault);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draftLocation || !type) return;
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError(null);
    try {
      const created = await api.post<EventDetail>("/api/v1/events", {
        type,
        title: String(data.get("title") ?? ""),
        body: String(data.get("body") ?? ""),
        location: draftLocation,
        applyable,
        expiresAt: `${String(data.get("expiresAt"))}T23:59:59Z`,
        tags,
      });
      await queryClient.invalidateQueries({ queryKey: ["events"] });
      navigate(`/events/${created.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : strings.auth.genericError);
    } finally {
      setBusy(false);
    }
  }

  if (placing || !draftLocation) {
    return (
      <section className="drawer">
        <button type="button" className="close" onClick={() => navigate("/")}>
          {s.cancel}
        </button>
        <h2>{s.title}</h2>
        <p>{draftLocation ? s.adjustHint : s.pickSpotHint}</p>
        {draftLocation && (
          <button type="button" className="primary" onClick={() => setPlacing(false)}>
            {s.continueToForm}
          </button>
        )}
      </section>
    );
  }

  const limits = meta?.limits;

  return (
    <section className="drawer">
      <button type="button" className="close" onClick={() => navigate("/")}>
        {s.cancel}
      </button>
      <h2>{s.title}</h2>
      <button type="button" onClick={() => setPlacing(true)}>
        {s.changeSpot}
      </button>
      <form className="form-grid" onSubmit={submit} style={{ marginTop: 12 }}>
        <span>{s.typeLabel}</span>
        <div className="type-pick">
          {meta?.types.map((t) => (
            <label key={t.key} className={type === t.key ? "selected" : ""}>
              <input
                type="radio"
                name="type"
                checked={type === t.key}
                onChange={() => pickType(t.key)}
              />
              <span className="type-dot" style={{ background: t.color }} />
              {t.label}
            </label>
          ))}
        </div>
        <label>
          {s.titleLabel}
          <input
            name="title"
            required
            minLength={limits?.titleMin ?? 3}
            maxLength={limits?.titleMax ?? 120}
          />
        </label>
        <label>
          {s.bodyLabel}
          <textarea name="body" required rows={6} maxLength={limits?.bodyMax ?? 4000} />
        </label>
        <label className="inline">
          <input
            type="checkbox"
            checked={applyable}
            onChange={(e) => setApplyable(e.target.checked)}
          />
          {s.applyableLabel}
        </label>
        <span className="meta-row">{s.applyableHelp}</span>
        <label>
          {s.expiresLabel}
          <input
            type="date"
            name="expiresAt"
            required
            defaultValue={isoDate(limits?.expiryDefaultDays ?? 30)}
            min={isoDate(1)}
            max={isoDate((limits?.expiryMaxDays ?? 90) - 1)}
          />
        </label>
        <label>
          {s.tagsLabel}
          <TagInput value={tags} onChange={setTags} max={limits?.tagsMax ?? 5} />
        </label>
        <button type="submit" className="primary" disabled={busy}>
          {s.submit}
        </button>
        {error && <p className="error-note" role="alert">{error}</p>}
      </form>
    </section>
  );
}
