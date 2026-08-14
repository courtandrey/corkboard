import { useState } from "react";
import type { FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { boardEvent } from "../../api/paths";
import { api, ApiError } from "../../api/client";
import type { EventDetail } from "../../api/client";
import { useMeta } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { TagInput } from "../tags/TagInput";

const s = strings.eventEdit;

export function EventEditForm({ event, onDone }: { event: EventDetail; onDone: () => void }) {
  const { data: meta } = useMeta();
  const queryClient = useQueryClient();
  const [type, setType] = useState(event.type as string);
  const [applyable, setApplyable] = useState(event.applyable);
  const [noEndDate, setNoEndDate] = useState(!event.expiresAt);
  const [tags, setTags] = useState(event.tags.map((t) => t.name));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const typeLocked = event.applicationCount > 0;
  const limits = meta?.limits;

  async function submit(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const data = new FormData(formEvent.currentTarget);
    setBusy(true);
    setError(null);
    try {
      await api.patch(boardEvent(event.boardOwnerId ?? null, event.id), {
        type: typeLocked ? undefined : type,
        title: String(data.get("title") ?? ""),
        body: String(data.get("body") ?? ""),
        applyable,
        expiresAt: noEndDate ? undefined : `${String(data.get("expiresAt"))}T23:59:59Z`,
        neverExpires: noEndDate ? true : undefined,
        tags,
      });
      await queryClient.invalidateQueries({ queryKey: ["event", event.id] });
      await queryClient.invalidateQueries({ queryKey: ["events"] });
      await queryClient.invalidateQueries({ queryKey: ["myEvents"] });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : strings.auth.genericError);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="form-grid" onSubmit={submit}>
      {typeLocked ? (
        <p className="meta-row">{s.typeLocked}</p>
      ) : (
        <div className="type-pick">
          {meta?.types.map((t) => (
            <label key={t.key} className={type === t.key ? "selected" : ""}>
              <input type="radio" name="type" checked={type === t.key} onChange={() => setType(t.key)} />
              <span className="type-dot" style={{ background: t.color }} />
              {t.label}
            </label>
          ))}
        </div>
      )}
      <label>
        {strings.create.titleLabel}
        <input
          name="title"
          required
          defaultValue={event.title}
          minLength={limits?.titleMin ?? 3}
          maxLength={limits?.titleMax ?? 120}
        />
      </label>
      <label>
        {strings.create.bodyLabel}
        <textarea name="body" required rows={6} defaultValue={event.body} maxLength={limits?.bodyMax ?? 4000} />
      </label>
      <label className="inline">
        <input type="checkbox" checked={applyable} onChange={(e) => setApplyable(e.target.checked)} />
        {strings.create.applyableLabel}
      </label>
      <label className="inline">
        <input type="checkbox" checked={noEndDate} onChange={(e) => setNoEndDate(e.target.checked)} />
        {strings.create.noEndDateLabel}
      </label>
      {!noEndDate && (
        <label>
          {strings.create.expiresLabel}
          <input
            type="date"
            name="expiresAt"
            required
            defaultValue={(event.expiresAt ? new Date(event.expiresAt) : new Date(Date.now() + 30 * 86_400_000))
              .toISOString()
              .slice(0, 10)}
            min={new Date(Date.now() + 86_400_000).toISOString().slice(0, 10)}
          />
        </label>
      )}
      <label>
        {strings.create.tagsLabel}
        <TagInput value={tags} onChange={setTags} max={limits?.tagsMax ?? 5} />
      </label>
      <div style={{ display: "flex", gap: 6 }}>
        <button type="submit" className="primary" disabled={busy}>
          {s.save}
        </button>
        <button type="button" onClick={onDone}>
          {s.cancel}
        </button>
      </div>
      {error && (
        <p className="error-note" role="alert">
          {error}
        </p>
      )}
    </form>
  );
}
