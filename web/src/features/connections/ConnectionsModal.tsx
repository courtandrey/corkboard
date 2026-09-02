import { useState } from "react";
import { useNavigate } from "react-router";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import type { ConnectionItem, ConversationRef, PersonCard } from "../../api/client";
import { useConnections, useMe, usePeopleSearch } from "../../api/hooks";
import { useBoardHome } from "../../stores/boardStore";
import { strings } from "../../i18n/strings";
import { Modal } from "../../ui/Modal";
import { PixelAvatar } from "../../ui/PixelAvatar";
import { handleOf } from "../../ui/handle";
import { toast } from "../../ui/toast";
import { useDebounced } from "../../ui/useDebounced";
import { CheckIcon, CloseIcon, PlusIcon, SearchIcon } from "../../ui/icons";
import { useFeature } from "../../ui/features";
import { PersonLink } from "./personCard";
import { useVerifyGate } from "../auth/verifyGate";

const s = strings.connections;
const TYPING_PAUSE_MS = 300;

function Person({
  person,
  onOpen,
  children,
}: {
  person: PersonCard;
  onOpen?: () => void;
  children?: React.ReactNode;
}) {
  return (
    <div className="person-row">
      <PixelAvatar seed={person.avatarSeed} size={26} />
      <span className="person-who">
        {onOpen ? (
          <>
            <button type="button" className="link-btn person-name" onClick={onOpen} title={s.openChat}>
              {person.displayName}
            </button>
            <span className="user-handle">{handleOf(person.handle)}</span>
          </>
        ) : (
          <PersonLink
            userId={person.id}
            displayName={person.displayName}
            handle={person.handle}
            className="person-name"
            stacked
          />
        )}
      </span>
      <span className="person-actions">{children}</span>
    </div>
  );
}

