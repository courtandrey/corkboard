import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { SHOTS, gotoBoard, settled, stubGeocoder } from "./helpers";

type MapWindow = Window & {
  __corkboardMap: {
    jumpTo(o: { center: [number, number]; zoom: number }): void;
    once(event: string, cb: () => void): void;
    querySourceFeatures(source: string, o: { sourceLayer: string }): { properties: Record<string, string> }[];
    queryRenderedFeatures(o: { layers: string[] }): { properties: Record<string, string> }[];
    getStyle(): { layers: { id: string; "source-layer"?: string }[] };
    getLayer(id: string): unknown;
    getZoom(): number;
  };
};

const NO_TILES = "no basemap tiles here — nothing to assert about them";

async function jumpTo(page: Page, lng: number, lat: number, zoom: number) {
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
}

async function carriesTiles(page: Page, sourceLayer: string): Promise<boolean> {
  const arrived = await page
    .waitForFunction(
      (layer) =>
        (window as unknown as MapWindow).__corkboardMap.querySourceFeatures("openmaptiles", { sourceLayer: layer })
          .length > 0,
      sourceLayer,
      { timeout: 20_000 },
    )
    .catch(() => null);
  return arrived !== null;
}

function stationsDrawn(page: Page): Promise<string[]> {
  return page.evaluate(() =>
    (window as unknown as MapWindow).__corkboardMap
      .queryRenderedFeatures({ layers: ["poi_transit"] })
      .map((f) => `${f.properties.class}/${f.properties.subclass}`),
  );
}

function streetDetail(page: Page): Promise<{ housenumbers: number; streets: number }> {
  return page.evaluate(() => {
    const map = (window as unknown as MapWindow).__corkboardMap;
    const names = map
      .getStyle()
      .layers.filter((l) => l["source-layer"] === "transportation_name" && map.getLayer(l.id))
      .flatMap((l) => map.queryRenderedFeatures({ layers: [l.id] }))
      .map((f) => f.properties.name)
      .filter(Boolean);
    return {
      housenumbers: map.getLayer("housenumbers")
        ? map.queryRenderedFeatures({ layers: ["housenumbers"] }).length
        : 0,
      streets: new Set(names).size,
    };
  });
}

test("stations stay on the basemap; every other POI is culled", async ({ page }) => {
  await gotoBoard(page);
  await jumpTo(page, -73.9857, 40.7484, 15);
  test.skip(!(await carriesTiles(page, "poi")), NO_TILES);

  await expect
    .poll(async () => (await stationsDrawn(page)).length, { timeout: 20_000, message: "midtown has stations to draw" })
    .toBeGreaterThan(0);

  const drawn = await stationsDrawn(page);
  expect(drawn.some((f) => f.endsWith("/subway")), "a metro sign is on the map").toBe(true);
  for (const f of drawn) {
    expect(f, "only rail stations survive the cull").toMatch(/^(railway|rail)\/(station|subway|halt|light_rail)$/);
  }
  await page.screenshot({ path: `${SHOTS}/transit.png` });
});

test("tram and bus stops stay off, in a city thick with them", async ({ page }) => {
  await gotoBoard(page);
  await jumpTo(page, 4.4777, 51.9244, 14.5);
  test.skip(!(await carriesTiles(page, "poi")), NO_TILES);

  await expect
    .poll(async () => (await stationsDrawn(page)).length, { timeout: 20_000, message: "Rotterdam has stations to draw" })
    .toBeGreaterThan(0);

  const inTiles = await page.evaluate(() => {
    const counts: Record<string, number> = {};
    for (const f of (window as unknown as MapWindow).__corkboardMap.querySourceFeatures("openmaptiles", {
      sourceLayer: "poi",
    })) {
      const key = `${f.properties.class}/${f.properties.subclass}`;
      counts[key] = (counts[key] ?? 0) + 1;
    }
    return counts;
  });
  const drawn = (await stationsDrawn(page)).map((f) => f.split("/")[1]);

  expect(inTiles["railway/tram_stop"] ?? 0, "Rotterdam's tram stops are in the tiles").toBeGreaterThan(0);
  expect(inTiles["bus/bus_stop"] ?? 0, "so are its bus stops").toBeGreaterThan(0);
  expect(drawn).not.toContain("tram_stop");
  expect(drawn).not.toContain("bus_stop");
  expect(drawn, "the metro and the train still show").toContain("subway");
});

test("the zoom the address search lands on is the zoom that names streets and numbers doors", async ({ page }) => {
  await stubGeocoder(page);
  await gotoBoard(page);

  await page.getByLabel("jump to an address…").fill("herald sq");
  await expect(page.locator(".address-match").first()).toBeVisible();
  await page.getByLabel("jump to an address…").press("Enter");
  await settled(page);
  test.skip(!(await carriesTiles(page, "housenumber")), NO_TILES);

  await expect
    .poll(async () => (await streetDetail(page)).housenumbers, {
      timeout: 20_000,
      message: "house numbers are readable where the search drops you",
    })
    .toBeGreaterThan(0);
  await expect
    .poll(async () => (await streetDetail(page)).streets, { timeout: 20_000, message: "and so are street names" })
    .toBeGreaterThan(0);

  await page.screenshot({ path: `${SHOTS}/street-detail.png` });
});
