import { useEffect, useRef } from "react";
import type { ReactNode } from "react";
import { CloseIcon } from "./icons";
import { strings } from "../i18n/strings";

type Size = "sm" | "md" | "lg" | "wide";

const open: symbol[] = [];

export function Modal({
  onClose,
  size = "md",
  labelledBy,
  className = "",
  children,
}: {
  onClose: () => void;
  size?: Size;
  labelledBy?: string;
  className?: string;
  children: ReactNode;
}) {
  const cardRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    const token = Symbol("modal");
    open.push(token);
    const previous = document.activeElement as HTMLElement | null;
    cardRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && open[open.length - 1] === token) closeRef.current();
    };
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      open.splice(open.indexOf(token), 1);
      document.removeEventListener("keydown", onKey);
      if (open.length === 0) document.body.style.overflow = "";
      previous?.focus?.();
    };
  }, []);

  return (
    <div className="modal-scrim" onMouseDown={onClose}>
      <div
        className={`modal-shell modal-${size} ${className}`}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <span className="modal-pin" aria-hidden="true" />
        <div ref={cardRef} role="dialog" aria-modal="true" aria-labelledby={labelledBy} tabIndex={-1} className="modal-card">
          <button type="button" className="icon-btn modal-close" onClick={onClose} aria-label={strings.event.close}>
            <CloseIcon size={18} />
          </button>
          {children}
        </div>
      </div>
    </div>
  );
}
