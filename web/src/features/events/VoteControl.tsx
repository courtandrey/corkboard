import { useEffect, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import type { EventDetail, VoteResponse } from "../../api/client";
import { strings } from "../../i18n/strings";
import { UpvoteIcon } from "../../ui/icons";

export function VoteControl({
  event,
  interactive,
  hint,
}: {
  event: EventDetail;
  interactive: boolean;
  hint?: string;
}) {
  const queryClient = useQueryClient();
  const key = ["event", event.id];
  const [bump, setBump] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const vote = useMutation({
    mutationFn: () => api.post<VoteResponse>(`/api/v1/events/${event.id}/vote`),
    onMutate: async () => {
      setBump(true);
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
    onError: (_e, _v, ctx) => ctx?.prev && queryClient.setQueryData(key, ctx.prev),
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

  useEffect(() => {
    if (!bump) return;
    const t = window.setTimeout(() => setBump(false), 340);
    return () => window.clearTimeout(t);
  }, [bump]);

  const voted = event.viewerState.voted;
  const cls = `vote${interactive ? "" : " static"}${voted ? " on" : ""}${bump ? " bump" : ""}`;

  const content = (
    <>
      <UpvoteIcon size={16} />
      <span className="vote-count">{event.score}</span>
      <span className="vote-label">{strings.board.pointsLabel(event.score)}</span>
    </>
  );

  if (!interactive) {
    return (
      <div
        ref={ref}
        className={cls}
        title={hint}
        aria-label={hint ? `${strings.board.points(event.score)} — ${hint}` : strings.board.points(event.score)}
      >
        {content}
      </div>
    );
  }

  return (
    <button
      type="button"
      className={cls}
      onClick={() => vote.mutate()}
      disabled={vote.isPending}
      aria-pressed={voted}
      aria-label={voted ? strings.engagement.unvote : strings.engagement.vote}
    >
      {content}
    </button>
  );
}
