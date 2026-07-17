import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import type { EventDetail, VoteResponse } from "../../api/client";
import { strings } from "../../i18n/strings";

const s = strings.engagement;

export function EventActions({ event }: { event: EventDetail }) {
  const queryClient = useQueryClient();
  const [reportOpen, setReportOpen] = useState(false);
  const [reported, setReported] = useState(false);
  const key = ["event", event.id];

  const vote = useMutation({
    mutationFn: () => api.post<VoteResponse>(`/api/v1/events/${event.id}/vote`),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: key });
      const prev = queryClient.getQueryData<EventDetail>(key);
      if (prev) {
        queryClient.setQueryData<EventDetail>(key, {
          ...prev,
          score: prev.score + (prev.viewerState.voted ? -1 : 1),
          viewerState: { ...prev.viewerState, voted: !prev.viewerState.voted },
        });
      }
      return { prev };
    },
    onError: (_error, _vars, ctx) => {
      if (ctx?.prev) queryClient.setQueryData(key, ctx.prev);
    },
    onSuccess: (res) => {
      const current = queryClient.getQueryData<EventDetail>(key);
      if (current) {
        queryClient.setQueryData<EventDetail>(key, {
          ...current,
          score: res.score,
          viewerState: { ...current.viewerState, voted: res.voted },
        });
      }
      void queryClient.invalidateQueries({ queryKey: ["events"] });
    },
  });

  const hide = useMutation({
    mutationFn: () =>
      event.viewerState.hidden
        ? api.del(`/api/v1/events/${event.id}/hide`)
        : api.post(`/api/v1/events/${event.id}/hide`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: key });
      void queryClient.invalidateQueries({ queryKey: ["events"] });
    },
  });

  async function sendReport(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const data = new FormData(formEvent.currentTarget);
    await api.post(`/api/v1/events/${event.id}/report`, {
      reason: String(data.get("reason")),
      detail: String(data.get("detail") ?? "").trim() || undefined,
    });
    setReportOpen(false);
    setReported(true);
  }

  if (event.viewerState.isAuthor) return null;

  return (
    <div style={{ margin: "12px 0" }}>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        <button type="button" onClick={() => vote.mutate()} disabled={vote.isPending}>
          {event.viewerState.voted ? s.unvote : s.vote}
        </button>
        <button type="button" onClick={() => hide.mutate()} disabled={hide.isPending}>
          {event.viewerState.hidden ? s.unhide : s.hide}
        </button>
        <button type="button" onClick={() => setReportOpen(!reportOpen)}>
          {s.report}
        </button>
      </div>
      {reportOpen && (
        <form className="form-grid" onSubmit={sendReport} style={{ marginTop: 10 }}>
          <label>
            {s.reportReasonLabel}
            <select name="reason" required defaultValue="spam">
              {Object.entries(s.reasons).map(([keyName, label]) => (
                <option key={keyName} value={keyName}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            {s.reportDetailLabel}
            <textarea name="detail" rows={3} maxLength={500} />
          </label>
          <button type="submit" className="primary">
            {s.reportSend}
          </button>
        </form>
      )}
      {reported && <p className="meta-row">{s.reportThanks}</p>}
    </div>
  );
}
