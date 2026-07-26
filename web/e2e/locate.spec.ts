import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { gotoBoard } from "./helpers";

const TARGET = { latitude: 52.3702, longitude: 4.8952 };

declare global {
  interface Window {
    __corkboardMap: {
      isMoving(): boolean;
      getCenter(): { lng: number; lat: number };
      jumpTo(options: { center: [number, number]; zoom: number }): void;
    };
  }
}

const state = (page: Page) =>
  page.evaluate(() => ({
    moving: window.__corkboardMap.isMoving(),
    center: window.__corkboardMap.getCenter(),
  }));

const away = (page: Page) =>
  page.evaluate(() => window.__corkboardMap.jumpTo({ center: [-73.98, 40.73], zoom: 13 }));

test.beforeEach(async ({ context }) => {
  await context.grantPermissions(["geolocation"]);
  await context.setGeolocation(TARGET);
});

test("use my location flies there and settles", async ({ page }) => {
  await gotoBoard(page);
  await away(page);

  await page.getByRole("button", { name: "Use my location" }).click();

  await expect
    .poll(async () => (await state(page)).moving, { timeout: 5_000 })
    .toBe(true);
  await expect
    .poll(async () => (await state(page)).moving, { timeout: 15_000 })
    .toBe(false);

  const { center } = await state(page);
  expect(Math.abs(center.lat - TARGET.latitude)).toBeLessThan(0.01);
  expect(Math.abs(center.lng - TARGET.longitude)).toBeLessThan(0.01);
});

test("a second press during the flight teleports instead of waiting", async ({ page }) => {
  await gotoBoard(page);
  await away(page);

  const locate = page.getByRole("button", { name: "Use my location" });
  await locate.click();
  await expect.poll(async () => (await state(page)).moving, { timeout: 5_000 }).toBe(true);

  await locate.click();
  await expect.poll(async () => (await state(page)).moving, { timeout: 2_000 }).toBe(false);

  const { center } = await state(page);
  expect(Math.abs(center.lat - TARGET.latitude)).toBeLessThan(0.01);
  expect(Math.abs(center.lng - TARGET.longitude)).toBeLessThan(0.01);
});
