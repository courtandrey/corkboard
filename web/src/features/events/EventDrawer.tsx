import { Fragment, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { useEventDetail, useMe, useMeta } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { ApplyBox } from "./ApplyBox";
import { EventActions } from "./EventActions";
import { EventEditForm } from "./EventEditForm";

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

export function EventDrawer() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: meta } = useMeta();
  const { data: me } = useMe();
  const { data: event, error } = useEventDetail(id);
  const [editing, setEditing] = useState(false);

  const type = meta?.types.find((t) => t.key === event?.type);

  return (
    <section className="drawer">
      <button type="button" className="close" onClick={() => navigate("/")}>
        {strings.event.close}
      </button>
      {error && <p className="error-note">{strings.event.notFound}</p>}
      {!event && !error && <p>{strings.loading}</p>}
      {event && editing && (
        <>
          <h2>{strings.eventEdit.edit}</h2>
          <EventEditForm event={event} onDone={() => setEditing(false)} />
        </>
      )}
      {event && !editing && (
        <>
          {type && (
            <span className="type-chip" style={{ background: type.color }}>
              {type.label}
            </span>
          )}
          {event.status !== "active" && (
            <span className="status-chip">{strings.myPins.statusHeading[event.status] ?? event.status}</span>
          )}
          <h2>{event.title}</h2>
          <div className="meta-row">
            {strings.board.points(event.score)}
            {" · "}
            {strings.event.expires(new Date(event.expiresAt).toLocaleDateString())}
            {event.updatedAt > event.createdAt && <> · {strings.event.edited}</>}
          </div>
          <p style={{ whiteSpace: "pre-wrap" }}>
            <Linkified text={event.body} />
          </p>
          {event.tags.length > 0 && (
            <p>
              {event.tags.map((tag) => (
                <span key={tag.slug} className="tag-chip">
                  {tag.name}
                </span>
              ))}
            </p>
          )}
          <div className="meta-row">
            {strings.event.postedBy(event.author.displayName)}{" "}
            {strings.event.memberSince(String(new Date(event.author.memberSince).getFullYear()))}
          </div>
          {event.viewerState.isAuthor && (
            <button type="button" onClick={() => setEditing(true)}>
              {strings.eventEdit.edit}
            </button>
          )}
          {me && <ApplyBox event={event} />}
          {me && <EventActions event={event} />}
        </>
      )}
    </section>
  );
}
