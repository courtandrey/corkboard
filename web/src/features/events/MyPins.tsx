import { Link, useNavigate } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import type { ApplicationItem, MyEventItem } from "../../api/client";
import { useMe, useMeta, useMyEvents, useReceivedApplications } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { PixelAvatar } from "../../ui/PixelAvatar";

const s = strings.myPins;

const STATUS_ORDER = ["active", "expired", "resolved", "under_review", "removed"];

function isoInDays(days: number): string {
  return new Date(Date.now() + days * 86_400_000).toISOString();
}

function ApplicationRow({ application }: { application: ApplicationItem }) {
  const queryClient = useQueryClient();

  async function setStatus(status: "accepted" | "declined") {
    await api.patch(`/api/v1/applications/${application.id}`, { status });
    await queryClient.invalidateQueries({ queryKey: ["myApplications"] });
  }

  return (
    <div className="pin-application">
      {application.applicant && <PixelAvatar seed={application.applicant.avatarSeed} size={18} />}{" "}
      <strong>{application.applicant?.displayName}</strong>{" "}
      <span className="meta-row">{strings.messagesUi.statusLabel(application.status)}</span>
      {application.message && <div className="conv-snippet">{application.message}</div>}
      <div style={{ display: "flex", gap: 6, marginTop: 4 }}>
        {application.status === "pending" && (
          <>
            <button type="button" onClick={() => setStatus("accepted")}>
              {strings.messagesUi.accept}
            </button>
            <button type="button" onClick={() => setStatus("declined")}>
              {strings.messagesUi.decline}
            </button>
          </>
        )}
        <Link to={`/messages/${application.conversationId}`}>{strings.messagesUi.title}</Link>
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
    await refresh();
  }

  async function renew() {
    await api.post(`/api/v1/events/${pin.id}/renew`, { expiresAt: isoInDays(30) });
    await refresh();
  }

  async function remove() {
    if (!window.confirm(s.removeConfirm)) return;
    await api.del(`/api/v1/events/${pin.id}`);
    await refresh();
  }

  return (
    <div className="pin-row">
      <div>
        {type && <span className="type-dot" style={{ background: type.color }} />}{" "}
        <Link to={`/events/${pin.id}`}>{pin.title}</Link>
        <div className="meta-row">
          {strings.board.points(pin.score)}
          {" · "}
          {s.responses(pin.applicationCount)}
          {" · "}
          {s.until(new Date(pin.expiresAt).toLocaleDateString())}
        </div>
      </div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        {pin.status === "active" && (
          <button type="button" onClick={resolve}>
            {s.resolve}
          </button>
        )}
        {(pin.status === "active" || pin.status === "expired") && (
          <button type="button" onClick={renew}>
            {s.renew}
          </button>
        )}
        {pin.status !== "removed" && (
          <button type="button" onClick={remove}>
            {s.remove}
          </button>
        )}
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
      <section className="drawer">
        <button type="button" className="close" onClick={() => navigate("/")}>
          {strings.event.close}
        </button>
        <p>{strings.auth.signInToPin}</p>
      </section>
    );
  }

  const groups = STATUS_ORDER
    .map((status) => ({ status, pins: data?.items.filter((p) => p.status === status) ?? [] }))
    .filter((g) => g.pins.length > 0);

  return (
    <section className="drawer wide">
      <button type="button" className="close" onClick={() => navigate("/")}>
        {strings.event.close}
      </button>
      <h2>{s.title}</h2>
      {data && data.items.length === 0 && <p className="meta-row">{s.empty}</p>}
      {groups.map((group) => (
        <div key={group.status}>
          <h3>{s.statusHeading[group.status] ?? group.status}</h3>
          {group.pins.map((pin) => (
            <PinRow key={pin.id} pin={pin} />
          ))}
        </div>
      ))}
    </section>
  );
}
