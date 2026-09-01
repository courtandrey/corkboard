import { useNavigate } from "react-router";
import { create } from "zustand";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import type { ConversationRef, PersonCard } from "../../api/client";
import { useMe, usePerson } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { Modal } from "../../ui/Modal";
import { PixelAvatar } from "../../ui/PixelAvatar";
import { handleOf } from "../../ui/handle";
import { toast } from "../../ui/toast";
import { CheckIcon, ChatIcon, PlusIcon } from "../../ui/icons";
import { useFeature } from "../../ui/features";
import { useVerifyGate } from "../auth/verifyGate";

const s = strings.person;

interface CardState {
  personId: string | null;
  open: (personId: string) => void;
  close: () => void;
}

const useCardStore = create<CardState>((set) => ({
  personId: null,
  open: (personId) => set({ personId }),
  close: () => set({ personId: null }),
}));

export function useOpenPerson(): (personId: string) => void {
  return useCardStore((state) => state.open);
}

export function PersonLink({
  userId,
  displayName,
  handle,
  className = "",
}: {
  userId: string;
  displayName: string;
  handle?: string;
  className?: string;
}) {
  const openPerson = useOpenPerson();
  return (
    <button
      type="button"
      className={`link-btn person-link ${className}`.trim()}
      onClick={() => openPerson(userId)}
      title={s.openCard(displayName)}
    >
      <span className="person-link-part">{displayName}</span>
      {handle && (
        <span className="user-handle">
          <span aria-hidden="true">@</span><span className="person-link-part">{handle}</span>
        </span>
      )}
    </button>
  );
}

export function PersonCardModal() {
  const personId = useCardStore((state) => state.personId);
  const close = useCardStore((state) => state.close);
  const { data: me } = useMe();
  const { data: person, error } = usePerson(personId);
  const { guard } = useVerifyGate();
  const subscriptions = useFeature("IS_SUBSCRIPTION_ENABLED");
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["person", personId] }),
      queryClient.invalidateQueries({ queryKey: ["connections"] }),
      queryClient.invalidateQueries({ queryKey: ["people"] }),
      queryClient.invalidateQueries({ queryKey: ["subscriptions"] }),
      queryClient.invalidateQueries({ queryKey: ["events"] }),
      queryClient.invalidateQueries({ queryKey: ["notifications"] }),
    ]);

  const request = useMutation({
    mutationFn: (userId: string) => api.post("/api/v1/connections", { userId }),
    onSuccess: async () => {
      toast(strings.connections.requested);
      await refresh();
    },
  });

  const share = useMutation({
    mutationFn: ({ userId, on }: { userId: string; on: boolean }) =>
      on
        ? api.post("/api/v1/subscriptions/viewers", { userId })
        : api.del(`/api/v1/subscriptions/viewers/${userId}`),
    onSuccess: async (_result, { on }) => {
      toast(on ? strings.connections.shared : strings.connections.unshared);
      await refresh();
    },
  });

  const answer = useMutation({
    mutationFn: ({ id, yes }: { id: string; yes: boolean }) =>
      api.post(`/api/v1/connections/${id}/${yes ? "accept" : "decline"}`),
    onSuccess: async (_result, { yes }) => {
      toast(yes ? strings.connections.accepted : strings.connections.dismissed);
      await refresh();
    },
  });

  if (!personId) return null;

  async function openChat(card: PersonCard) {
    const conversation = await api.post<ConversationRef>(`/api/v1/conversations/with/${card.id}`);
    close();
    navigate(`/messages/${conversation.id}`);
  }

  const itsYou = !!me && me.id === person?.id;

  return (
    <Modal onClose={close} size="sm" className="modal-person" labelledBy="person-name">
      <div className="modal-body person-card">
        {error && <p className="empty-state error-note">{s.notFound}</p>}
        {!person && !error && <p className="empty-state">{strings.loading}</p>}
        {person && (
          <>
            <PixelAvatar seed={person.avatarSeed} size={64} />
            <h2 id="person-name" className="person-card-name">
              {person.displayName}
            </h2>
            <span className="user-handle">{handleOf(person.handle)}</span>
            <span className="meta-row person-card-since">
              {s.memberSince(new Date(person.memberSince).toLocaleDateString())}
            </span>

            {!itsYou && (
              <div className="person-card-actions">
                {person.state === "none" && me && (
                  <button
                    type="button"
                    className="primary"
                    disabled={request.isPending}
                    onClick={guard("connect", () => request.mutate(person.id))}
                  >
                    <PlusIcon size={15} /> {strings.connections.connect}
                  </button>
                )}
                {person.state === "outgoing" && <span className="person-card-state">{s.requested}</span>}
                {person.state === "incoming" && person.connectionId && (
                  <>
                    <span className="person-card-state">{s.wantsToConnect}</span>
                    <div className="modal-actions">
                      <button
                        type="button"
                        className="primary"
                        disabled={answer.isPending}
                        onClick={guard("connect", () =>
                          answer.mutate({ id: person.connectionId!, yes: true }),
                        )}
                      >
                        <CheckIcon size={15} /> {strings.connections.accept}
                      </button>
                      <button
                        type="button"
                        className="ghost"
                        disabled={answer.isPending}
                        onClick={() => answer.mutate({ id: person.connectionId!, yes: false })}
                      >
                        {strings.connections.dismiss}
                      </button>
                    </div>
                  </>
                )}
                {person.state === "connected" && (
                  <>
                    <span className="person-card-state connected">{s.connected}</span>
                    <button type="button" className="ghost" onClick={() => void openChat(person)}>
                      <ChatIcon size={15} /> {strings.connections.message}
                    </button>
                    {subscriptions && (
                      <label className="inline share-board">
                        <input
                          type="checkbox"
                          checked={person.sharedWithThem}
                          disabled={share.isPending}
                          onChange={(event) => share.mutate({ userId: person.id, on: event.target.checked })}
                        />
                        {strings.connections.shareBoard}
                      </label>
                    )}
                  </>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </Modal>
  );
}
