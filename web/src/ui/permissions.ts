import { useMe } from "../api/hooks";

export type Permission =
  | "EVENT_HIDE"
  | "EVENT_CREATE"
  | "EVENT_VOTE"
  | "EVENT_REPORT"
  | "EVENT_APPLY"
  | "MESSAGE_SEND"
  | "CONNECTION_MANAGE"
  | "EVENT_TAKE_DOWN_ANY"
  | "REPORT_QUEUE_VIEW"
  | "ROLE_MANAGE"
  | "FEATURE_FLAG_MANAGE";

export function usePermissions(): { can: (permission: Permission) => boolean; loaded: boolean } {
  const { data: me, isLoading } = useMe();
  const held = me?.permissions ?? [];
  return {
    can: (permission) => held.includes(permission),
    loaded: !isLoading,
  };
}
