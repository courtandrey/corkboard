import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { createPortal } from "react-dom";
import { useNavigate, useParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../../api/client";
import type { EventDetail } from "../../api/client";
import { useMe, useMeta } from "../../api/hooks";
import { boardEvents, boardPath, notePath } from "../../api/paths";
import { strings } from "../../i18n/strings";
import { useVerifyGate } from "../auth/verifyGate";
import { useBoardStore } from "../../stores/boardStore";
import { useScopeTypes } from "../../ui/scope";
import { Modal } from "../../ui/Modal";
import { PinIcon, PlusIcon } from "../../ui/icons";
import { AuthPanel } from "../auth/AuthPanel";
import { TagInput } from "../tags/TagInput";

const s = strings.create;

function isoDate(daysFromNow: number): string {
  return new Date(Date.now() + daysFromNow * 86_400_000).toISOString().slice(0, 10);
}

export function CreateEventFlow() {
  const { data: me, isLoading: meLoading } = useMe();
  const { data: meta } = useMeta();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { ownerId } = useParams();
  const board = ownerId ?? null;
  const scopeTypes = useScopeTypes(board);
  const personal = board !== null;
  const setCrosshair = useBoardStore((st) => st.setCrosshair);
  const draftLocation = useBoardStore((st) => st.draftLocation);
  const draftPinEl = useBoardStore((st) => st.draftPinEl);

  const [placing, setPlacing] = useState(true);
  const [type, setType] = useState<string | null>(null);
  const [applyable, setApplyable] = useState(false);
  const [tags, setTags] = useState<string[]>([]);
  const [noEndDate, setNoEndDate] = useState(board !== null);
  const { allows } = useVerifyGate();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const signedIn = !!me;
  const close = () => navigate(boardPath(board));

  const confirmed = allows("pin");

  useEffect(() => {
    if (!signedIn || !confirmed) return;
    setCrosshair(true);
    return () => setCrosshair(false);
  }, [signedIn, confirmed, setCrosshair]);

  useEffect(() => {
    if (scopeTypes.length > 0 && !scopeTypes.some((t) => t.key === type)) {
      setType(scopeTypes[0].key);
      setApplyable(!personal && scopeTypes[0].applyableDefault);
    }
  }, [scopeTypes, type, personal]);

  if (meLoading) return null;

  if (!signedIn) {
    return (
      <Modal onClose={close} size="sm">
        <div className="modal-head">
          <h2>{s.title}</h2>
        </div>
        <div className="modal-body" style={{ paddingBottom: 18 }}>
          <p className="form-hint" style={{ marginBottom: 12 }}>
            {strings.auth.signInToPin}
          </p>
          <AuthPanel />
        </div>
      </Modal>
    );
  }

  if (!confirmed) {
    return (
      <Modal onClose={close} size="sm">
        <div className="modal-head">
          <h2>{strings.verify.gateTitle}</h2>
        </div>
        <div className="modal-body" style={{ paddingBottom: 18 }}>
          <p className="form-hint" style={{ marginBottom: 6 }}>
            {strings.verify.gateReason.pin}
          </p>
          <p className="form-hint">{strings.verify.gateBody(me.email)}</p>
        </div>
      </Modal>
    );
  }

  function pickType(key: string) {
    setType(key);
    const t = scopeTypes.find((x) => x.key === key);
    if (t) setApplyable(!personal && t.applyableDefault);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draftLocation || !type) return;
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError(null);
    try {
      const created = await api.post<EventDetail>(boardEvents(board), {
        type,
        title: String(data.get("title") ?? ""),
        body: String(data.get("body") ?? ""),
        location: draftLocation,
        applyable,
        expiresAt: noEndDate ? undefined : `${String(data.get("expiresAt"))}T23:59:59Z`,
        tags,
      });
      await queryClient.invalidateQueries({ queryKey: ["events"] });
      navigate(notePath(board, created.id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : strings.auth.genericError);
    } finally {
      setBusy(false);
    }
  }

  if (placing || !draftLocation) {
    if (!draftLocation) {
      return (
        <div className="creator-bar" role="dialog" aria-label={s.title}>
          <PinIcon size={18} />
          <span className="creator-msg">{s.pickSpotHint}</span>
          <button type="button" className="ghost sm" onClick={close}>
            {s.cancel}
          </button>
        </div>
      );
    }
    return draftPinEl
      ? createPortal(
          <div className="pin-callout" role="dialog" aria-label={s.title}>
            <p className="creator-msg">{s.adjustHint}</p>
            <div className="creator-actions">
              <button type="button" className="primary sm" onClick={() => setPlacing(false)}>
                {s.continueToForm}
              </button>
              <button type="button" className="ghost sm" onClick={close}>
                {s.cancel}
              </button>
            </div>
          </div>,
          draftPinEl,
        )
      : null;
  }

  const limits = meta?.limits;
  return (
    <Modal onClose={close} size="md">
      <div className="modal-head">
        <h2>{s.title}</h2>
        {personal && <p className="form-hint">{strings.scope.personalHint}</p>}
      </div>
      <form onSubmit={submit} style={{ display: "contents" }}>
        <div className="modal-body">
          <div className="form-grid" style={{ paddingBottom: 4 }}>
            <div>
              <div style={{ marginBottom: 6 }}>{s.typeLabel}</div>
              <div className="type-pick">
                {scopeTypes.map((t) => (
                  <label key={t.key} className={type === t.key ? "selected" : ""}>
                    <input type="radio" name="type" checked={type === t.key} onChange={() => pickType(t.key)} />
                    <span className="type-dot" style={{ background: t.color }} />
                    {t.label}
                  </label>
                ))}
              </div>
            </div>
            <label>
              {s.titleLabel}
              <input name="title" required minLength={limits?.titleMin ?? 3} maxLength={limits?.titleMax ?? 120} />
            </label>
            <label>
              {s.bodyLabel}
              <textarea name="body" required rows={5} maxLength={limits?.bodyMax ?? 4000} />
            </label>
            {!personal && (
              <div>
                <label className="inline">
                  <input type="checkbox" checked={applyable} onChange={(e) => setApplyable(e.target.checked)} />
                  {s.applyableLabel}
                </label>
                <p className="form-hint">{s.applyableHelp}</p>
              </div>
            )}
            <div>
              <label className="inline">
                <input type="checkbox" checked={noEndDate} onChange={(e) => setNoEndDate(e.target.checked)} />
                {s.noEndDateLabel}
              </label>
              <p className="form-hint">{s.noEndDateHelp}</p>
            </div>
            {!noEndDate && (
              <label>
                {s.expiresLabel}
                <input
                  type="date"
                  name="expiresAt"
                  required
                  defaultValue={isoDate(limits?.expiryDefaultDays ?? 30)}
                  min={isoDate(1)}
                />
              </label>
            )}
            <label>
              {s.tagsLabel}
              <TagInput value={tags} onChange={setTags} max={limits?.tagsMax ?? 5} />
            </label>
            {error && (
              <p className="error-note" role="alert">
                {error}
              </p>
            )}
          </div>
        </div>
        <div className="ev-foot">
          <button type="submit" className="primary grow" disabled={busy}>
            <PlusIcon size={16} /> {s.submit}
          </button>
          <button type="button" className="ghost" onClick={() => setPlacing(true)}>
            {s.changeSpot}
          </button>
        </div>
      </form>
    </Modal>
  );
}
