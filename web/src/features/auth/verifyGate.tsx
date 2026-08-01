import { useEffect, useState } from "react";
import { create } from "zustand";
import { api, ApiError } from "../../api/client";
import { useMe } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { Modal } from "../../ui/Modal";
import { SendIcon } from "../../ui/icons";
import { toast } from "../../ui/toast";

export type GatedAction = keyof typeof strings.verify.gateReason;

interface GateState {
  reason: GatedAction | null;
  open: (reason: GatedAction) => void;
  close: () => void;
}

const useGateStore = create<GateState>((set) => ({
  reason: null,
  open: (reason) => set({ reason }),
  close: () => set({ reason: null }),
}));

export function useVerifyGate() {
  const { data: me } = useMe();
  const open = useGateStore((s) => s.open);
  const verified = !!me?.emailVerified;

  return {
    verified,
    block: open,
    guard<A extends unknown[]>(reason: GatedAction, action: (...args: A) => void) {
      return (...args: A) => {
        if (!verified) open(reason);
        else action(...args);
      };
    },
  };
}

export function VerifyGate() {
  const { data: me } = useMe();
  const reason = useGateStore((s) => s.reason);
  const close = useGateStore((s) => s.close);
  const [busy, setBusy] = useState(false);
  const verified = !!me?.emailVerified;

  useEffect(() => {
    if (verified) close();
  }, [verified, close]);

  if (!reason || !me || verified) return null;

  async function resend() {
    setBusy(true);
    try {
      await api.post("/api/v1/auth/verification/resend");
      toast(strings.verify.resent);
    } catch (error) {
      toast(error instanceof ApiError ? strings.verify.resendFailed : strings.auth.genericError, "info");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal onClose={close} size="sm" labelledBy="verify-gate-title">
      <div className="modal-head">
        <h2 id="verify-gate-title">{strings.verify.gateTitle}</h2>
      </div>
      <div className="modal-body" style={{ paddingBottom: 18 }}>
        <p className="form-hint" style={{ marginBottom: 6 }}>
          {strings.verify.gateReason[reason]}
        </p>
        <p className="form-hint" style={{ marginBottom: 14 }}>
          {strings.verify.gateBody(me.email)}
        </p>
        <div className="modal-actions">
          <button type="button" className="primary" onClick={resend} disabled={busy}>
            <SendIcon size={14} /> {strings.verify.resend}
          </button>
          <button type="button" className="ghost" onClick={close}>
            {strings.verify.gateDismiss}
          </button>
        </div>
      </div>
    </Modal>
  );
}
