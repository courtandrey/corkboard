import { create } from "zustand";
import type { LatLng } from "../api/client";

export interface Bbox {
  west: number;
  south: number;
  east: number;
  north: number;
}

export interface Viewport {
  bbox: Bbox;
  zoom: number;
  center: LatLng;
}

export interface Filters {
  types: string[];
  tags: string[];
  applyableOnly: boolean;
  q: string;
}

interface BoardState {
  viewport: Viewport | null;
  filters: Filters;
  crosshair: boolean;
  draftLocation: LatLng | null;
  setViewport: (viewport: Viewport) => void;
  setFilters: (patch: Partial<Filters>) => void;
  toggleType: (key: string) => void;
  setCrosshair: (on: boolean) => void;
  setDraftLocation: (location: LatLng | null) => void;
}

const POSITION_KEY = "corkboard.position";

export interface SavedPosition {
  center: LatLng;
  zoom: number;
}

export function loadSavedPosition(): SavedPosition | null {
  try {
    const raw = localStorage.getItem(POSITION_KEY);
    return raw ? (JSON.parse(raw) as SavedPosition) : null;
  } catch {
    return null;
  }
}

export function savePosition(position: SavedPosition): void {
  try {
    localStorage.setItem(POSITION_KEY, JSON.stringify(position));
  } catch {
    /* private mode etc. — the NYC fallback covers it */
  }
}

export function initialFilters(search: URLSearchParams): Filters {
  return {
    types: search.get("types")?.split(",").filter(Boolean) ?? [],
    tags: search.get("tags")?.split(",").filter(Boolean) ?? [],
    applyableOnly: search.get("applyable") === "true",
    q: search.get("q") ?? "",
  };
}

export function filtersToSearch(filters: Filters): URLSearchParams {
  const search = new URLSearchParams();
  if (filters.types.length) search.set("types", filters.types.join(","));
  if (filters.tags.length) search.set("tags", filters.tags.join(","));
  if (filters.applyableOnly) search.set("applyable", "true");
  if (filters.q) search.set("q", filters.q);
  return search;
}

export const useBoardStore = create<BoardState>((set) => ({
  viewport: null,
  filters: initialFilters(new URLSearchParams(window.location.search)),
  crosshair: false,
  draftLocation: null,
  setViewport: (viewport) => set({ viewport }),
  setFilters: (patch) => set((s) => ({ filters: { ...s.filters, ...patch } })),
  toggleType: (key) =>
    set((s) => ({
      filters: {
        ...s.filters,
        types: s.filters.types.includes(key)
          ? s.filters.types.filter((t) => t !== key)
          : [...s.filters.types, key],
      },
    })),
  setCrosshair: (on) => set({ crosshair: on, draftLocation: null }),
  setDraftLocation: (location) => set({ draftLocation: location }),
}));
