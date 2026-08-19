import { useEffect, useRef } from "react";
import maplibregl from "maplibre-gl";
import type { GeoJSONSource, MapMouseEvent } from "maplibre-gl";
import type { Feature, FeatureCollection, Point } from "geojson";
import { useMatch, useNavigate } from "react-router";
import { useMeta, useViewportEvents } from "../../api/hooks";
import { ApiError, api, query } from "../../api/client";
import type { MetaResponse, ViewportResponse } from "../../api/client";
import { strings } from "../../i18n/strings";
import { boardEvents, newNotePath, notePath } from "../../api/paths";
import { clusterPushpinDataUri, pushpinDataUri } from "../../ui/pushpin";
import { PinIcon, PushpinIcon } from "../../ui/icons";
import { toast } from "../../ui/toast";
import { loadNoteFont } from "../../ui/noteFont";
import { useIsPhone } from "../../ui/useMediaQuery";
import { loadSavedPosition, savePosition, useBoardStore } from "../../stores/boardStore";

const FALLBACK_ZOOM = 13;
const CALLOUT_GAP = 8;
const ARROW_INSET = 18;
const MAX_SPLIT_ZOOM = 18;
const SPLIT_EPS = 45 / 2 ** MAX_SPLIT_ZOOM;

function defaultCenter(): [number, number] {
  const [lng, lat] = __DEFAULT_CENTER__.split(",").map(Number);
  return [lng, lat];
}

function countLabel(count: number): string {
  return count > 99 ? "99+" : String(count);
}

function statusText(total: number, short: boolean, personal: boolean): string {
  if (total === 0) {
    if (personal) return strings.scope.emptyPersonal;
    return short ? strings.board.emptyViewportShort : strings.board.emptyViewport;
  }
  return short ? strings.board.notesHereShort(total) : strings.board.notesHere(total);
}

function reducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

const FLY_TO_ME = { zoom: 14, speed: 2.8, curve: 1.3 } as const;

function wrapLng(value: number): number {
  return ((((value + 180) % 360) + 360) % 360) - 180;
}

function normalizeBounds(bounds: maplibregl.LngLatBounds) {
  let west = bounds.getWest();
  let east = bounds.getEast();
  if (east - west >= 360) {
    west = -180;
    east = 180;
  } else {
    west = wrapLng(west);
    east = wrapLng(east);
    if (east === -180) east = 180;
  }
  const south = Math.min(Math.max(bounds.getSouth(), -85), 84.99);
  const north = Math.min(Math.max(bounds.getNorth(), south + 0.01), 85);
  return { west, south, east, north };
}

function toFeatureCollection(
  data: ViewportResponse,
  selectedId: string | undefined,
): FeatureCollection {
  const pins: Feature[] = data.items.map((pin) => ({
    type: "Feature",
    geometry: { type: "Point", coordinates: [pin.location.lng, pin.location.lat] },
    properties: {
      kind: "pin",
      id: pin.id,
      type: pin.type,
      title: pin.title,
      score: pin.score,
      selected: pin.id === selectedId,
    },
  }));
  const clusters: Feature[] = data.clusters.map((cluster) => ({
    type: "Feature",
    geometry: { type: "Point", coordinates: [cluster.location.lng, cluster.location.lat] },
    properties: {
      kind: "cluster",
      count: cluster.count,
      countLabel: countLabel(cluster.count),
      west: cluster.bounds.west,
      south: cluster.bounds.south,
      east: cluster.bounds.east,
      north: cluster.bounds.north,
    },
  }));
  return { type: "FeatureCollection", features: [...pins, ...clusters] };
}

function loadImage(map: maplibregl.Map, name: string, uri: string, size: [number, number]): Promise<void> {
  return new Promise((resolve) => {
    if (map.hasImage(name)) return resolve();
    const img = new Image(size[0], size[1]);
    img.onload = () => {
      if (!map.hasImage(name)) map.addImage(name, img, { pixelRatio: 2 });
      resolve();
    };
    img.onerror = () => resolve();
    img.src = uri;
  });
}

const BUILDING_SOURCE_LAYER = "building";
const TRANSIT_LAYER = "poi_transit";

const RAIL_STATIONS: maplibregl.FilterSpecification = [
  "all",
  ["match", ["get", "class"], ["railway", "rail"], true, false],
  ["match", ["get", "subclass"], ["station", "subway", "halt", "light_rail"], true, false],
];

