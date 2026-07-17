import { useEffect, useRef } from "react";
import maplibregl from "maplibre-gl";
import type { GeoJSONSource, MapMouseEvent } from "maplibre-gl";
import type { FeatureCollection, Point } from "geojson";
import { useMatch, useNavigate } from "react-router";
import { useMeta, useViewportEvents } from "../../api/hooks";
import type { EventPin, MetaResponse } from "../../api/client";
import { strings } from "../../i18n/strings";
import { pushpinDataUri } from "../../ui/pushpin";
import { loadSavedPosition, savePosition, useBoardStore } from "../../stores/boardStore";

const FALLBACK_ZOOM = 13;
const OVERLAP_ZOOM = 13.05;

function defaultCenter(): [number, number] {
  const [lng, lat] = __DEFAULT_CENTER__.split(",").map(Number);
  return [lng, lat];
}

function toFeatureCollection(items: EventPin[], selectedId: string | undefined): FeatureCollection {
  return {
    type: "FeatureCollection",
    features: items.map((pin) => ({
      type: "Feature",
      geometry: { type: "Point", coordinates: [pin.location.lng, pin.location.lat] },
      properties: {
        id: pin.id,
        type: pin.type,
        title: pin.title,
        score: pin.score,
        selected: pin.id === selectedId,
      },
    })),
  };
}

async function registerPushpins(map: maplibregl.Map, meta: MetaResponse): Promise<void> {
  const load = (name: string, uri: string) =>
    new Promise<void>((resolve) => {
      if (map.hasImage(name)) return resolve();
      const img = new Image(48, 64);
      img.onload = () => {
        if (!map.hasImage(name)) map.addImage(name, img, { pixelRatio: 2 });
        resolve();
      };
      img.onerror = () => resolve();
      img.src = uri;
    });
  await Promise.all(
    meta.types.flatMap((t) => [
      load(`pin-${t.key}`, pushpinDataUri(t.color)),
      load(`pin-${t.key}-pressed`, pushpinDataUri(t.color, true)),
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

  const { data: meta } = useMeta();
  const viewport = useBoardStore((s) => s.viewport);
  const filters = useBoardStore((s) => s.filters);
  const setViewport = useBoardStore((s) => s.setViewport);
  const crosshair = useBoardStore((s) => s.crosshair);
  const draftLocation = useBoardStore((s) => s.draftLocation);
  const setDraftLocation = useBoardStore((s) => s.setDraftLocation);
  const navigate = useNavigate();
  const selectedMatch = useMatch("/events/:id");
  const selectedId = selectedMatch?.params.id;

  const { data } = useViewportEvents(viewport, filters);

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

    const publishViewport = () => {
      const bounds = map.getBounds();
      const center = map.getCenter();
      setViewport({
        bbox: {
          west: bounds.getWest(),
          south: bounds.getSouth(),
          east: bounds.getEast(),
          north: bounds.getNorth(),
        },
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

    map.on("load", () => {
      map.addSource("events", {
        type: "geojson",
        data: { type: "FeatureCollection", features: [] },
      });
      const symbol = (id: string, overlap: boolean) =>
        map.addLayer({
          id,
          type: "symbol",
          source: "events",
          minzoom: overlap ? OVERLAP_ZOOM : 0,
          maxzoom: overlap ? 24 : OVERLAP_ZOOM,
          layout: {
            "icon-image": PIN_ICON as never,
            "icon-anchor": "bottom",
            "icon-allow-overlap": overlap,
            "icon-padding": 1,
            "icon-size": ["interpolate", ["linear"], ["zoom"], 10, 0.8, 15, 1.05],
          },
        });
      symbol("pins-low", false);
      symbol("pins-high", true);
      for (const layer of ["pins-low", "pins-high"]) {
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
        (pos) => map.flyTo({ center: [pos.coords.longitude, pos.coords.latitude], zoom: 14 }),
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
        toFeatureCollection(data.items, selectedId),
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
      duration: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 450,
    });
  }, [data, selectedId]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !meta) return;

    const onPinClick = (event: MapMouseEvent) => {
      if (useBoardStore.getState().crosshair) return;
      const feature = map.queryRenderedFeatures(event.point, { layers: ["pins-low", "pins-high"] })[0];
      if (!feature) return;
      const props = feature.properties as { id: string; type: string; title: string; score: number };
      const type = meta.types.find((t) => t.key === props.type);

      popupRef.current?.remove();
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
      more.textContent = strings.board.readMore;
      more.addEventListener("click", () => {
        popupRef.current?.remove();
        navigate(`/events/${props.id}`);
      });

      container.append(pinImg, title, metaRow, more);

      popupRef.current = new maplibregl.Popup({ className: "note-popup", offset: 26 })
        .setLngLat((feature.geometry as Point).coordinates as [number, number])
        .setDOMContent(container)
        .addTo(map);
    };

    const onMapClick = (event: MapMouseEvent) => {
      if (!useBoardStore.getState().crosshair) return;
      setDraftLocation({ lng: event.lngLat.lng, lat: event.lngLat.lat });
    };

    map.on("click", "pins-low", onPinClick);
    map.on("click", "pins-high", onPinClick);
    map.on("click", onMapClick);
    return () => {
      map.off("click", "pins-low", onPinClick);
      map.off("click", "pins-high", onPinClick);
      map.off("click", onMapClick);
    };
  }, [meta, navigate, setDraftLocation]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    if (draftLocation && !draftMarkerRef.current) {
      const element = document.createElement("img");
      element.className = "draft-pin";
      element.alt = "";
      element.width = 30;
      element.height = 40;
      element.src = pushpinDataUri("#B3352C");
      const marker = new maplibregl.Marker({ element, draggable: true, anchor: "bottom" })
        .setLngLat([draftLocation.lng, draftLocation.lat])
        .addTo(map);
      marker.on("dragend", () => {
        const pos = marker.getLngLat();
        setDraftLocation({ lng: pos.lng, lat: pos.lat });
      });
      draftMarkerRef.current = marker;
    } else if (draftLocation && draftMarkerRef.current) {
      draftMarkerRef.current.setLngLat([draftLocation.lng, draftLocation.lat]);
    } else if (!draftLocation && draftMarkerRef.current) {
      draftMarkerRef.current.remove();
      draftMarkerRef.current = null;
    }
  }, [draftLocation, setDraftLocation]);

  function locateMe() {
    navigator.geolocation?.getCurrentPosition(
      (pos) => mapRef.current?.flyTo({ center: [pos.coords.longitude, pos.coords.latitude], zoom: 14 }),
      () => undefined,
      { timeout: 5000 },
    );
  }

  const shown = data?.items.length ?? 0;

  return (
    <div className="map-wrap">
      <div ref={containerRef} className={`map${crosshair ? " crosshair" : ""}`} />
      <button type="button" className="locate-btn" onClick={locateMe} title={strings.board.useMyLocation}>
        📍 {strings.board.useMyLocation}
      </button>
      {data && (
        <div className="status-line" role="status">
          {shown === 0
            ? strings.board.emptyViewport
            : strings.board.showing(shown, data.total, data.truncated || data.total >= 500)}
        </div>
      )}
    </div>
  );
}
