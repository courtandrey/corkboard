import { useEffect } from "react";
import { create } from "zustand";
import { CheckIcon } from "./icons";

type Toast = { id: number; message: string; kind: "ok" | "info" };

interface ToastState {
  toasts: Toast[];
  push: (message: string, kind?: Toast["kind"]) => void;
  dismiss: (id: number) => void;
}

let seq = 0;

const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (message, kind = "ok") =>
    set((s) => ({ toasts: [...s.toasts, { id: ++seq, message, kind }] })),
  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

export function toast(message: string, kind?: Toast["kind"]) {
  useToastStore.getState().push(message, kind);
}

function ToastSlip({ toast: t }: { toast: Toast }) {
  const dismiss = useToastStore((s) => s.dismiss);
  useEffect(() => {
    const timer = window.setTimeout(() => dismiss(t.id), 3200);
    return () => window.clearTimeout(timer);
  }, [t.id, dismiss]);

  return (
    <button type="button" className="toast-slip" onClick={() => dismiss(t.id)}>
      {t.kind === "ok" && <CheckIcon size={16} className="toast-check" />}
      <span>{t.message}</span>
    </button>
  );
}

export function Toaster() {
  const toasts = useToastStore((s) => s.toasts);
  return (
    <div className="toaster" aria-live="polite">
      {toasts.map((t) => (
        <ToastSlip key={t.id} toast={t} />
      ))}
    </div>
  );
}
