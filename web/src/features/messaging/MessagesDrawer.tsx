import { useCallback, useEffect, useLayoutEffect, useMemo, useRef } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import type { ConversationSummary, MessageResponse } from "../../api/client";
import { useConversations, useMe, useMessages } from "../../api/hooks";
import { notePath } from "../../api/paths";
import { useBoardHome } from "../../stores/boardStore";
import { strings } from "../../i18n/strings";
import { handleOf } from "../../ui/handle";
import { Modal } from "../../ui/Modal";
import { PixelAvatar } from "../../ui/PixelAvatar";
import { useIsPhone } from "../../ui/useMediaQuery";
import { useVerifyGate } from "../auth/verifyGate";
import { BackIcon, SendIcon } from "../../ui/icons";

const s = strings.messagesUi;

function ConversationRow({ conversation, active }: { conversation: ConversationSummary; active: boolean }) {
  return (
    <Link to={`/messages/${conversation.id}`} className={`conv-row${active ? " active" : ""}`}>
      <span className="conv-name">
        <PixelAvatar seed={conversation.otherParty.avatarSeed} size={18} />
        {conversation.otherParty.displayName}
        <span className="user-handle">{handleOf(conversation.otherParty.handle)}</span>
        {conversation.unreadCount > 0 && <span className="badge">{conversation.unreadCount}</span>}
      </span>
      {conversation.lastMessageBody && <div className="conv-snippet">{conversation.lastMessageBody}</div>}
    </Link>
  );
}

function Thread({ conversation, onBack }: { conversation: ConversationSummary; onBack?: () => void }) {
  const { data: me } = useMe();
  const { allows, block } = useVerifyGate();
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage } = useMessages(conversation.id);
  const queryClient = useQueryClient();
  const scrollRef = useRef<HTMLDivElement>(null);
  const anchorRef = useRef<{ height: number; top: number } | null>(null);
  const pinnedToNewest = useRef<string | null>(null);

  const messages = useMemo(
    () => (data ? [...data.pages].reverse().flatMap((page) => page.items) : []),
    [data],
  );

  useEffect(() => {
    if (conversation.unreadCount > 0) {
      void api.post(`/api/v1/conversations/${conversation.id}/read`).then(() => {
        void queryClient.invalidateQueries({ queryKey: ["conversations"] });
        void queryClient.invalidateQueries({ queryKey: ["notifications"] });
      });
    }
  }, [conversation.id, conversation.unreadCount, queryClient]);

  useLayoutEffect(() => {
    const el = scrollRef.current;
    if (!el || messages.length === 0) return;
    const anchor = anchorRef.current;
    if (anchor) {
      el.scrollTop = el.scrollHeight - anchor.height + anchor.top;
      anchorRef.current = null;
      return;
    }
    const newest = messages[messages.length - 1].id;
    if (pinnedToNewest.current === newest) return;
    const opening = pinnedToNewest.current === null;
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 140;
    pinnedToNewest.current = newest;
    if (opening || nearBottom) el.scrollTop = el.scrollHeight;
  }, [messages]);

  const loadOlder = useCallback(() => {
    const el = scrollRef.current;
    if (!el || !hasNextPage || isFetchingNextPage) return;
    anchorRef.current = { height: el.scrollHeight, top: el.scrollTop };
    void fetchNextPage();
  }, [fetchNextPage, hasNextPage, isFetchingNextPage]);

  function onScroll() {
    const el = scrollRef.current;
    if (el && el.scrollTop < 48) loadOlder();
  }

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!allows("message")) {
      block("message");
      return;
    }
    const form = event.currentTarget;
    const body = String(new FormData(form).get("body") ?? "").trim();
    if (!body) return;
    await api.post<MessageResponse>(`/api/v1/conversations/${conversation.id}/messages`, { body });
    form.reset();
    await queryClient.invalidateQueries({ queryKey: ["messages", conversation.id] });
    await queryClient.invalidateQueries({ queryKey: ["conversations"] });
  }

  return (
    <div className="thread">
      <div className="thread-header">
        {onBack && (
          <button type="button" className="quiet sm thread-back" onClick={onBack}>
            <BackIcon size={14} /> {s.backToList}
          </button>
        )}
        <span className="thread-who">
          <PixelAvatar seed={conversation.otherParty.avatarSeed} size={20} />
          {conversation.otherParty.displayName}
          <span className="user-handle">{handleOf(conversation.otherParty.handle)}</span>
        </span>
      </div>
      <div className="thread-messages" ref={scrollRef} onScroll={onScroll}>
        {hasNextPage && (
          <button type="button" className="quiet sm load-older" onClick={loadOlder} disabled={isFetchingNextPage}>
            {isFetchingNextPage ? s.loadingOlder : s.loadOlder}
          </button>
        )}
        {messages.map((m) => (
          <div key={m.id} className={`bubble${m.senderId === me?.id ? " mine" : ""}`}>
            {m.event && (
              <Link className="bubble-note" to={notePath(null, m.event.id)}>
                {s.aboutNote} {m.event.title}
              </Link>
            )}
            {m.body}
            <span className="bubble-time">
              {new Date(m.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
            </span>
          </div>
        ))}
      </div>
      <form className="thread-input" onSubmit={send}>
        <input name="body" placeholder={s.inputPlaceholder} maxLength={2000} autoComplete="off" />
        <button type="submit" className="primary" aria-label={s.send}>
          <SendIcon size={16} />
        </button>
      </form>
    </div>
  );
}

export function MessagesDrawer() {
  const { conversationId } = useParams();
  const { data: me, isLoading } = useMe();
  const { data } = useConversations(!!me);
  const navigate = useNavigate();
  const home = useBoardHome();
  const isPhone = useIsPhone();

  const selected = data?.items.find((c) => c.id === conversationId);

  if (!me && !isLoading) {
    return (
      <Modal onClose={() => navigate(home)} size="sm">
        <p className="empty-state">{strings.auth.signInToPin}</p>
      </Modal>
    );
  }

  const threadOnly = isPhone && !!selected;

  return (
    <Modal onClose={() => navigate(home)} size="lg" className="modal-messages">
      {!threadOnly && (
        <div className="modal-head modal-head-ruled">
          <h2>{s.title}</h2>
        </div>
      )}
      <div className="messages-panes">
        {!threadOnly && (
          <div className="conv-list">
            {data && data.items.length === 0 && <p className="empty-state">{s.empty}</p>}
            {data?.items.map((c) => (
              <ConversationRow key={c.id} conversation={c} active={c.id === conversationId} />
            ))}
          </div>
        )}
        {selected ? (
          <Thread
            conversation={selected}
            onBack={isPhone ? () => navigate("/messages") : undefined}
          />
        ) : (
          !isPhone && (
            <div className="thread thread-empty">
              <p className="empty-state">{s.emptyThread}</p>
            </div>
          )
        )}
      </div>
    </Modal>
  );
}