const STATION_ICON = [
  "match",
  ["get", "subclass"],
  "subway",
  "railway_metro",
  "light_rail",
  "railway_light",
  "railway",
];

function flattenBaseMap(map: maplibregl.Map): void {
  const layers = map.getStyle().layers ?? [];
  let hasBuildings = false;
  for (const layer of layers) {
    const sourceLayer = (layer as { "source-layer"?: string })["source-layer"];
    if (layer.id === TRANSIT_LAYER) {
      map.setFilter(layer.id, RAIL_STATIONS);
      map.setLayoutProperty(layer.id, "icon-image", STATION_ICON);
      map.setLayoutProperty(layer.id, "icon-size", 1);
    } else if (layer.type === "fill-extrusion" || sourceLayer === "poi") {
      map.setLayoutProperty(layer.id, "visibility", "none");
    } else if (sourceLayer === BUILDING_SOURCE_LAYER && layer.type === "fill") {
      hasBuildings = true;
      const minzoom = (layer as { minzoom?: number }).minzoom ?? 13;
      map.setLayerZoomRange(layer.id, minzoom, 24);
      map.setPaintProperty(layer.id, "fill-color", "hsl(36,10%,79%)");
      map.setPaintProperty(layer.id, "fill-outline-color", "hsl(36,15%,64%)");
    }
  }

  if (hasBuildings && map.getSource("openmaptiles") && !map.getLayer("housenumbers")) {
    map.addLayer({
      id: "housenumbers",
      type: "symbol",
      source: "openmaptiles",
      "source-layer": "housenumber",
      minzoom: 17.5,
      layout: {
        "text-field": ["get", "housenumber"],
        "text-font": ["Noto Sans Regular"],
        "text-size": 9.5,
        "text-padding": 4,
      },
      paint: {
        "text-color": "hsl(35,14%,52%)",
        "text-halo-color": "rgba(253,251,242,0.85)",
        "text-halo-width": 1,
      },
    });
  }
}

async function registerPushpins(map: maplibregl.Map, meta: MetaResponse): Promise<void> {
  await Promise.all(
    meta.types.flatMap((t) => [
      loadImage(map, `pin-${t.key}`, pushpinDataUri(t.color), [48, 64]),
      loadImage(map, `pin-${t.key}-pressed`, pushpinDataUri(t.color, true), [48, 64]),
    ]),
  );
}

const PIN_ICON = [
  "concat",
  "pin-",
  ["get", "type"],
  ["case", ["get", "selected"], "-pressed", ""],
];

