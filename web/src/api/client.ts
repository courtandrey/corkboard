import type { components } from "./types.gen";

export type MetaResponse = components["schemas"]["MetaResponse"];
export type HealthResponse = components["schemas"]["HealthResponse"];
export type AuthResponse = components["schemas"]["AuthResponse"];
export type UserResponse = components["schemas"]["UserResponse"];
export type RegisterRequest = components["schemas"]["RegisterRequest"];
export type LoginRequest = components["schemas"]["LoginRequest"];
export type TypeMeta = components["schemas"]["TypeMeta"];
export type EventPin = components["schemas"]["EventPin"];
export type EventDetail = components["schemas"]["EventDetail"];
export type ViewportResponse = components["schemas"]["ViewportResponse"];
export type CreateEventRequest = components["schemas"]["CreateEventRequest"];
export type UpdateEventRequest = components["schemas"]["UpdateEventRequest"];
export type LatLng = components["schemas"]["LatLng"];
export type VoteResponse = components["schemas"]["VoteResponse"];
export type ApplyResponse = components["schemas"]["ApplyResponse"];
export type ConversationSummary = components["schemas"]["ConversationSummary"];
export type ConversationListResponse = components["schemas"]["ConversationListResponse"];
export type MessageResponse = components["schemas"]["MessageResponse"];
export type MessageListResponse = components["schemas"]["MessageListResponse"];
export type NotificationResponse = components["schemas"]["NotificationResponse"];
export type NotificationListResponse = components["schemas"]["NotificationListResponse"];
export type MyEventItem = components["schemas"]["MyEventItem"];
export type MyEventsResponse = components["schemas"]["MyEventsResponse"];
export type MyApplicationsResponse = components["schemas"]["MyApplicationsResponse"];
export type ApplicationItem = components["schemas"]["ApplicationItem"];
export type TagItem = components["schemas"]["TagItem"];
export type TagListResponse = components["schemas"]["TagListResponse"];
export type ReportRequest = components["schemas"]["ReportRequest"];

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
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T = void>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body ? JSON.stringify(body) : undefined }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
  del: (path: string) => request<void>(path, { method: "DELETE" }),
};

export function query(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, String(value));
  }
  const s = search.toString();
  return s ? `?${s}` : "";
}
