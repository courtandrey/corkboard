import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { SHOTS, clickPin, createEventViaApi, gotoBoard, registerViaApi } from "./helpers";

async function jumpTo(page: Page, lng: number, lat: number, zoom: number) {
  await page.evaluate(
    ([x, y, z]) => {
      (window as unknown as { __corkboardMap?: { jumpTo(o: { center: [number, number]; zoom: number }): void } })
        .__corkboardMap!.jumpTo({ center: [x, y], zoom: z });
    },
    [lng, lat, zoom],
  );
}

async function renderedPinCount(page: Page): Promise<number> {
  return page.evaluate(() => {
    const map = (window as unknown as { __corkboardMap?: { queryRenderedFeatures(o: { layers: string[] }): unknown[] } })
      .__corkboardMap!;
    return map.queryRenderedFeatures({ layers: ["pins"] }).length;
  });
}

test("nearby notes render as one merged pin and split on click", async ({ page }) => {
  await page.goto("/");
  await registerViaApi(page, "Cluster Author");

  const cell = 45 / 2 ** 13;
  const baseLng = -38 + Math.random() * 8;
  const baseLat = 33 + Math.random() * 6;
  const centerLng = (Math.floor(baseLng / cell) + 0.5) * cell;
  const centerLat = (Math.floor(baseLat / cell) + 0.5) * cell;
  const coords = [-0.0009, 0, 0.0009].map((d) => [centerLng + d, centerLat + d * 0.8]);
  for (const [lng, lat] of coords) {
    await createEventViaApi(page, { lng, lat, title: `Merged member ${lng}` });
  }

  await gotoBoard(page);
  await jumpTo(page, centerLng, centerLat, 13);
  await expect(page.locator(".status-line")).toHaveText("3 notes on this stretch of the board");
  await expect.poll(() => renderedPinCount(page)).toBe(0);
  await page.screenshot({ path: `${SHOTS}/cluster-merged.png` });

  const clusterCoord = await page.evaluate(() => {
    const map = (window as unknown as {
      __corkboardMap: { queryRenderedFeatures(o: { layers: string[] }): { geometry: { coordinates: [number, number] } }[] };
    }).__corkboardMap;
    return map.queryRenderedFeatures({ layers: ["clusters"] })[0].geometry.coordinates;
  });
  const zoomBefore = await page.evaluate(
    () => (window as unknown as { __corkboardMap: { getZoom(): number } }).__corkboardMap.getZoom(),
  );
  await clickPin(page, clusterCoord[0], clusterCoord[1]);
  await expect
    .poll(async () =>
      page.evaluate(
        () => (window as unknown as { __corkboardMap: { getZoom(): number } }).__corkboardMap.getZoom(),
      ),
    )
    .toBeGreaterThan(zoomBefore + 1);
  await expect.poll(() => renderedPinCount(page), { timeout: 15_000 }).toBe(3);
  await page.screenshot({ path: `${SHOTS}/cluster-split.png` });
});

test("a fully merged pin lists its members for selection", async ({ page }) => {
  await page.goto("/");
  await registerViaApi(page, "Stack Author");
  const lng = -28 + Math.random() * 8;
  const lat = 33 + Math.random() * 6;
  const titles = ["Same-spot alpha", "Same-spot beta", "Same-spot gamma"];
  for (const title of titles) {
    await createEventViaApi(page, { lng, lat, title });
  }

  await gotoBoard(page);
  await jumpTo(page, lng, lat, 16);
  await expect(page.locator(".status-line")).toHaveText("3 notes on this stretch of the board");

  await expect(async () => {
    await clickPin(page, lng, lat);
    await expect(page.locator(".cluster-list")).toBeVisible({ timeout: 1500 });
  }).toPass({ timeout: 20_000 });
  await expect(page.locator(".cluster-list-row")).toHaveCount(3);
  await page.screenshot({ path: `${SHOTS}/cluster-list.png` });

  await page.locator(".cluster-list-row", { hasText: "Same-spot beta" }).click();
  await expect(page.locator(".drawer .note-title-large")).toHaveText("Same-spot beta");
});
