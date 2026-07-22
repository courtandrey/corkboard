import { Link, useNavigate } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import type { ApplicationItem, MyEventItem } from "../../api/client";
import { useMe, useMeta, useMyEvents, useReceivedApplications } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { Modal } from "../../ui/Modal";
import { PixelAvatar } from "../../ui/PixelAvatar";
import { toast } from "../../ui/toast";
import { CheckIcon, RenewIcon, TrashIcon } from "../../ui/icons";

const s = strings.myPins;

const STATUS_ORDER = ["active", "expired", "resolved", "under_review", "removed"];

function isoInDays(days: number): string {
  return new Date(Date.now() + days * 86_400_000).toISOString();
}

function ApplicationRow({ application }: { application: ApplicationItem }) {
  const queryClient = useQueryClient();

  async function setStatus(status: "accepted" | "declined") {
    await api.patch(`/api/v1/applications/${application.id}`, { status });
    toast(status === "accepted" ? strings.toasts.accepted : strings.toasts.declined);
    await queryClient.invalidateQueries({ queryKey: ["myApplications"] });
  }

  return (
    <div className="pin-application">
      <div className="appl-head">
        {application.applicant && <PixelAvatar seed={application.applicant.avatarSeed} size={18} />}
        {application.applicant?.displayName}
        <span className="meta-row" style={{ margin: 0, fontWeight: 400 }}>
          · {strings.messagesUi.statusLabel(application.status)}
        </span>
      </div>
      {application.message && <div className="conv-snippet" style={{ whiteSpace: "normal" }}>{application.message}</div>}
      <div className="appl-actions">
        {application.status === "pending" && (
          <>
            <button type="button" className="primary sm" onClick={() => setStatus("accepted")}>
              <CheckIcon size={14} /> {strings.messagesUi.accept}
            </button>
            <button type="button" className="ghost sm" onClick={() => setStatus("declined")}>
              {strings.messagesUi.decline}
            </button>
          </>
        )}
        <Link to={`/messages/${application.conversationId}`}>{strings.messagesUi.open}</Link>
      </div>
    </div>
  );
}

function PinRow({ pin }: { pin: MyEventItem }) {
  const { data: meta } = useMeta();
  const { data: received } = useReceivedApplications(pin.applicationCount > 0);
  const queryClient = useQueryClient();

  const type = meta?.types.find((t) => t.key === pin.type);
  const applications = received?.items.find((g) => g.event.id === pin.id)?.applications ?? [];

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ["myEvents"] });
    await queryClient.invalidateQueries({ queryKey: ["events"] });
    await queryClient.invalidateQueries({ queryKey: ["event", pin.id] });
  }

  async function resolve() {
    await api.post(`/api/v1/events/${pin.id}/resolve`);
    toast(strings.toasts.resolved);
    await refresh();
  }

  async function renew() {
    await api.post(`/api/v1/events/${pin.id}/renew`, { expiresAt: isoInDays(30) });
    toast(strings.toasts.renewed);
    await refresh();
  }

  async function remove() {
    if (!window.confirm(s.removeConfirm)) return;
    await api.del(`/api/v1/events/${pin.id}`);
    toast(strings.toasts.removed);
    await refresh();
  }

  return (
    <div className="pin-row">
      <div className="pin-row-top">
        <div className="grow">
          <Link to={`/events/${pin.id}`} className="pin-title">
            {type && <span className="type-dot" style={{ background: type.color }} />}
            {pin.title}
          </Link>
          <div className="meta-row" style={{ margin: "4px 0 0" }}>
            {strings.board.points(pin.score)} · {s.responses(pin.applicationCount)} ·{" "}
            {s.until(new Date(pin.expiresAt).toLocaleDateString())}
          </div>
        </div>
        <div className="pin-actions">
          {pin.status === "active" && (
            <button type="button" className="ghost sm" onClick={resolve}>
              <CheckIcon size={14} /> {s.resolve}
            </button>
          )}
          {(pin.status === "active" || pin.status === "expired") && (
            <button type="button" className="ghost sm" onClick={renew}>
              <RenewIcon size={14} /> {s.renew}
            </button>
          )}
          {pin.status !== "removed" && (
            <button type="button" className="danger sm" onClick={remove}>
              <TrashIcon size={14} /> {s.remove}
            </button>
          )}
        </div>
      </div>
      {applications.length > 0 && (
        <div className="pin-applications">
          {applications.map((a) => (
            <ApplicationRow key={a.id} application={a} />
          ))}
        </div>
      )}
    </div>
  );
}

export function MyPins() {
  const { data: me, isLoading } = useMe();
  const { data } = useMyEvents(!!me);
  const navigate = useNavigate();

  if (!me && !isLoading) {
    return (
      <Modal onClose={() => navigate("/")} size="sm">
        <p className="empty-state">{strings.auth.signInToPin}</p>
      </Modal>
    );
  }

  const groups = STATUS_ORDER.map((status) => ({
    status,
    pins: data?.items.filter((p) => p.status === status) ?? [],
  })).filter((g) => g.pins.length > 0);

  return (
    <Modal onClose={() => navigate("/")} size="lg">
      <div className="modal-head">
        <h2>{s.title}</h2>
      </div>
      <div className="modal-body" style={{ paddingBottom: 18 }}>
        {data && data.items.length === 0 && <p className="empty-state">{s.empty}</p>}
        {groups.map((group) => (
          <div key={group.status}>
            <h3 className="pins-group-head">{s.statusHeading[group.status] ?? group.status}</h3>
            {group.pins.map((pin) => (
              <PinRow key={pin.id} pin={pin} />
            ))}
          </div>
        ))}
      </div>
    </Modal>
  );
}
