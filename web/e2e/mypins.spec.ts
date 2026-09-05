import { expect, test } from "@playwright/test";
import { SHOTS, createEventViaApi, gotoBoard, registerViaApi } from "./helpers";

test("my pins: resolve stamps the note, delete takes it down", async ({ page }) => {
  await page.goto("/");
  await registerViaApi(page, "Pin Manager");
  const keeper = await createEventViaApi(page, { title: `Keeper note ${Date.now()}` });
  const goner = await createEventViaApi(page, { title: `Goner note ${Date.now()}` });

  await page.goto("/me/pins");
  await expect(page.getByRole("heading", { name: "On the board" })).toBeVisible();
  await expect(page.getByRole("link", { name: keeper.title })).toBeVisible();
  await expect(page.getByRole("link", { name: goner.title })).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/mypins.png` });

  const keeperRow = page.locator(".pin-row", { hasText: keeper.title });
  await keeperRow.getByRole("button", { name: "Mark resolved" }).click();
  await expect(page.getByRole("heading", { name: "Resolved" })).toBeVisible();

  await page.getByRole("link", { name: keeper.title }).click();
  await expect(page.locator(".modal-card .stamp")).toHaveText("Resolved");
  await page.goBack();

  const gonerRow = page.locator(".pin-row", { hasText: goner.title });
  await gonerRow.getByRole("button", { name: "Take down" }).click();
  await expect(gonerRow.locator(".confirm-slip")).toBeVisible();
  await gonerRow.getByRole("button", { name: "Yes, take it down" }).click();
  await expect(page.getByRole("heading", { name: "Withdrawn" })).toBeVisible();

  const anonymous = await page.context().browser()!.newContext();
  const anonymousPage = await anonymous.newPage();
  const res = await anonymousPage.request.get(`/api/v1/events/${goner.id}`);
  expect(res.status()).toBe(404);
  await anonymous.close();
});

test("a note can be pinned with no end date, and the date field appears only when it has one", async ({ page }) => {
  await registerViaApi(page, "Endless Pinner");
  await gotoBoard(page);

  await page.getByRole("button", { name: "Pin a note" }).click();
  await expect(page.getByText("Click the map where your note belongs.")).toBeVisible();
  const map = (await page.locator(".map-wrap .map").boundingBox())!;
  await page.mouse.click(map.x + map.width / 2, map.y + map.height / 2);
  await expect(page.getByText("Drag the pin to adjust")).toBeVisible();
  await page.getByRole("button", { name: "Write the note" }).click();

  const dateField = page.locator('input[name="expiresAt"]');
  await expect(dateField, "by default a note still gets an end date").toBeVisible();
  expect(
    await dateField.getAttribute("max"),
    "and that date is no longer capped",
  ).toBeNull();

  const title = `Endless note ${Date.now()}`;
  await page.getByLabel("Title").fill(title);
  await page.getByLabel("The note itself").fill("This one stays until I take it down.");
  await page.getByLabel("Keep it up with no end date").check();
  await expect(dateField, "the date is meaningless once there is no end").toHaveCount(0);
  await page.getByRole("button", { name: "Pin it" }).click();

  await expect(page.locator(".modal-card .ev-title")).toHaveText(title);
  await expect(page.locator(".modal-card .ev-meta")).toContainText("no end date");

  await page.goto("/me/pins");
  const row = page.locator(".pin-row", { hasText: title });
  await expect(row).toContainText("no end date");
  await expect(row.getByRole("button", { name: "Renew +30 days" }), "nothing to renew").toHaveCount(0);
});

test("an existing note can have its end date taken off, and put back", async ({ page }) => {
  await registerViaApi(page, "Date Remover");
  const note = await createEventViaApi(page, { title: `Dated note ${Date.now()}` });
  await page.goto(`/events/${note.id}`);
  await expect(page.locator(".modal-card .ev-meta")).toContainText("until");

  await page.getByRole("button", { name: "Edit note" }).click();
  await page.getByLabel("Keep it up with no end date").check();
  await page.getByRole("button", { name: "Save changes" }).click();
  await expect(page.locator(".modal-card .ev-meta")).toContainText("no end date");

  await page.getByRole("button", { name: "Edit note" }).click();
  await page.getByLabel("Keep it up with no end date").uncheck();
  await expect(page.locator('input[name="expiresAt"]')).toBeVisible();
  await page.getByRole("button", { name: "Save changes" }).click();
  await expect(page.locator(".modal-card .ev-meta")).toContainText("until");
});
