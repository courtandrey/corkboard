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

async function stubGeolocation(page: Page, failures: number, code: number) {
  await page.addInitScript(
    ({ failures, code, target }) => {
      let seen = 0;
      const box = window as unknown as { __locateCalls: number; __locateReset: () => void };
      box.__locateCalls = 0;
      box.__locateReset = () => {
        seen = 0;
        box.__locateCalls = 0;
      };
      Object.defineProperty(navigator, "geolocation", {
        configurable: true,
        value: {
          getCurrentPosition(ok: PositionCallback, no?: PositionErrorCallback) {
            seen++;
            box.__locateCalls = seen;
            if (seen <= failures) {
              no?.({ code, message: "stub" } as GeolocationPositionError);
              return;
            }
            ok({ coords: { latitude: target.latitude, longitude: target.longitude } } as GeolocationPosition);
          },
          watchPosition: () => 0,
          clearWatch: () => undefined,
        },
      });
    },
    { failures, code, target: TARGET },
  );
}

const calls = (page: Page) =>
  page.evaluate(() => (window as unknown as { __locateCalls?: number }).__locateCalls ?? 0);

const resetCalls = (page: Page) =>
  page.evaluate(() => (window as unknown as { __locateReset?: () => void }).__locateReset?.());

test("a provider that misses the first window is retried instead of reported", async ({ page }) => {
  await stubGeolocation(page, 1, 3);
  await gotoBoard(page);
  await away(page);
  await resetCalls(page);

  const locate = page.getByRole("button", { name: "Use my location" });
  await locate.click();

  await expect(locate).toHaveAttribute("aria-busy", "true");
  await expect(page.locator(".toast-slip")).toHaveCount(0);

  await expect.poll(async () => (await state(page)).moving, { timeout: 15_000 }).toBe(true);
  await expect.poll(async () => (await state(page)).moving, { timeout: 15_000 }).toBe(false);

  const { center } = await state(page);
  expect(Math.abs(center.lat - TARGET.latitude)).toBeLessThan(0.01);
  expect(Math.abs(center.lng - TARGET.longitude)).toBeLessThan(0.01);
  expect(await calls(page), "the first miss was asked again").toBe(2);
  await expect(page.locator(".toast-slip"), "a retried miss is never reported").toHaveCount(0);
  await expect(locate).toHaveAttribute("aria-busy", "false");
});

test("a refusal is final and is reported without waiting for a retry", async ({ page }) => {
  await stubGeolocation(page, 99, 1);
  await gotoBoard(page);
  await away(page);
  await resetCalls(page);

  await page.getByRole("button", { name: "Use my location" }).click();

  await expect(page.locator(".toast-slip")).toContainText("Couldn’t get your location", {
    timeout: 3_000,
  });
  expect(await calls(page), "a denial is not asked twice").toBe(1);
});
