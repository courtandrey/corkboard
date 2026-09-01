import { useMeta } from "../api/hooks";
import type { TypeMeta } from "../api/client";
import type { BoardRef } from "../api/paths";

export type ScopeKind = "global" | "personal";

export const scopeOf = (board: BoardRef): ScopeKind => (board ? "personal" : "global");

export function useScopeTypes(board: BoardRef): TypeMeta[] {
  const { data: meta } = useMeta();
  if (!meta) return [];
  const allowed = meta.scopes.find((s) => s.key === scopeOf(board))?.types ?? [];
  return meta.types.filter((t) => allowed.includes(t.key));
}
