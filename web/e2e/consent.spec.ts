import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";
import { SHOTS } from "./helpers";

const KEY = "corkboard.cookie-consent";

const stored = (page: Page) => page.evaluate((k) => localStorage.getItem(k), KEY);

test("a first visit is asked, and the answer is remembered", async ({ page }) => {
  await page.goto("/");
  const banner = page.locator(".cookie-banner");
  await expect(banner).toBeVisible();
  expect(await stored(page), "nothing is assumed before they answer").toBeNull();
  await page.screenshot({ path: `${SHOTS}/cookie-banner.png` });

  await banner.getByRole("button", { name: "Accept all" }).click();
  await expect(banner).toHaveCount(0);
  expect(await stored(page)).toBe("all");

  await page.reload();
  await expect(page.locator(".cookie-banner"), "a returning visitor is not asked again").toHaveCount(0);
});

test("essential only is a real answer, not a deferral", async ({ page }) => {
  await page.goto("/");
  await page.locator(".cookie-banner").getByRole("button", { name: "Only essential" }).click();
  expect(await stored(page)).toBe("essential");

  await page.reload();
  await expect(page.locator(".cookie-banner")).toHaveCount(0);
});

test("the banner never sits on top of the board's own controls", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator(".cookie-banner")).toBeVisible();
  await expect(page.locator(".status-line")).toBeVisible();

  const banner = (await page.locator(".cookie-banner").boundingBox())!;
  for (const control of [".status-line", ".locate-btn"]) {
    const box = (await page.locator(control).boundingBox())!;
    const overlaps =
      box.x < banner.x + banner.width &&
      box.x + box.width > banner.x &&
      box.y < banner.y + banner.height &&
      box.y + box.height > banner.y;
    expect(overlaps, `${control} is left clear`).toBe(false);
  }
});

test("phone: answering does not leave the board unusable", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 700 });
  await page.goto("/");
  const banner = page.locator(".cookie-banner");
  await expect(banner).toBeVisible();

  const box = (await banner.boundingBox())!;
  expect(box.x).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(375);

  const fab = (await page.locator(".pin-fab").boundingBox())!;
  expect(
    box.y + box.height > fab.y && box.y < fab.y + fab.height && box.x < fab.x + fab.width && box.x + box.width > fab.x,
    "the pin button is left clear",
  ).toBe(false);

  await banner.getByRole("button", { name: "Only essential" }).click();
  await expect(banner).toHaveCount(0);
  await expect(page.locator(".pin-fab"), "the board is fully usable again").toBeVisible();
});

test("the banner steps aside while a modal is open, and comes back after", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator(".cookie-banner")).toBeVisible();

  await page.goto("/login");
  await expect(page.locator(".modal-card")).toBeVisible();
  await expect(page.locator(".cookie-banner")).toBeHidden();

  await page.getByRole("button", { name: "Close" }).click();
  await expect(page.locator(".cookie-banner"), "still unanswered, so still asked").toBeVisible();
});
