import { Fragment, useState } from "react";
import type { FormEvent } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../../api/client";
import type { ApplyResponse } from "../../api/client";
import { useEventDetail, useMe, useMeta } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { boardPath } from "../../api/paths";
import { boardInPath } from "../../stores/boardStore";
import { Modal } from "../../ui/Modal";
import { toast } from "../../ui/toast";
import { useNoteFontReady } from "../../ui/noteFont";
import { ChatIcon, ClockIcon, FlagIcon, HideIcon, SendIcon, ShowIcon, TrashIcon } from "../../ui/icons";
import { PersonLink } from "../connections/personCard";
import { useVerifyGate } from "../auth/verifyGate";
import { usePermissions } from "../../ui/permissions";
import { VoteControl } from "./VoteControl";
import { EventEditForm } from "./EventEditForm";

const eng = strings.engagement;

function Linkified({ text }: { text: string }) {
  const parts = text.split(/(https?:\/\/[^\s]+)/g);
  return (
    <>
      {parts.map((part, i) =>
        /^https?:\/\//.test(part) ? (
          <a key={i} href={part} target="_blank" rel="noopener nofollow ugc">
            {part}
          </a>
        ) : (
          <Fragment key={i}>{part}</Fragment>
        ),
      )}
    </>
  );
}

type Mode = "view" | "respond" | "report" | "takedown" | "edit";

