import { expect, test } from "@playwright/test";
import { SHOTS, clickPin, gotoBoard } from "./helpers";

test("board renders chrome, server-driven filters, and pins", async ({ page }) => {
  await gotoBoard(page);

  await expect(page.getByLabel("lamppostal")).toBeVisible();
  await expect(page.getByText("Show on the board")).toBeVisible();
  for (const label of [
    "Lost & Found", "Sports & Activities", "Clubs & Hobbies", "Help Wanted / Offered",
    "Free Stuff", "Local Happenings", "Notices",
  ]) {
    await expect(page.getByText(label, { exact: true })).toBeVisible();
  }
  await expect(page.locator(".status-line")).toHaveText(/\d+ notes? on this stretch of the board/);
  await expect(page.getByText("Popular tags")).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/board.png` });
});

test("search narrows the board; pin click opens popup then drawer", async ({ page }) => {
  await gotoBoard(page);

  const res = await page.request.get(
    "/api/v1/events?bbox=-74.05,40.62,-73.85,40.85&zoom=13&q=Pirozhok",
  );
  const items = ((await res.json()) as { items: { id: string; location: { lng: number; lat: number } }[] }).items;
  expect(items.length, "seeded Pirozhok note must be on the board (reseed with make seed)").toBe(1);
  const pin = items[0];

  await page.evaluate(
    ([lng, lat]) => {
      (window as unknown as { __corkboardMap?: { jumpTo(o: { center: [number, number]; zoom: number }): void } })
        .__corkboardMap!.jumpTo({ center: [lng, lat], zoom: 14 });
    },
    [pin.location.lng, pin.location.lat],
  );
  await page.getByLabel("search notes…").fill("Pirozhok");
  await page.getByLabel("search notes…").press("Enter");
  await expect(page.locator(".status-line")).toHaveText(/1 note on this stretch/);

  const popup = page.locator(".paper-note");
  await expect(async () => {
    await clickPin(page, pin.location.lng, pin.location.lat);
    await expect(popup).toBeVisible({ timeout: 1500 });
  }).toPass({ timeout: 20_000 });
  await expect(popup.locator(".note-title")).toContainText("Pirozhok");
  await page.screenshot({ path: `${SHOTS}/popup.png` });

  await popup.getByText("read more").click();
  await expect(page.locator(".modal-card .ev-title")).toContainText("Pirozhok");
  await expect(page.locator(".modal-card .stamp")).toHaveText("Resolved");
  await expect(page.locator(".modal-card")).toContainText("Demo Resident");
  await expect(page).toHaveURL(/\/events\//);
  await page.screenshot({ path: `${SHOTS}/drawer.png` });
});

test("filters narrow by type and tag", async ({ page }) => {
  await gotoBoard(page);
  const before = await page.locator(".status-line").textContent();

  await page.getByText("Lost & Found", { exact: true }).click();
  await expect(page).toHaveURL(/types=/);
  await expect
    .poll(async () => page.locator(".status-line").textContent())
    .not.toBe(before);

  await page.getByRole("button", { name: "chess" }).click();
  await expect(page).toHaveURL(/tags=chess/);
});

test("mobile: filters take the whole board, modals take the whole screen", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 700 });
  await gotoBoard(page);

  await expect(page.locator(".sidebar")).toBeHidden();
  await page.getByRole("button", { name: "Filters" }).click();
  const sidebar = page.locator(".sidebar");
  await expect(sidebar).toBeVisible();
  const sheet = await sidebar.boundingBox();
  expect(sheet!.width).toBeGreaterThan(370);
  await page.screenshot({ path: `${SHOTS}/mobile-filters.png` });
  await page.getByRole("button", { name: "Show the board" }).click();
  await expect(sidebar).toBeHidden();

  await page.goto("/login");
  const card = page.locator(".modal-card");
  await expect(card).toBeVisible();
  const box = await card.boundingBox();
  expect(box!.width).toBeGreaterThan(370);
  expect(box!.height).toBeGreaterThan(600);
  await page.screenshot({ path: `${SHOTS}/mobile-login.png` });
});
