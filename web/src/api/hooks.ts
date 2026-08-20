import { keepPreviousData, useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, query } from "./client";
import { boardEvent, boardEvents } from "./paths";
import type {
  AuthResponse,
  ConversationListResponse,
  EventDetail,
  FeaturesResponse,
  MessageListResponse,
  MetaResponse,
  MyApplicationsResponse,
  MyEventsResponse,
  NotificationListResponse,
  PlaceSuggestions,
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

const FEATURES_REFRESH_MS = 600_000;

export function useFeatures() {
  return useQuery({
    queryKey: ["features"],
    queryFn: () => api.get<FeaturesResponse>("/api/v1/features"),
    staleTime: FEATURES_REFRESH_MS,
    refetchInterval: FEATURES_REFRESH_MS,
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
    staleTime: (query) => (query.state.data?.emailVerified ? Infinity : 60_000),
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
        `${boardEvents(filters.board)}${query({
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

const PLACE_MIN_QUERY = 3;

export function usePlaceSearch(q: string, near: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ["places", q, near],
    enabled: enabled && q.trim().length >= PLACE_MIN_QUERY,
    queryFn: () => api.get<PlaceSuggestions>(`/api/v1/places${query({ q: q.trim(), near })}`),
    placeholderData: keepPreviousData,
    staleTime: 300_000,
    retry: false,
  });
}

export function useEventDetail(id: string | undefined, boardOwner: string | null = null) {
  return useQuery({
    queryKey: ["event", id],
    enabled: !!id,
    queryFn: () => api.get<EventDetail>(boardEvent(boardOwner, id!)),
  });
}
