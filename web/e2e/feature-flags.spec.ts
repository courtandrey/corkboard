import { expect, test } from "@playwright/test";
import { SHOTS, gotoBoard, grantRole, myUserId, registerViaApi, setFeatureFlag } from "./helpers";

const FLAG = "ARE_USER_DETAILS_EDITABLE";

test.afterEach(async () => {
  await setFeatureFlag(FLAG, true);
});

test("an admin switches renaming off, and every open board locks at once", async ({ browser }) => {
  const residentCtx = await browser.newContext();
  const residentPage = await residentCtx.newPage();
  await residentPage.goto("/");
  await registerViaApi(residentPage, "Live Resident");
  await residentPage.goto("/me/account");

  const account = residentPage.getByRole("dialog", { name: "Your account" });
  await expect(account.getByRole("button", { name: "Save" })).toBeVisible();

  const adminCtx = await browser.newContext();
  const adminPage = await adminCtx.newPage();
  await adminPage.goto("/");
  await registerViaApi(adminPage, "Flag Keeper");
  await grantRole(await myUserId(adminPage), "admin");
  await gotoBoard(adminPage);

  await adminPage.getByRole("button", { name: "Your account" }).click();
  await adminPage.getByRole("menuitem", { name: "Feature toggles" }).click();

  const toggle = adminPage.getByRole("switch", { name: "Turn Editable account details on or off" });
  await expect(toggle).toHaveAttribute("aria-checked", "true");
  await adminPage.screenshot({ path: `${SHOTS}/feature-flags.png` });

  await toggle.click();
  await expect(adminPage.locator(".toaster")).toContainText("Editable account details is off");
  await expect(toggle).toHaveAttribute("aria-checked", "false");

  await expect(
    account.getByRole("button", { name: "Save" }),
    "the resident's open form locks without a reload",
  ).toHaveCount(0);
  await expect(account.getByLabel("Display name"), "the name stays readable, just not editable").toBeVisible();
  await expect(account.getByLabel("Display name")).toBeDisabled();

  const refused = await residentPage.request.patch("/api/v1/auth/me", {
    data: { displayName: "Sneaky Rename" },
  });
  expect(refused.status(), await refused.text()).toBe(403);
  expect(((await refused.json()) as { code: string }).code).toBe("feature_disabled");

  await toggle.click();
  await expect(adminPage.locator(".toaster")).toContainText("Editable account details is on");
  await expect(account.getByRole("button", { name: "Save" })).toBeVisible();

  await residentCtx.close();
  await adminCtx.close();
});

test("an ordinary resident is never offered the toggles, and cannot reach them by URL", async ({ page }) => {
  await registerViaApi(page, "Toggle-free Resident");
  await gotoBoard(page);

  await page.getByRole("button", { name: "Your account" }).click();
  await expect(page.getByRole("menuitem", { name: "Feature toggles" })).toHaveCount(0);
  await page.keyboard.press("Escape");

  await page.goto("/admin/features");
  await expect(page.getByText("This is for board keepers only.")).toBeVisible();
  await expect(page.locator(".flag-row"), "and no toggles are fetched for them").toHaveCount(0);
});