export function ConnectionsModal() {
  const navigate = useNavigate();
  const home = useBoardHome();
  const queryClient = useQueryClient();
  const { data: me, isLoading } = useMe();
  const { data } = useConnections(!!me);
  const { guard } = useVerifyGate();
  const subscriptions = useFeature("IS_SUBSCRIPTION_ENABLED");
  const [text, setText] = useState("");
  const typed = useDebounced(text, TYPING_PAUSE_MS);
  const { data: found } = usePeopleSearch(typed, !!me);

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["connections"] }),
      queryClient.invalidateQueries({ queryKey: ["people"] }),
      queryClient.invalidateQueries({ queryKey: ["subscriptions"] }),
      queryClient.invalidateQueries({ queryKey: ["events"] }),
      queryClient.invalidateQueries({ queryKey: ["notifications"] }),
    ]);

  const request = useMutation({
    mutationFn: (userId: string) => api.post("/api/v1/connections", { userId }),
    onSuccess: async () => {
      toast(s.requested);
      await refresh();
    },
  });

  const share = useMutation({
    mutationFn: ({ userId, on }: { userId: string; on: boolean }) =>
      on
        ? api.post("/api/v1/subscriptions/viewers", { userId })
        : api.del(`/api/v1/subscriptions/viewers/${userId}`),
    onSuccess: async (_result, { on }) => {
      toast(on ? s.shared : s.unshared);
      await refresh();
    },
  });

  const answer = useMutation({
    mutationFn: ({ id, yes }: { id: string; yes: boolean }) =>
      api.post(`/api/v1/connections/${id}/${yes ? "accept" : "decline"}`),
    onSuccess: async (_result, { yes }) => {
      toast(yes ? s.accepted : s.dismissed);
      await refresh();
    },
  });

  async function openChat(person: PersonCard) {
    const conversation = await api.post<ConversationRef>(`/api/v1/conversations/with/${person.id}`);
    navigate(`/messages/${conversation.id}`);
  }

  if (!me && !isLoading) {
    return (
      <Modal onClose={() => navigate(home)} size="sm">
        <p className="empty-state">{strings.auth.signInToPin}</p>
      </Modal>
    );
  }

  const connected = data?.connected ?? [];
  const incoming = data?.incoming ?? [];
  const outgoing = data?.outgoing ?? [];
  const searching = typed.trim().length >= 2;

  return (
    <Modal onClose={() => navigate(home)} size="md" className="modal-connections" labelledBy="connections-title">
      <div className="modal-head modal-head-ruled">
        <h2 id="connections-title">{s.title}</h2>
      </div>
      <div className="modal-body connections-body">
        <div className="search address-field">
          <SearchIcon size={15} className="search-icon" />
          <input
            type="text"
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder={s.searchPlaceholder}
            aria-label={s.searchPlaceholder}
            autoComplete="off"
            spellCheck={false}
          />
          {text && (
            <button type="button" className="icon-btn address-clear" onClick={() => setText("")} aria-label={s.clear}>
              <CloseIcon size={15} />
            </button>
          )}
        </div>

        <div className="connections-list">
          {searching ? (
          <section className="connections-section">
            <h3>{s.found}</h3>
            {found?.items.length === 0 && <p className="empty-state">{s.nobody}</p>}
            {found?.items.map((person) => (
              <Person key={person.id} person={person}>
                {person.state === "none" && (
                  <button
                    type="button"
                    className="primary sm"
                    disabled={request.isPending}
                    onClick={guard("connect", () => request.mutate(person.id))}
                  >
                    <PlusIcon size={14} /> {s.connect}
                  </button>
                )}
                {person.state === "outgoing" && <span className="meta-row">{s.asked}</span>}
                {person.state === "incoming" && person.connectionId && (
                  <button
                    type="button"
                    className="primary sm"
                    onClick={guard("connect", () =>
                      answer.mutate({ id: person.connectionId!, yes: true }),
                    )}
                  >
                    <CheckIcon size={14} /> {s.accept}
                  </button>
                )}
                {person.state === "connected" && <span className="meta-row">{s.connectedLabel}</span>}
              </Person>
            ))}
          </section>
        ) : (
          <>
            {incoming.length > 0 && (
              <section className="connections-section">
                <h3>{s.incoming}</h3>
                {incoming.map((item: ConnectionItem) => (
                  <Person key={item.id} person={item.person}>
                    <button
                      type="button"
                      className="primary sm"
                      disabled={answer.isPending}
                      onClick={() => answer.mutate({ id: item.id, yes: true })}
                    >
                      <CheckIcon size={14} /> {s.accept}
                    </button>
                    <button
                      type="button"
                      className="ghost sm"
                      disabled={answer.isPending}
                      onClick={() => answer.mutate({ id: item.id, yes: false })}
                    >
                      {s.dismiss}
                    </button>
                  </Person>
                ))}
              </section>
            )}

            <section className="connections-section">
              <h3>{s.yours}</h3>
              {connected.length === 0 && <p className="empty-state">{s.empty}</p>}
              {connected.map((item: ConnectionItem) => (
                <Person key={item.id} person={item.person} onOpen={() => void openChat(item.person)}>
                  {subscriptions && (
                    <label className="inline share-board">
                      <input
                        type="checkbox"
                        checked={item.person.sharedWithThem}
                        disabled={share.isPending}
                        onChange={(event) =>
                          share.mutate({ userId: item.person.id, on: event.target.checked })
                        }
                      />
                      {s.shareBoard}
                    </label>
                  )}
                </Person>
              ))}
            </section>

            {outgoing.length > 0 && (
              <section className="connections-section">
                <h3>{s.outgoing}</h3>
                {outgoing.map((item: ConnectionItem) => (
                  <Person key={item.id} person={item.person}>
                    <span className="meta-row">{s.asked}</span>
                  </Person>
                ))}
              </section>
            )}
          </>
          )}
        </div>
      </div>
    </Modal>
  );
}