export function EventDrawer() {
  const { id } = useParams();
  const board = boardInPath(useLocation().pathname);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: meta } = useMeta();
  const { data: me } = useMe();
  const { data: event, error } = useEventDetail(id, board);
  const [mode, setMode] = useState<Mode>("view");
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const { allows, guard, block } = useVerifyGate();
  const { can } = usePermissions();
  const canTakeDown = can("EVENT_TAKE_DOWN_ANY") && !event?.viewerState.isAuthor;
  const titleReady = useNoteFontReady(event?.title);

  const close = () => navigate(boardPath(board));
  const type = meta?.types.find((t) => t.key === event?.type);

  const takeDown = useMutation({
    mutationFn: () => api.post(`/api/v1/admin/events/${event!.id}/takedown`),
    onSuccess: async () => {
      toast(strings.moderation.tookDown);
      await queryClient.invalidateQueries({ queryKey: ["event", event!.id] });
      await queryClient.invalidateQueries({ queryKey: ["events"] });
      await queryClient.invalidateQueries({ queryKey: ["admin", "reports"] });
      setMode("view");
    },
  });

  const hide = useMutation({
    mutationFn: () =>
      event!.viewerState.hidden
        ? api.del(`/api/v1/events/${event!.id}/hide`)
        : api.post(`/api/v1/events/${event!.id}/hide`),
    onSuccess: () => {
      toast(event!.viewerState.hidden ? strings.toasts.unhidden : strings.toasts.hidden);
      void queryClient.invalidateQueries({ queryKey: ["event", event!.id] });
      void queryClient.invalidateQueries({ queryKey: ["events"] });
    },
  });

  const apply = useMutation({
    mutationFn: (message: string) =>
      api.post<ApplyResponse>(`/api/v1/events/${event!.id}/apply`, { message }),
    onSuccess: async (res) => {
      setConversationId(res.conversationId);
      setMode("view");
      toast(strings.toasts.responseSent);
      await queryClient.invalidateQueries({ queryKey: ["event", event!.id] });
      await queryClient.invalidateQueries({ queryKey: ["conversations"] });
    },
    onError: (e) => setFormError(e instanceof ApiError ? e.message : strings.auth.genericError),
  });

  const report = useMutation({
    mutationFn: (body: { reason: string; detail?: string }) =>
      api.post(`/api/v1/events/${event!.id}/report`, body),
    onSuccess: () => {
      setMode("view");
      toast(eng.reportThanks);
    },
  });

  function submitRespond(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const message = String(new FormData(e.currentTarget).get("message") ?? "").trim();
    if (!message) return;
    setFormError(null);
    apply.mutate(message);
  }

  function submitReport(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    report.mutate({
      reason: String(data.get("reason")),
      detail: String(data.get("detail") ?? "").trim() || undefined,
    });
  }

  return (
    <Modal onClose={close} size="md" labelledBy="ev-title">
      {error && <p className="empty-state error-note">{strings.event.notFound}</p>}
      {(!event || !titleReady) && !error && <p className="empty-state">{strings.loading}</p>}

      {event && titleReady && mode === "edit" && (
        <>
          <div className="modal-head">
            <h2>{strings.eventEdit.edit}</h2>
          </div>
          <div className="modal-body" style={{ paddingBottom: 18 }}>
            <EventEditForm
              event={event}
              onDone={() => {
                setMode("view");
                toast(strings.toasts.saved);
              }}
            />
          </div>
        </>
      )}

      {event && titleReady && mode !== "edit" && (
        <>
          <div className="ev-head">
            {type && (
              <span className="type-chip" style={{ background: type.color }}>
                {type.label}
              </span>
            )}
            {event.scope === "global" && (
              <VoteControl
                event={event}
                interactive={!!me && !event.viewerState.isAuthor}
                hint={
                  !me ? eng.voteSignedOut : event.viewerState.isAuthor ? eng.voteOwn : undefined
                }
                intercept={() => {
                  if (allows("vote")) return false;
                  block("vote");
                  return true;
                }}
              />
            )}
            {event.status !== "active" && (
              <span className={`stamp${event.status === "resolved" ? "" : " muted"}`}>
                {strings.myPins.statusHeading[event.status] ?? event.status}
              </span>
            )}
          </div>

          <div className="ev-scroll">
            <h2 id="ev-title" className="ev-title">
              {event.title}
            </h2>
            <div className="ev-meta">
              <span className="who">
                <PersonLink
                  userId={event.author.id}
                  displayName={strings.event.postedBy(event.author.displayName)}
                  handle={event.author.handle}
                  avatarSeed={event.author.avatarSeed}
                />
              </span>
              <span className="dot">·</span>
              <span className="with-icon">
                <ClockIcon size={13} />
                {event.expiresAt
                  ? strings.event.expires(new Date(event.expiresAt).toLocaleDateString())
                  : strings.event.noEndDate}
              </span>
              {event.updatedAt > event.createdAt && (
                <>
                  <span className="dot">·</span>
                  <span>{strings.event.edited}</span>
                </>
              )}
            </div>
            <p className="ev-text">
              <Linkified text={event.body} />
            </p>
            {event.tags.length > 0 && (
              <div className="ev-tags">
                {event.tags.map((tag) => (
                  <span key={tag.slug} className="tag-chip">
                    {tag.name}
                  </span>
                ))}
              </div>
            )}
          </div>

          {mode === "respond" && (
            <form className="ev-foot" style={{ flexDirection: "column", alignItems: "stretch", gap: 8 }} onSubmit={submitRespond}>
              <textarea name="message" rows={3} maxLength={2000} placeholder={strings.apply.placeholder} required autoFocus />
              {formError && <p className="error-note">{formError}</p>}
              <div style={{ display: "flex", gap: 8 }}>
                <button type="submit" className="primary grow" disabled={apply.isPending}>
                  <SendIcon size={16} /> {strings.apply.send}
                </button>
                <button type="button" className="ghost" onClick={() => setMode("view")}>
                  {strings.apply.cancel}
                </button>
              </div>
            </form>
          )}

          {mode === "report" && (
            <form className="ev-foot" style={{ flexDirection: "column", alignItems: "stretch", gap: 10 }} onSubmit={submitReport}>
              <label className="form-grid" style={{ gap: 5 }}>
                {eng.reportReasonLabel}
                <select name="reason" required defaultValue="spam">
                  {Object.entries(eng.reasons).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </label>
              <textarea name="detail" rows={2} maxLength={500} placeholder={eng.reportDetailLabel} />
              <div style={{ display: "flex", gap: 8 }}>
                <button type="submit" className="danger grow" disabled={report.isPending}>
                  <FlagIcon size={15} /> {eng.reportSend}
                </button>
                <button type="button" className="ghost" onClick={() => setMode("view")}>
                  {eng.reportCancel}
                </button>
              </div>
            </form>
          )}

          {mode === "takedown" && (
            <div className="ev-foot" style={{ flexDirection: "column", alignItems: "stretch", gap: 10 }}>
              <p className="form-hint" style={{ margin: 0 }}>
                {strings.moderation.takeDownConfirm}
              </p>
              <div style={{ display: "flex", gap: 8 }}>
                <button
                  type="button"
                  className="danger grow"
                  disabled={takeDown.isPending}
                  onClick={() => takeDown.mutate()}
                >
                  <TrashIcon size={15} /> {strings.moderation.takeDownYes}
                </button>
                <button type="button" className="ghost" onClick={() => setMode("view")}>
                  {strings.moderation.takeDownNo}
                </button>
              </div>
            </div>
          )}
          {mode === "view" && event.scope === "personal" && event.viewerState.isAuthor && (
            <div className="ev-foot">
              <span className="grow meta-row" style={{ margin: 0 }}>
                {strings.scope.personalHint}
              </span>
              <button type="button" className="ghost" onClick={() => setMode("edit")}>
                {strings.event.edit}
              </button>
            </div>
          )}
          {mode === "view" && event.scope === "personal" && !event.viewerState.isAuthor && (
            <div className="ev-foot">
              {conversationId || event.viewerState.applied ? (
                <span className="grow meta-row" style={{ margin: 0 }}>
                  {conversationId ? strings.apply.sent : strings.apply.alreadyApplied}{" "}
                  <Link to={conversationId ? `/messages/${conversationId}` : "/messages"}>
                    {strings.apply.goToConversation}
                  </Link>
                </span>
              ) : event.viewerState.canRespond ? (
                <button
                  type="button"
                  className="primary grow"
                  onClick={() => {
                    if (!me) navigate("/login");
                    else guard("respond", () => setMode("respond"))();
                  }}
                >
                  <ChatIcon size={16} /> {strings.apply.respond}
                </button>
              ) : (
                <span className="grow meta-row" style={{ margin: 0 }}>
                  {strings.scope.sharedWithYou}
                </span>
              )}
            </div>
          )}
          {mode === "view" && event.scope === "global" && (
            <div className="ev-foot">
              {conversationId ? (
                <span className="grow meta-row" style={{ margin: 0 }}>
                  {strings.apply.sent}{" "}
                  <Link to={`/messages/${conversationId}`}>{strings.apply.goToConversation}</Link>
                </span>
              ) : event.viewerState.isAuthor ? (
                <button type="button" className="ghost grow" onClick={() => setMode("edit")}>
                  {strings.event.edit}
                </button>
              ) : (
                <>
                  {me && event.viewerState.applied ? (
                    <span className="grow meta-row" style={{ margin: 0 }}>
                      {strings.apply.alreadyApplied}{" "}
                      <Link to="/messages">{strings.apply.goToConversation}</Link>
                    </span>
                  ) : event.viewerState.canRespond ? (
                    <button
                      type="button"
                      className="primary grow"
                      onClick={() => {
                        if (!me) navigate("/login");
                        else guard("respond", () => setMode("respond"))();
                      }}
                    >
                      <ChatIcon size={16} /> {strings.apply.respond}
                    </button>
                  ) : (
                    <span className="grow" />
                  )}
                  {me && (
                    <>
                      <button
                        type="button"
                        className="icon-btn"
                        onClick={() => hide.mutate()}
                        disabled={hide.isPending}
                        aria-label={event.viewerState.hidden ? eng.unhide : eng.hide}
                        title={event.viewerState.hidden ? eng.unhide : eng.hide}
                      >
                        {event.viewerState.hidden ? <ShowIcon size={18} /> : <HideIcon size={18} />}
                      </button>
                      {canTakeDown ? (
                        <button
                          type="button"
                          className="icon-btn danger-icon"
                          onClick={() => setMode("takedown")}
                          aria-label={strings.moderation.takeDown}
                          title={strings.moderation.takeDown}
                        >
                          <TrashIcon size={18} />
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="icon-btn"
                          onClick={guard("report", () => setMode("report"))}
                          aria-label={eng.report}
                          title={eng.report}
                        >
                          <FlagIcon size={18} />
                        </button>
                      )}
                    </>
                  )}
                </>
              )}
            </div>
          )}
        </>
      )}
    </Modal>
  );
}
