import { keepPreviousData, useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, query } from "./client";
import type {
  AuthResponse,
  ConversationListResponse,
  EventDetail,
  MessageListResponse,
  MetaResponse,
  MyApplicationsResponse,
  MyEventsResponse,
  NotificationListResponse,
  TagListResponse,
  ViewportResponse,
} from "./client";
import type { Filters, Viewport } from "../stores/boardStore";

export function useMeta() {
  return useQuery({
    queryKey: ["meta"],
    queryFn: () => api.get<MetaResponse>("/api/v1/meta"),
    staleTime: Infinity,
  });
}

export function useMe() {
  return useQuery({
    queryKey: ["auth", "me"],
    queryFn: () =>
      api.get<AuthResponse>("/api/v1/auth/me").then(
        (res) => res.user,
        () => null,
      ),
    staleTime: 60_000,
  });
}

export function useInvalidateMe() {
  const client = useQueryClient();
  return () => client.invalidateQueries({ queryKey: ["auth", "me"] });
}

const round = (n: number) => Math.round(n * 10_000) / 10_000;

export function useViewportEvents(viewport: Viewport | null, filters: Filters) {
  const zoomInt = viewport ? Math.floor(viewport.zoom) : null;
  const bbox = viewport
    ? [viewport.bbox.west, viewport.bbox.south, viewport.bbox.east, viewport.bbox.north]
        .map(round)
        .join(",")
    : null;

  return useQuery({
    queryKey: ["events", bbox, zoomInt, filters],
    enabled: bbox !== null,
    placeholderData: keepPreviousData,
    queryFn: () =>
      api.get<ViewportResponse>(
        `/api/v1/events${query({
          bbox: bbox!,
          zoom: zoomInt!,
          types: filters.types.join(","),
          tags: filters.tags.join(","),
          applyable: filters.applyableOnly ? true : undefined,
          q: filters.q,
        })}`,
      ),
  });
}

export function useNotifications(enabled: boolean) {
  return useQuery({
    queryKey: ["notifications"],
    enabled,
    queryFn: () => api.get<NotificationListResponse>("/api/v1/notifications"),
  });
}

export function useConversations(enabled: boolean) {
  return useQuery({
    queryKey: ["conversations"],
    enabled,
    queryFn: () => api.get<ConversationListResponse>("/api/v1/conversations"),
  });
}

export function useMessages(conversationId: string | undefined) {
  return useInfiniteQuery({
    queryKey: ["messages", conversationId],
    enabled: !!conversationId,
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) =>
      api.get<MessageListResponse>(
        `/api/v1/conversations/${conversationId}/messages${query({ cursor: pageParam ?? undefined })}`,
      ),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });
}

export function useMyEvents(enabled: boolean) {
  return useQuery({
    queryKey: ["myEvents"],
    enabled,
    queryFn: () => api.get<MyEventsResponse>("/api/v1/me/events"),
  });
}

export function useReceivedApplications(enabled: boolean) {
  return useQuery({
    queryKey: ["myApplications", "received"],
    enabled,
    queryFn: () => api.get<MyApplicationsResponse>("/api/v1/me/applications?role=received"),
  });
}

export function useTagSearch(q: string) {
  return useQuery({
    queryKey: ["tags", q],
    queryFn: () => api.get<TagListResponse>(`/api/v1/tags${query({ q })}`),
    staleTime: 30_000,
  });
}

export function useEventDetail(id: string | undefined) {
  return useQuery({
    queryKey: ["event", id],
    enabled: !!id,
    queryFn: () => api.get<EventDetail>(`/api/v1/events/${id}`),
  });
}
