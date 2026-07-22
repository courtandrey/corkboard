import { useEffect, useRef } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import type { ConversationSummary, MessageResponse } from "../../api/client";
import { useConversations, useMe, useMessages } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { Modal } from "../../ui/Modal";
import { PixelAvatar } from "../../ui/PixelAvatar";
import { SendIcon } from "../../ui/icons";

const s = strings.messagesUi;

function ConversationRow({ conversation, active }: { conversation: ConversationSummary; active: boolean }) {
  return (
    <Link to={`/messages/${conversation.id}`} className={`conv-row${active ? " active" : ""}`}>
      <span className="conv-name">
        <PixelAvatar seed={conversation.otherParty.avatarSeed} size={18} />
        {conversation.otherParty.displayName}
        {conversation.unreadCount > 0 && <span className="badge">{conversation.unreadCount}</span>}
      </span>
      <div className="conv-event">{conversation.event.title}</div>
      {conversation.lastMessageBody && <div className="conv-snippet">{conversation.lastMessageBody}</div>}
    </Link>
  );
}

function Thread({ conversation }: { conversation: ConversationSummary }) {
  const { data: me } = useMe();
  const { data: messages } = useMessages(conversation.id);
  const queryClient = useQueryClient();
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (conversation.unreadCount > 0) {
      void api.post(`/api/v1/conversations/${conversation.id}/read`).then(() => {
        void queryClient.invalidateQueries({ queryKey: ["conversations"] });
      });
    }
  }, [conversation.id, conversation.unreadCount, queryClient]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages]);

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
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
        {s.aboutNote}{" "}
        <Link to={`/events/${conversation.event.id}`}>{conversation.event.title}</Link>
        {" · "}
        {s.statusLabel(conversation.applicationStatus)}
      </div>
      <div className="thread-messages">
        {messages?.items.map((m) => (
          <div key={m.id} className={`bubble${m.senderId === me?.id ? " mine" : ""}`}>
            {m.body}
            <span className="bubble-time">{new Date(m.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
          </div>
        ))}
        <div ref={bottomRef} />
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

  const selected = data?.items.find((c) => c.id === conversationId);

  if (!me && !isLoading) {
    return (
      <Modal onClose={() => navigate("/")} size="sm">
        <p className="empty-state">{strings.auth.signInToPin}</p>
      </Modal>
    );
  }

  return (
    <Modal onClose={() => navigate("/")} size="wide">
      <div className="modal-head" style={{ paddingBottom: 12, borderBottom: "1px solid var(--paper-edge)" }}>
        <h2>{s.title}</h2>
      </div>
      <div className="messages-panes">
        <div className="conv-list">
          {data && data.items.length === 0 && <p className="empty-state">{s.empty}</p>}
          {data?.items.map((c) => (
            <ConversationRow key={c.id} conversation={c} active={c.id === conversationId} />
          ))}
        </div>
        {selected ? (
          <Thread conversation={selected} />
        ) : (
          <div className="thread thread-empty">
            <p className="empty-state">{s.emptyThread}</p>
          </div>
        )}
      </div>
    </Modal>
  );
}
