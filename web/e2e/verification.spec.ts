import { expect, test } from "@playwright/test";
import { PASSWORD, SHOTS, gotoBoard, uniqueEmail, verificationLink } from "./helpers";

test("a new account is read-only until the emailed link is opened", async ({ page }) => {
  await gotoBoard(page);

  const email = uniqueEmail("verify");
  await page.getByRole("link", { name: "Sign in" }).click();
  await page.getByRole("button", { name: "New here? Create an account" }).click();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Display name").fill("Unconfirmed Neighbour");
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Join the board" }).click();

  await expect(page.locator(".verify-banner")).toContainText(email);
  await page.screenshot({ path: `${SHOTS}/verify-banner.png` });

  await page.getByRole("button", { name: "Pin a note" }).click();
  await expect(page.getByRole("heading", { name: "One step left" })).toBeVisible();
  await page.keyboard.press("Escape");

  const link = await verificationLink(page, email);
  await page.goto(link);

  await expect(page.locator(".toaster")).toContainText("Email confirmed");
  await expect(page.locator(".toast-slip")).toHaveCount(1);
  await expect(page.locator(".verify-banner")).toHaveCount(0);

  await page.getByRole("button", { name: "Pin a note" }).click();
  await expect(page.getByText("Click the map where your note belongs.")).toBeVisible();
});
