import type { components } from "./types.gen";

export type MetaResponse = components["schemas"]["MetaResponse"];
export type HealthResponse = components["schemas"]["HealthResponse"];
export type AuthResponse = components["schemas"]["AuthResponse"];
export type UserResponse = components["schemas"]["UserResponse"];
export type RegisterRequest = components["schemas"]["RegisterRequest"];
export type LoginRequest = components["schemas"]["LoginRequest"];

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    detail: string,
    readonly fields?: Record<string, string>,
  ) {
    super(detail);
  }
}

interface ProblemBody {
  code?: string;
  detail?: string;
  fields?: Record<string, string>;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init,
  });
  if (!res.ok) {
    const problem: ProblemBody | null = await res.json().catch(() => null);
    throw new ApiError(
      res.status,
      problem?.code ?? "unknown",
      problem?.detail ?? `${res.status} ${res.statusText}`,
      problem?.fields,
    );
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T = void>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body ? JSON.stringify(body) : undefined }),
};
