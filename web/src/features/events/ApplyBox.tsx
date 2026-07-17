import { useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../../api/client";
import type { ApplyResponse, EventDetail } from "../../api/client";
import { strings } from "../../i18n/strings";

const s = strings.apply;

export function ApplyBox({ event }: { event: EventDetail }) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const queryClient = useQueryClient();

  if (event.viewerState.isAuthor || !event.applyable || event.status !== "active") return null;

  if (conversationId) {
    return (
      <p>
        {s.sent} <Link to={`/messages/${conversationId}`}>{s.goToConversation}</Link>
      </p>
    );
  }

  if (event.viewerState.applied) {
    return (
      <p className="meta-row">
        {s.alreadyApplied} <Link to="/messages">{s.goToConversation}</Link>
      </p>
    );
  }

  async function submit(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const message = String(new FormData(formEvent.currentTarget).get("message") ?? "").trim();
    if (!message) return;
    setBusy(true);
    setError(null);
    try {
      const res = await api.post<ApplyResponse>(`/api/v1/events/${event.id}/apply`, { message });
      setConversationId(res.conversationId);
      await queryClient.invalidateQueries({ queryKey: ["event", event.id] });
      await queryClient.invalidateQueries({ queryKey: ["conversations"] });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : strings.auth.genericError);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ margin: "12px 0" }}>
      {!open ? (
        <button type="button" className="primary" onClick={() => setOpen(true)}>
          {s.respond}
        </button>
      ) : (
        <form className="form-grid" onSubmit={submit}>
          <textarea name="message" rows={3} maxLength={2000} placeholder={s.placeholder} required />
          <button type="submit" className="primary" disabled={busy}>
            {s.send}
          </button>
          {error && (
            <p className="error-note" role="alert">
              {error}
            </p>
          )}
        </form>
      )}
    </div>
  );
}
