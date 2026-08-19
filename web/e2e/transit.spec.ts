import { expect, test } from "@playwright/test";
import { SHOTS, gotoBoard } from "./helpers";

type MapWindow = Window & {
  __corkboardMap: {
    jumpTo(o: { center: [number, number]; zoom: number }): void;
    once(event: string, cb: () => void): void;
    querySourceFeatures(source: string, o: { sourceLayer: string }): { properties: Record<string, string> }[];
    queryRenderedFeatures(o: { layers: string[] }): { properties: Record<string, string> }[];
  };
};

async function jumpTo(page: import("@playwright/test").Page, lng: number, lat: number, zoom: number) {
  await page.evaluate(
    ([lng, lat, zoom]) =>
      new Promise<void>((resolve) => {
        const map = (window as unknown as MapWindow).__corkboardMap;
        map.jumpTo({ center: [lng, lat], zoom });
        map.once("idle", resolve);
        setTimeout(resolve, 15_000);
      }),
    [lng, lat, zoom],
  );
  await page.waitForTimeout(1500);
}

test("stations stay on the basemap; every other POI is culled", async ({ page }) => {
  await gotoBoard(page);
  await jumpTo(page, -73.9857, 40.7484, 15);

  const carriesPois = await page.evaluate(
    () =>
      (window as unknown as MapWindow).__corkboardMap.querySourceFeatures("openmaptiles", {
        sourceLayer: "poi",
      }).length > 0,
  );
  test.skip(!carriesPois, "no basemap tiles here — nothing to assert about them");

  const drawn = await page.evaluate(() =>
    (window as unknown as MapWindow).__corkboardMap
      .queryRenderedFeatures({ layers: ["poi_transit"] })
      .map((f) => `${f.properties.class}/${f.properties.subclass}`),
  );

  expect(drawn.length, "midtown has stations to draw").toBeGreaterThan(0);
  expect(drawn.some((f) => f.endsWith("/subway")), "a metro sign is on the map").toBe(true);
  for (const f of drawn) {
    expect(f, "only rail stations survive the cull").toMatch(/^(railway|rail)\/(station|subway|halt|light_rail)$/);
  }
  await page.screenshot({ path: `${SHOTS}/transit.png` });
});

test("tram and bus stops stay off, in a city thick with them", async ({ page }) => {
  await gotoBoard(page);
  await jumpTo(page, 4.4777, 51.9244, 14.5);

  const counts = await page.evaluate(() => {
    const map = (window as unknown as MapWindow).__corkboardMap;
    const inTiles: Record<string, number> = {};
    for (const f of map.querySourceFeatures("openmaptiles", { sourceLayer: "poi" })) {
      const key = `${f.properties.class}/${f.properties.subclass}`;
      inTiles[key] = (inTiles[key] ?? 0) + 1;
    }
    return {
      inTiles,
      drawn: map.queryRenderedFeatures({ layers: ["poi_transit"] }).map((f) => f.properties.subclass),
    };
  });

  test.skip(Object.keys(counts.inTiles).length === 0, "no basemap tiles here — nothing to assert about them");
  expect(counts.inTiles["railway/tram_stop"] ?? 0, "Rotterdam's tram stops are in the tiles").toBeGreaterThan(0);
  expect(counts.inTiles["bus/bus_stop"] ?? 0, "so are its bus stops").toBeGreaterThan(0);
  expect(counts.drawn).not.toContain("tram_stop");
  expect(counts.drawn).not.toContain("bus_stop");
  expect(counts.drawn, "the metro and the train still show").toContain("subway");
});
