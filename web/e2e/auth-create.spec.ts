import { expect, test } from "@playwright/test";
import { PASSWORD, SHOTS, gotoBoard, uniqueEmail } from "./helpers";

test("register via UI, pin a note through the crosshair flow, sign out", async ({ page }) => {
  await gotoBoard(page);

  await page.getByRole("link", { name: "Sign in" }).click();
  await page.getByRole("button", { name: "New here? Create an account" }).click();
  await page.getByLabel("Email").fill(uniqueEmail("ui"));
  await page.getByLabel("Display name").fill("E2E Creator");
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Join the board" }).click();
  await expect(page.locator(".whoami")).toContainText("E2E Creator");

  await page.getByRole("button", { name: "Pin a note" }).click();
  await expect(page.getByText("Click the map where your note belongs.")).toBeVisible();

  const box = await page.locator(".map-wrap .map").boundingBox();
  await page.mouse.click(box!.x + box!.width / 2, box!.y + box!.height / 2);
  await expect(page.getByText("Drag the pin to adjust, then continue.")).toBeVisible();
  await page.getByRole("button", { name: "Looks right — write the note" }).click();

  const title = `Crosshair-made note ${Date.now()}`;
  await page.locator(".drawer .type-pick").getByText("Free Stuff").click();
  await page.getByLabel("Title").fill(title);
  await page.getByLabel("The note itself").fill("Placed with the crosshair by the regression suite.");
  await page.getByPlaceholder("type to find or add tags…").fill("e2e-made");
  await page.getByPlaceholder("type to find or add tags…").press("Enter");
  await page.screenshot({ path: `${SHOTS}/create-form.png` });
  await page.getByRole("button", { name: "Pin it" }).click();

  await expect(page.locator(".drawer .note-title-large")).toHaveText(title);
  await expect(page).toHaveURL(/\/events\//);
  await expect(page.locator(".drawer .tag-chip")).toContainText("e2e-made");

  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByRole("link", { name: "Sign in" })).toBeVisible();
});
