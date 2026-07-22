import { useEffect, useRef } from "react";
import type { ReactNode } from "react";
import { CloseIcon } from "./icons";
import { strings } from "../i18n/strings";

type Size = "sm" | "md" | "lg" | "wide";

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

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    cardRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      previous?.focus?.();
    };
  }, [onClose]);

  return (
    <div className="modal-scrim" onMouseDown={onClose}>
      <div
        ref={cardRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        tabIndex={-1}
        className={`modal-card modal-${size} ${className}`}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <span className="modal-pin" aria-hidden="true" />
        <button type="button" className="icon-btn modal-close" onClick={onClose} aria-label={strings.event.close}>
          <CloseIcon size={18} />
        </button>
        {children}
      </div>
    </div>
  );
}
