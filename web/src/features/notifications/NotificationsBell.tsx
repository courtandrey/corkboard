import { useState } from "react";
import { useNavigate } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import type { NotificationResponse } from "../../api/client";
import { useNotifications } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { BellIcon } from "../../ui/icons";

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
    case "message_received":
      return s.messageReceived(event);
    default:
      return s.fallback;
  }
}

function target(notification: NotificationResponse): string {
  const payload = notification.payload as unknown as Record<string, string | undefined>;
  if (payload.conversationId) return `/messages/${payload.conversationId}`;
  if (payload.eventId) return `/events/${payload.eventId}`;
  return "/";
}

export function NotificationsBell() {
  const { data } = useNotifications(true);
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const unread = data?.unreadCount ?? 0;

  async function markAllRead() {
    await api.post("/api/v1/notifications/read");
    await queryClient.invalidateQueries({ queryKey: ["notifications"] });
  }

  async function openNotification(notification: NotificationResponse) {
    setOpen(false);
    if (!notification.readAt) {
      await api.post("/api/v1/notifications/read", { ids: [notification.id] });
      await queryClient.invalidateQueries({ queryKey: ["notifications"] });
    }
    navigate(target(notification));
  }

  return (
    <div className="bell-wrap">
      <button type="button" className="icon-btn" onClick={() => setOpen(!open)} aria-label={s.title}>
        <BellIcon size={19} />
        {unread > 0 && <span className="badge">{unread}</span>}
      </button>
      {open && (
        <div className="bell-panel">
          <div className="bell-head">
            <strong>{s.title}</strong>
            {unread > 0 && (
              <button type="button" className="quiet sm" onClick={markAllRead}>
                {s.markAllRead}
              </button>
            )}
          </div>
          <div className="bell-list">
            {!data?.items.length && <p className="empty-state">{s.empty}</p>}
            {data?.items.map((n) => (
              <button
                key={n.id}
                type="button"
                className={`bell-item${n.readAt ? " read" : " unread"}`}
                onClick={() => openNotification(n)}
              >
                <span className="unread-dot" />
                <span className="bell-text">
                  {label(n)}
                  <span className="bell-time">{new Date(n.createdAt).toLocaleString()}</span>
                </span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
