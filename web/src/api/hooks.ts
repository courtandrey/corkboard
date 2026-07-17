import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, query } from "./client";
import type { AuthResponse, EventDetail, MetaResponse, ViewportResponse } from "./client";
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
  const zoomBand = viewport && viewport.zoom <= 13 ? "low" : "high";
  const bbox = viewport
    ? [viewport.bbox.west, viewport.bbox.south, viewport.bbox.east, viewport.bbox.north]
        .map(round)
        .join(",")
    : null;

  return useQuery({
    queryKey: ["events", bbox, zoomBand, filters],
    enabled: bbox !== null,
    placeholderData: keepPreviousData,
    queryFn: () =>
      api.get<ViewportResponse>(
        `/api/v1/events${query({
          bbox: bbox!,
          zoom: Math.round(viewport!.zoom),
          types: filters.types.join(","),
          tags: filters.tags.join(","),
          applyable: filters.applyableOnly ? true : undefined,
          q: filters.q,
        })}`,
      ),
  });
}

export function useEventDetail(id: string | undefined) {
  return useQuery({
    queryKey: ["event", id],
    enabled: !!id,
    queryFn: () => api.get<EventDetail>(`/api/v1/events/${id}`),
  });
}
