import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { SHOTS, gotoBoard } from "./helpers";

const HERALD = { lng: -73.9877, lat: 40.7505 };

async function stubGeocoder(page: Page): Promise<string[]> {
  const asked: string[] = [];
  await page.route("**/api/v1/places?*", async (route) => {
    asked.push(route.request().url());
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        items: [
          {
            id: "N1",
            name: "Herald Square",
            context: "Manhattan, New York",
            location: HERALD,
            bounds: null,
          },
          {
            id: "R2",
            name: "Herald Square Park",
            context: "Midtown, New York",
            location: { lng: -73.988, lat: 40.751 },
            bounds: null,
          },
        ],
      }),
    });
  });
  return asked;
}

function centre(page: Page) {
  return page.evaluate(() => {
    const map = (window as unknown as { __corkboardMap: { getCenter(): { lng: number; lat: number }; getZoom(): number } })
      .__corkboardMap;
    const c = map.getCenter();
    return { lng: +c.lng.toFixed(3), lat: +c.lat.toFixed(3), zoom: Math.round(map.getZoom()) };
  });
}

test("typing an address offers matches, and picking one moves the board", async ({ page }) => {
  const asked = await stubGeocoder(page);
  await gotoBoard(page);
  await page.evaluate(() =>
    (window as unknown as { __corkboardMap: { jumpTo(o: { center: [number, number]; zoom: number }): void } })
      .__corkboardMap.jumpTo({ center: [-73.95, 40.68], zoom: 12 }),
  );

  const field = page.getByLabel("jump to an address…");
  await field.fill("he");
  await expect(page.locator(".address-match"), "two letters are not a search").toHaveCount(0);

  await field.fill("herald sq");
  await expect(page.locator(".address-match")).toHaveCount(2);
  await expect(page.locator(".address-match").first()).toContainText("Herald Square");
  await expect(page.locator(".address-match").first()).toContainText("Manhattan, New York");
  await page.screenshot({ path: `${SHOTS}/address-search.png` });

  expect(asked.length, "typing is debounced into one lookup, not one per keystroke").toBeLessThan(4);
  expect(asked.at(-1), "the map's centre goes along, so matches are local first").toContain("near=");

  await field.press("Enter");
  await page.evaluate(
    () =>
      new Promise<void>((resolve) => {
        const map = (window as unknown as { __corkboardMap: { once(e: string, cb: () => void): void } }).__corkboardMap;
        map.once("moveend", () => resolve());
        setTimeout(resolve, 10_000);
      }),
  );

  const where = await centre(page);
  expect(where.lng).toBeCloseTo(HERALD.lng, 2);
  expect(where.lat).toBeCloseTo(HERALD.lat, 2);
  expect(where.zoom, "a pinpoint gets a street-level zoom").toBeGreaterThanOrEqual(15);
  await expect(page.locator(".address-match")).toHaveCount(0);
  await expect(field).toHaveValue("Herald Square");
});

test("the box gets out of the way: escape closes it, the cross empties it", async ({ page }) => {
  await stubGeocoder(page);
  await gotoBoard(page);

  const field = page.getByLabel("jump to an address…");
  await field.fill("herald sq");
  await expect(page.locator(".address-match")).toHaveCount(2);
  await expect(page.locator(".address-match.active"), "the top match is armed for Enter").toContainText(
    "Manhattan",
  );

  await field.press("ArrowDown");
  await expect(page.locator(".address-match.active")).toContainText("Midtown");

  await field.press("Escape");
  await expect(page.locator(".address-match")).toHaveCount(0);
  await expect(field).toHaveValue("herald sq");

  await field.press("Escape");
  await expect(field).toHaveValue("");

  await field.fill("herald sq");
  await expect(page.locator(".address-match")).toHaveCount(2);
  await page.getByLabel("Clear the address").click();
  await expect(field).toHaveValue("");
  await expect(page.locator(".address-match")).toHaveCount(0);
});
