import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function Base({ size = 18, children, ...rest }: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...rest}
    >
      {children}
    </svg>
  );
}

export const CloseIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M6 6l12 12M18 6L6 18" />
  </Base>
);

export const UpvoteIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M12 5l7 8h-4v6H9v-6H5z" />
  </Base>
);

export const HideIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M3 3l18 18" />
    <path d="M10.6 6.1A9.6 9.6 0 0112 6c5 0 9 6 9 6a15 15 0 01-2.4 2.8M6.1 6.2A15 15 0 003 12s4 6 9 6a9 9 0 003.3-.6" />
    <path d="M9.9 9.9a3 3 0 004.2 4.2" />
  </Base>
);

export const ShowIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M3 12s4-6 9-6 9 6 9 6-4 6-9 6-9-6-9-6z" />
    <circle cx="12" cy="12" r="2.6" />
  </Base>
);

export const FlagIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M6 21V4M6 4h11l-2 4 2 4H6" />
  </Base>
);

export const ReplyIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M4 12h11a4 4 0 014 4v2M4 12l5-5M4 12l5 5" />
  </Base>
);

export const BellIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M6 9a6 6 0 0112 0c0 5 2 6 2 6H4s2-1 2-6z" />
    <path d="M10 20a2 2 0 004 0" />
  </Base>
);

export const PinIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M12 21s7-6.3 7-11a7 7 0 10-14 0c0 4.7 7 11 7 11z" />
    <circle cx="12" cy="10" r="2.4" />
  </Base>
);

export const PlusIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M12 5v14M5 12h14" />
  </Base>
);

export const CheckIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M5 12.5l4.5 4.5L19 6.5" />
  </Base>
);

export const TrashIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13" />
  </Base>
);

export const RenewIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M4 12a8 8 0 0114-5.3L21 8M20 12a8 8 0 01-14 5.3L3 16" />
    <path d="M21 4v4h-4M3 20v-4h4" />
  </Base>
);

export const ClockIcon = (p: IconProps) => (
  <Base {...p}>
    <circle cx="12" cy="12" r="8.2" />
    <path d="M12 7.5V12l3 2" />
  </Base>
);

export const SearchIcon = (p: IconProps) => (
  <Base {...p}>
    <circle cx="11" cy="11" r="6.2" />
    <path d="M20 20l-4.3-4.3" />
  </Base>
);

export const FiltersIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M4 6h16M7 12h10M10 18h4" />
  </Base>
);

export const SendIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M4 12l16-7-7 16-2.5-6.5z" />
  </Base>
);

export const ChatIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M4 5h16v11H8l-4 4z" />
  </Base>
);

export const BackIcon = (p: IconProps) => (
  <Base {...p}>
    <path d="M15 5l-7 7 7 7" />
  </Base>
);