export function BoardMap() {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const popupRef = useRef<maplibregl.Popup | null>(null);
  const draftMarkerRef = useRef<maplibregl.Marker | null>(null);
  const loadedRef = useRef(false);
  const easedToRef = useRef<string | null>(null);
  const flightRef = useRef<[number, number] | null>(null);
  const locatingRef = useRef(false);

  const { data: meta } = useMeta();
  const viewport = useBoardStore((s) => s.viewport);
  const filters = useBoardStore((s) => s.filters);
  const setViewport = useBoardStore((s) => s.setViewport);
  const crosshair = useBoardStore((s) => s.crosshair);
  const draftLocation = useBoardStore((s) => s.draftLocation);
  const setDraftLocation = useBoardStore((s) => s.setDraftLocation);
  const setDraftPinEl = useBoardStore((s) => s.setDraftPinEl);
  const draftPinEl = useBoardStore((s) => s.draftPinEl);
  const isPhone = useIsPhone();
  const boardRef = useRef(filters.board);
  boardRef.current = filters.board;
  const navigate = useNavigate();
  const selectedMatch = useMatch("/events/:id");
  const selectedOnBoard = useMatch("/boards/:ownerId/events/:id");
  const selectedId = selectedMatch?.params.id ?? selectedOnBoard?.params.id;

  const { data, error } = useViewportEvents(viewport, filters);

  useEffect(() => {
    if (!filters.board || !(error instanceof ApiError)) return;
    if (error.status === 401 || error.status === 403 || error.status === 404) {
      toast(strings.scope.unavailable, "info");
      navigate("/", { replace: true });
    }
  }, [error, filters.board, navigate]);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const saved = loadSavedPosition();
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: __MAP_STYLE_URL__,
      center: saved ? [saved.center.lng, saved.center.lat] : defaultCenter(),
      zoom: saved?.zoom ?? FALLBACK_ZOOM,
    });
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), "top-right");
    mapRef.current = map;
    if (import.meta.env.DEV) {
      (window as unknown as { __corkboardMap?: maplibregl.Map }).__corkboardMap = map;
    }

    const publishViewport = () => {
      const center = map.getCenter();
      setViewport({
        bbox: normalizeBounds(map.getBounds()),
        zoom: map.getZoom(),
        center: { lng: center.lng, lat: center.lat },
      });
      savePosition({ center: { lng: center.lng, lat: center.lat }, zoom: map.getZoom() });
    };

    let debounce: number | undefined;
    map.on("moveend", () => {
      window.clearTimeout(debounce);
      debounce = window.setTimeout(publishViewport, 300);
    });

    map.on("styleimagemissing", (event) => {
      const name = event.id;
      if (!name.startsWith("cluster-")) return;
      void loadImage(map, name, clusterPushpinDataUri(name.slice("cluster-".length)), [64, 80]);
    });

    map.on("load", () => {
      flattenBaseMap(map);
      if (map.getCanvasContainer().offsetWidth <= 640) {
        map.getContainer()
          .querySelector(".maplibregl-ctrl-attrib")
          ?.classList.remove("maplibregl-compact-show");
      }
      map.addSource("events", {
        type: "geojson",
        data: { type: "FeatureCollection", features: [] },
      });
      map.addLayer({
        id: "pins",
        type: "symbol",
        source: "events",
        filter: ["==", ["get", "kind"], "pin"],
        layout: {
          "icon-image": PIN_ICON as never,
          "icon-anchor": "bottom",
          "icon-allow-overlap": true,
          "icon-size": ["interpolate", ["linear"], ["zoom"], 10, 0.8, 15, 1.05],
        },
      });
      map.addLayer({
        id: "clusters",
        type: "symbol",
        source: "events",
        filter: ["==", ["get", "kind"], "cluster"],
        layout: {
          "icon-image": ["concat", "cluster-", ["get", "countLabel"]] as never,
          "icon-anchor": "bottom",
          "icon-allow-overlap": true,
        },
      });
      for (const layer of ["pins", "clusters"]) {
        map.on("mouseenter", layer, () => {
          map.getCanvas().style.cursor = "pointer";
        });
        map.on("mouseleave", layer, () => {
          map.getCanvas().style.cursor = "";
        });
      }
      loadedRef.current = true;
      publishViewport();
    });

    if (!saved && "geolocation" in navigator) {
      navigator.geolocation.getCurrentPosition(
        (pos) => map.flyTo({ center: [pos.coords.longitude, pos.coords.latitude], ...FLY_TO_ME }),
        () => undefined,
        { timeout: 5000 },
      );
    }

    return () => {
      loadedRef.current = false;
      map.remove();
      mapRef.current = null;
    };
  }, [setViewport]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !meta) return;
    const apply = () => void registerPushpins(map, meta);
    if (loadedRef.current) apply();
    else map.once("load", apply);
  }, [meta]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !data) return;
    const apply = () => {
      (map.getSource("events") as GeoJSONSource | undefined)?.setData(
        toFeatureCollection(data, selectedId),
      );
    };
    if (loadedRef.current) apply();
    else map.once("load", apply);
  }, [data, selectedId]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !data || !selectedId) {
      easedToRef.current = null;
      return;
    }
    if (easedToRef.current === selectedId) return;
    const pin = data.items.find((p) => p.id === selectedId);
    if (!pin) return;
    easedToRef.current = selectedId;
    map.easeTo({
      center: [pin.location.lng, pin.location.lat],
      offset: [-160, 0],
      duration: reducedMotion() ? 0 : 240,
    });
  }, [data, selectedId]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !meta) return;

    const openPinPopup = async (feature: Feature) => {
      const props = feature.properties as { id: string; type: string; title: string; score: number };
      await loadNoteFont(props.title);
      const type = meta.types.find((t) => t.key === props.type);

      const container = document.createElement("div");
      container.className = "paper-note";

      const pinImg = document.createElement("img");
      pinImg.className = "paper-note-pin";
      pinImg.alt = "";
      pinImg.src = pushpinDataUri(type?.color ?? "#8A8A8A");

      const title = document.createElement("p");
      title.className = "note-title";
      title.textContent = props.title;

      const metaRow = document.createElement("div");
      metaRow.className = "meta-row";
      const chip = document.createElement("span");
      chip.className = "type-chip";
      chip.style.background = type?.color ?? "#8A8A8A";
      chip.textContent = type?.label ?? props.type;
      metaRow.append(chip, ` · ${strings.board.points(props.score)}`);

      const more = document.createElement("button");
      more.className = "primary sm note-open";
      more.textContent = strings.board.readMore;
      more.addEventListener("click", () => {
        popupRef.current?.remove();
        navigate(notePath(boardRef.current, props.id));
      });

      container.append(pinImg, title, metaRow, more);
      popupRef.current?.remove();
      popupRef.current = new maplibregl.Popup({ className: "note-popup", offset: 26 })
        .setLngLat((feature.geometry as Point).coordinates as [number, number])
        .setDOMContent(container)
        .addTo(map);
    };

    const openMemberList = async (feature: Feature) => {
      const props = feature.properties as {
        count: number; west: number; south: number; east: number; north: number;
      };
      const eps = 1e-6;
      const res = await api.get<ViewportResponse>(
        `${boardEvents(boardRef.current)}${query({
          bbox: [props.west - eps, props.south - eps, props.east + eps, props.north + eps].join(","),
          zoom: 22,
          clustered: false,
          limit: 100,
        })}`,
      );

      const heading = strings.board.clusterList(props.count);
      await loadNoteFont(heading);

      const container = document.createElement("div");
      container.className = "paper-note cluster-list";

      const title = document.createElement("p");
      title.className = "note-title";
      title.textContent = heading;
      container.append(title);

      for (const pin of res.items) {
        const row = document.createElement("button");
        row.className = "cluster-list-row";
        const dot = document.createElement("span");
        dot.className = "type-dot";
        dot.style.background = meta.types.find((t) => t.key === pin.type)?.color ?? "#8A8A8A";
        row.append(dot, ` ${pin.title}`);
        row.addEventListener("click", () => {
          popupRef.current?.remove();
          navigate(notePath(boardRef.current, pin.id));
        });
        container.append(row);
      }

      popupRef.current?.remove();
      popupRef.current = new maplibregl.Popup({ className: "note-popup", offset: 34, maxWidth: "280px" })
        .setLngLat((feature.geometry as Point).coordinates as [number, number])
        .setDOMContent(container)
        .addTo(map);
    };

    const onClick = (event: MapMouseEvent) => {
      if (useBoardStore.getState().crosshair) {
        setDraftLocation({ lng: event.lngLat.lng, lat: event.lngLat.lat });
        return;
      }
      const feature = map.queryRenderedFeatures(event.point, { layers: ["pins", "clusters"] })[0];
      if (!feature) return;
      if (feature.properties?.kind === "pin") {
        void openPinPopup(feature as unknown as Feature);
        return;
      }
      const props = feature.properties as { west: number; south: number; east: number; north: number };
      const extent = Math.max(props.east - props.west, props.north - props.south);
      if (extent < SPLIT_EPS) {
        void openMemberList(feature as unknown as Feature);
      } else {
        popupRef.current?.remove();
        map.fitBounds(
          [[props.west, props.south], [props.east, props.north]],
          {
            padding: 96,
            maxZoom: MAX_SPLIT_ZOOM,
            duration: reducedMotion() ? 0 : 280,
          },
        );
      }
    };

    map.on("click", onClick);
    return () => {
      map.off("click", onClick);
    };
  }, [meta, navigate, setDraftLocation]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    if (draftLocation && !draftMarkerRef.current) {
      const element = document.createElement("div");
      element.className = "draft-pin-wrap";
      const image = document.createElement("img");
      image.className = "draft-pin";
      image.alt = "";
      image.width = 30;
      image.height = 40;
      image.src = pushpinDataUri("#B3352C");
      const host = document.createElement("div");
      host.className = "draft-callout-host";
      for (const type of ["mousedown", "touchstart", "pointerdown"]) {
        host.addEventListener(type, (event) => event.stopPropagation());
      }
      element.append(image, host);
      const marker = new maplibregl.Marker({ element, draggable: true, anchor: "bottom" })
        .setLngLat([draftLocation.lng, draftLocation.lat])
        .addTo(map);
      marker.on("dragend", () => {
        const pos = marker.getLngLat();
        setDraftLocation({ lng: pos.lng, lat: pos.lat });
      });
      draftMarkerRef.current = marker;
      setDraftPinEl(host);
    } else if (draftLocation && draftMarkerRef.current) {
      draftMarkerRef.current.setLngLat([draftLocation.lng, draftLocation.lat]);
    } else if (!draftLocation && draftMarkerRef.current) {
      draftMarkerRef.current.remove();
      draftMarkerRef.current = null;
      setDraftPinEl(null);
    }
  }, [draftLocation, setDraftLocation, setDraftPinEl]);

  useEffect(() => {
    const map = mapRef.current;
    const marker = draftMarkerRef.current;
    const host = draftPinEl;
    if (!map || !marker || !host) return;

    const wrap = marker.getElement();
    const place = () => {
      const callout = host.firstElementChild as HTMLElement | null;
      if (!callout) return;
      const bounds = map.getContainer().getBoundingClientRect();
      const pin = wrap.getBoundingClientRect();
      const height = callout.offsetHeight;
      const needed = height + CALLOUT_GAP;

      const roomAbove = pin.top - bounds.top;
      const roomBelow = bounds.bottom - pin.bottom;
      const below = roomAbove < needed && roomBelow > roomAbove;
      host.classList.toggle("is-below", below);

      const half = callout.offsetWidth / 2;
      const centre = pin.left + pin.width / 2;
      const pastLeft = Math.max(0, bounds.left + CALLOUT_GAP - (centre - half));
      const pastRight = Math.max(0, centre + half - (bounds.right - CALLOUT_GAP));
      const shift = pastLeft - pastRight;
      host.style.setProperty("--callout-shift", `${shift}px`);

      const reach = Math.max(0, half - ARROW_INSET);
      host.style.setProperty("--callout-arrow-shift", `${Math.min(reach, Math.max(-reach, shift))}px`);

      const top = below ? pin.bottom + CALLOUT_GAP : pin.top - CALLOUT_GAP - height;
      const pastTop = Math.max(0, bounds.top + CALLOUT_GAP - top);
      const pastBottom = Math.max(0, top + height - (bounds.bottom - CALLOUT_GAP));
      host.style.setProperty("--callout-lift", `${pastTop - pastBottom}px`);
    };

    place();
    map.on("move", place);
    map.on("resize", place);
    const resizes = new ResizeObserver(place);
    resizes.observe(host);
    return () => {
      map.off("move", place);
      map.off("resize", place);
      resizes.disconnect();
    };
  }, [draftLocation, draftPinEl]);

  function locateMe() {
    const map = mapRef.current;
    if (!map) return;

    const inFlight = flightRef.current;
    if (inFlight) {
      flightRef.current = null;
      map.stop();
      map.jumpTo({ center: inFlight, zoom: FLY_TO_ME.zoom });
      return;
    }
    if (locatingRef.current) return;

    locatingRef.current = true;
    navigator.geolocation?.getCurrentPosition(
      (pos) => {
        locatingRef.current = false;
        const center: [number, number] = [pos.coords.longitude, pos.coords.latitude];
        flightRef.current = center;
        map.once("moveend", () => {
          flightRef.current = null;
        });
        map.flyTo({ center, ...FLY_TO_ME });
      },
      () => {
        locatingRef.current = false;
        toast(strings.board.locateFailed, "info");
      },
      { timeout: 5000, maximumAge: 60_000 },
    );
  }

  return (
    <div className="map-wrap">
      <div ref={containerRef} className={`map${crosshair ? " crosshair" : ""}`} />
      <button
        type="button"
        className="locate-btn"
        onClick={locateMe}
        title={strings.board.useMyLocation}
        aria-label={strings.board.useMyLocation}
      >
        <PinIcon size={15} />
        <span className="locate-label">{strings.board.useMyLocation}</span>
      </button>
      {!crosshair && (
        <button
          type="button"
          className="pin-fab"
          onClick={() => navigate(newNotePath(filters.board))}
          title={strings.board.pinANote}
          aria-label={strings.board.pinANote}
        >
          <PushpinIcon size={23} />
        </button>
      )}
      {data && (
        <div className="status-line" role="status">
          {statusText(data.total, isPhone, filters.board !== null)}
        </div>
      )}
    </div>
  );
}
