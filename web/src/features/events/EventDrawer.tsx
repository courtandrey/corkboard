import { Fragment } from "react";
import { useNavigate, useParams } from "react-router";
import { useEventDetail, useMe, useMeta } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { EventActions } from "./EventActions";

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

  const type = meta?.types.find((t) => t.key === event?.type);

  return (
    <section className="drawer">
      <button type="button" className="close" onClick={() => navigate("/")}>
        {strings.event.close}
      </button>
      {error && <p className="error-note">{strings.event.notFound}</p>}
      {!event && !error && <p>{strings.loading}</p>}
      {event && (
        <>
          {type && (
            <span className="type-chip" style={{ background: type.color }}>
              {type.label}
            </span>
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
          {me && <EventActions event={event} />}
        </>
      )}
    </section>
  );
}
