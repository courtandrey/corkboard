import { useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../../api/client";
import type { AuthResponse } from "../../api/client";
import { useMe, useMeta } from "../../api/hooks";
import { useBoardHome } from "../../stores/boardStore";
import { strings } from "../../i18n/strings";
import { handleOf } from "../../ui/handle";
import { HintMark } from "../../ui/HintMark";
import { useFeature } from "../../ui/features";
import { Modal } from "../../ui/Modal";
import { PixelAvatar } from "../../ui/PixelAvatar";
import { toast } from "../../ui/toast";

const s = strings.account;

export function AccountModal() {
  const navigate = useNavigate();
  const home = useBoardHome();
  const queryClient = useQueryClient();
  const { data: me, isLoading } = useMe();
  const { data: meta } = useMeta();
  const editable = useFeature("ARE_USER_DETAILS_EDITABLE");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const close = () => navigate(home);

  if (isLoading) return null;
  if (!me) {
    navigate("/login", { replace: true });
    return null;
  }

  async function save(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const displayName = String(new FormData(formEvent.currentTarget).get("displayName") ?? "").trim();
    if (!displayName || displayName === me!.displayName) return close();

    setBusy(true);
    setError(null);
    try {
      const res = await api.patch<AuthResponse>("/api/v1/auth/me", { displayName });
      queryClient.setQueryData(["auth", "me"], res.user);
      await queryClient.invalidateQueries({ queryKey: ["events"] });
      await queryClient.invalidateQueries({ queryKey: ["event"] });
      await queryClient.invalidateQueries({ queryKey: ["conversations"] });
      toast(s.saved);
      close();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : strings.auth.genericError);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal onClose={close} size="sm" className="modal-account" labelledBy="account-title">
      <div className="modal-head">
        <h2 id="account-title">{s.title}</h2>
      </div>
      <form onSubmit={save} style={{ display: "contents" }}>
        <div className="modal-body">
          <div className="form-grid">
            <div className="account-identity">
              <PixelAvatar seed={me.avatarSeed} size={44} />
              <div className="account-identity-text">
                <span className="account-email">{me.email}</span>
                <span className="form-hint">
                  {me.emailVerified ? s.emailConfirmed : s.emailUnconfirmed}
                  {" · "}
                  {s.memberSince(new Date(me.createdAt).toLocaleDateString())}
                </span>
              </div>
            </div>

            <label>
              {s.handleLabel}
              <HintMark hint={s.handleHelp} />
              <input
                name="handle"
                value={handleOf(me.handle)}
                title={s.handleHelp}
                disabled
                readOnly
              />
            </label>

            <label>
              {s.displayNameLabel}
              <input
                name="displayName"
                defaultValue={me.displayName}
                required
                disabled={!editable}
                maxLength={meta?.limits.displayNameMax ?? 50}
                autoComplete="nickname"
              />
            </label>
            <p className="form-hint" style={{ marginTop: -10 }}>
              {s.displayNameHelp}
            </p>

            {error && (
              <p className="error-note" role="alert">
                {error}
              </p>
            )}
          </div>
        </div>
        <div className="modal-foot">
          <div className="modal-actions">
            {editable && (
              <button type="submit" className="primary" disabled={busy}>
                {s.save}
              </button>
            )}
            <button type="button" className="ghost" onClick={close}>
              {s.cancel}
            </button>
          </div>
        </div>
      </form>
    </Modal>
  );
}
