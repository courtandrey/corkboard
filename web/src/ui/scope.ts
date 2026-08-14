import { useMeta } from "../api/hooks";
import type { TypeMeta } from "../api/client";

export type ScopeKind = "global" | "personal";

export const scopeOf = (board: string | null): ScopeKind => (board ? "personal" : "global");

export function useScopeTypes(board: string | null): TypeMeta[] {
  const { data: meta } = useMeta();
  if (!meta) return [];
  const allowed = meta.scopes.find((s) => s.key === scopeOf(board))?.types ?? [];
  return meta.types.filter((t) => allowed.includes(t.key));
}
