import { expect, test } from "@playwright/test";
import { SHOTS, createEventViaApi, registerViaApi } from "./helpers";

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
  await expect(page.getByRole("heading", { name: "Taken down" })).toBeVisible();

  const anonymous = await page.context().browser()!.newContext();
  const anonymousPage = await anonymous.newPage();
  const res = await anonymousPage.request.get(`/api/v1/events/${goner.id}`);
  expect(res.status()).toBe(404);
  await anonymous.close();
});
