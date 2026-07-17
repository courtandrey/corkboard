import { useEffect, useRef } from "react";
import maplibregl from "maplibre-gl";
import type { GeoJSONSource, MapMouseEvent } from "maplibre-gl";
import type { FeatureCollection, Point } from "geojson";
import { useMatch, useNavigate } from "react-router";
import { useMeta, useViewportEvents } from "../../api/hooks";
import type { EventPin } from "../../api/client";
import { strings } from "../../i18n/strings";
import { loadSavedPosition, savePosition, useBoardStore } from "../../stores/boardStore";

const FALLBACK_ZOOM = 13;

function defaultCenter(): [number, number] {
  const [lng, lat] = __DEFAULT_CENTER__.split(",").map(Number);
  return [lng, lat];
}

function toFeatureCollection(items: EventPin[]): FeatureCollection {
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
      },
    })),
  };
}

export function BoardMap() {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const popupRef = useRef<maplibregl.Popup | null>(null);
  const draftMarkerRef = useRef<maplibregl.Marker | null>(null);
  const loadedRef = useRef(false);

  const { data: meta } = useMeta();
  const viewport = useBoardStore((s) => s.viewport);
  const filters = useBoardStore((s) => s.filters);
  const setViewport = useBoardStore((s) => s.setViewport);
  const crosshair = useBoardStore((s) => s.crosshair);
  const draftLocation = useBoardStore((s) => s.draftLocation);
  const setDraftLocation = useBoardStore((s) => s.setDraftLocation);
  const navigate = useNavigate();
  const selectedMatch = useMatch("/events/:id");

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
      loadedRef.current = true;
      map.addSource("events", {
        type: "geojson",
        data: { type: "FeatureCollection", features: [] },
      });
      map.addLayer({
        id: "event-pins",
        type: "circle",
        source: "events",
        paint: {
          "circle-radius": ["interpolate", ["linear"], ["zoom"], 10, 5, 16, 9],
          "circle-color": "#8A8A8A",
          "circle-stroke-width": 1.5,
          "circle-stroke-color": "#ffffff",
        },
      });
      map.on("mouseenter", "event-pins", () => {
        map.getCanvas().style.cursor = "pointer";
      });
      map.on("mouseleave", "event-pins", () => {
        map.getCanvas().style.cursor = "";
      });
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
    const apply = () => {
      const match: unknown[] = ["match", ["get", "type"]];
      for (const t of meta.types) match.push(t.key, t.color);
      match.push("#8A8A8A");
      map.setPaintProperty("event-pins", "circle-color", match);
    };
    if (loadedRef.current) apply();
    else map.once("load", apply);
  }, [meta]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !data) return;
    const apply = () => {
      (map.getSource("events") as GeoJSONSource | undefined)?.setData(
        toFeatureCollection(data.items),
      );
    };
    if (loadedRef.current) apply();
    else map.once("load", apply);
  }, [data]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !meta) return;

    const onPinClick = (event: MapMouseEvent) => {
      if (useBoardStore.getState().crosshair) return;
      const feature = map.queryRenderedFeatures(event.point, { layers: ["event-pins"] })[0];
      if (!feature) return;
      const props = feature.properties as { id: string; type: string; title: string; score: number };
      const label = meta.types.find((t) => t.key === props.type)?.label ?? props.type;
      const color = meta.types.find((t) => t.key === props.type)?.color ?? "#8A8A8A";

      popupRef.current?.remove();
      const container = document.createElement("div");
      const chip = document.createElement("span");
      chip.className = "type-chip";
      chip.style.background = color;
      chip.textContent = label;
      const title = document.createElement("p");
      title.style.margin = "6px 0";
      title.style.fontWeight = "bold";
      title.textContent = props.title;
      const metaRow = document.createElement("div");
      metaRow.className = "meta-row";
      metaRow.textContent = strings.board.points(props.score);
      const more = document.createElement("button");
      more.textContent = strings.board.readMore;
      more.addEventListener("click", () => {
        popupRef.current?.remove();
        navigate(`/events/${props.id}`);
      });
      container.append(chip, title, metaRow, more);

      popupRef.current = new maplibregl.Popup({ className: "note-popup", offset: 10 })
        .setLngLat((feature.geometry as Point).coordinates as [number, number])
        .setDOMContent(container)
        .addTo(map);
    };

    const onMapClick = (event: MapMouseEvent) => {
      if (!useBoardStore.getState().crosshair) return;
      setDraftLocation({ lng: event.lngLat.lng, lat: event.lngLat.lat });
    };

    map.on("click", "event-pins", onPinClick);
    map.on("click", onMapClick);
    return () => {
      map.off("click", "event-pins", onPinClick);
      map.off("click", onMapClick);
    };
  }, [meta, navigate, setDraftLocation]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    if (draftLocation && !draftMarkerRef.current) {
      const marker = new maplibregl.Marker({ draggable: true, color: "#B3352C" })
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
  const selectedId = selectedMatch?.params.id;

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !loadedRef.current) return;
    map.setPaintProperty("event-pins", "circle-stroke-color", [
      "case",
      ["==", ["get", "id"], selectedId ?? ""],
      "#2B2B2B",
      "#ffffff",
    ]);
  }, [selectedId, data]);

  return (
    <div className="map-wrap">
      <div ref={containerRef} className={`map${crosshair ? " crosshair" : ""}`} />
      <button
        type="button"
        style={{ position: "absolute", top: 12, left: 12, zIndex: 5 }}
        onClick={locateMe}
        title={strings.board.useMyLocation}
      >
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
