import { useCallback, useState } from "react";
import { useNavigate } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import type { NotificationResponse } from "../../api/client";
import { useNotifications } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { useDismiss } from "../../ui/useDismiss";
import { BellIcon, CloseIcon } from "../../ui/icons";

const s = strings.notificationsUi;

function label(notification: NotificationResponse): string {
  const payload = notification.payload as unknown as Record<string, string | undefined>;
  const event = payload.eventTitle ?? "";
  switch (notification.kind) {
    case "application_received":
      return s.applicationReceived(event);
    case "application_status":
      return s.applicationStatus(event, payload.status ?? "");
    case "event_expiring":
      return s.eventExpiring(event);
    case "event_under_review":
      return s.eventUnderReview(event);
    case "event_taken_down":
      return s.eventTakenDown(event);
    case "message_received":
      return s.messageReceived(payload.senderName ?? "");
    case "connection_requested":
      return s.connectionRequested(payload.senderName ?? "");
    case "connection_accepted":
      return s.connectionAccepted(payload.senderName ?? "");
    default:
      return s.fallback;
  }
}

function target(notification: NotificationResponse): string {
  const payload = notification.payload as unknown as Record<string, string | undefined>;
  if (payload.connectionId) return "/me/connections";
  if (payload.conversationId) return `/messages/${payload.conversationId}`;
  if (payload.eventId) return `/events/${payload.eventId}`;
  return "/";
}

export function NotificationsBell() {
  const { data } = useNotifications(true);
  const [open, setOpen] = useState(false);
  const close = useCallback(() => setOpen(false), []);
  const panelRef = useDismiss<HTMLDivElement>(open, close);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const pending = data?.unreadCount ?? 0;

  async function clear(ids?: string[]) {
    await api.post("/api/v1/notifications/read", ids ? { ids } : undefined);
    await queryClient.invalidateQueries({ queryKey: ["notifications"] });
  }

  async function openNotification(notification: NotificationResponse) {
    setOpen(false);
    navigate(target(notification));
    await clear([notification.id]);
  }

  return (
    <div className="bell-wrap" ref={panelRef}>
      <button
        type="button"
        className="icon-btn"
        onClick={() => setOpen(!open)}
        aria-label={s.title}
        aria-expanded={open}
      >
        <BellIcon size={19} />
        {pending > 0 && <span className="badge">{pending}</span>}
      </button>
      {open && (
        <div className="bell-panel">
          <div className="bell-head">
            <strong>{s.title}</strong>
            {pending > 0 && (
              <button type="button" className="quiet sm" onClick={() => clear()}>
                {s.clearAll}
              </button>
            )}
          </div>
          <div className="bell-list">
            {!data?.items.length && <p className="empty-state">{s.empty}</p>}
            {data?.items.map((n) => (
              <div key={n.id} className="bell-item">
                <button type="button" className="bell-open" onClick={() => openNotification(n)}>
                  {label(n)}
                  <span className="bell-time">{new Date(n.createdAt).toLocaleString()}</span>
                </button>
                <button
                  type="button"
                  className="icon-btn bell-dismiss"
                  onClick={() => clear([n.id])}
                  aria-label={s.dismiss}
                  title={s.dismiss}
                >
                  <CloseIcon size={14} />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
