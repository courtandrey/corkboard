import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";
import { SHOTS, gotoBoard, registerViaApi } from "./helpers";

async function badgeAndMenu(page: Page) {
  const badge = (await page.locator(".whoami").boundingBox())!;
  await page.getByRole("button", { name: "Your account" }).click();
  const panel = page.locator(".menu-panel");
  await panel.evaluate((el) => Promise.all(el.getAnimations().map((a) => a.finished)));
  return { badge, panel: (await panel.boundingBox())! };
}

test("the account badge keeps one width whatever the name, and its menu matches", async ({ browser }) => {
  const widths: number[] = [];

  for (const name of ["Ann", "Bartholomew Fitzgerald-Windsor III"]) {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto("/");
    await registerViaApi(page, name);
    await gotoBoard(page);

    await expect(page.locator(".whoami")).toContainText(name.slice(0, 3));
    const { badge, panel } = await badgeAndMenu(page);

    expect(panel.width, `the menu is as wide as the badge for "${name}"`).toBeCloseTo(badge.width, 0);
    expect(panel.x, "and lines up with it").toBeCloseTo(badge.x, 0);
    widths.push(badge.width);

    if (name.startsWith("Bart")) await page.screenshot({ path: `${SHOTS}/account-menu.png` });
    await context.close();
  }

  expect(widths[0], "a short name gets the same badge as a long one").toBe(widths[1]);
});

test("phone: the badge collapses but the menu stays readable and on screen", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 700 });
  await registerViaApi(page, "Bartholomew Fitzgerald-Windsor III");
  await gotoBoard(page);

  const { badge, panel } = await badgeAndMenu(page);
  expect(badge.width, "no room for a name here, so the badge is just the avatar").toBeLessThan(90);
  expect(panel.width, "the menu keeps the full width regardless").toBeGreaterThan(150);
  expect(panel.x).toBeGreaterThanOrEqual(0);
  expect(panel.x + panel.width, "and stays on screen").toBeLessThanOrEqual(375);
});
