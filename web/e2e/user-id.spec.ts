import { expect, test } from "@playwright/test";
import { PASSWORD, SHOTS, createEventViaApi, gotoBoard, registerViaApi, uniqueEmail, uniqueHandle } from "./helpers";

test("a user id is taken at sign-up, shown beside the name, and cannot be edited", async ({ page }) => {
  await gotoBoard(page);
  const handle = uniqueHandle();

  await page.getByRole("link", { name: "Sign in" }).click();
  await page.getByRole("button", { name: "New here? Create an account" }).click();
  await page.getByLabel("Email").fill(uniqueEmail("handle"));
  await page.getByLabel("Display name").fill("Known Resident");
  await page.getByLabel("User ID").fill(handle.toUpperCase());
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Join the board" }).click();
  await expect(page.locator(".whoami")).toContainText("Known Resident");

  await page.goto("/me/account");
  const field = page.getByLabel("User ID");
  await expect(field, "the id is shown as typed, in one case").toHaveValue(`@${handle}`);
  await expect(field, "and is not up for changing").toBeDisabled();
  await page.screenshot({ path: `${SHOTS}/user-id.png` });
});

test("the id someone else picked is refused, saying which half collided", async ({ page }) => {
  await gotoBoard(page);
  const taken = uniqueHandle();
  const first = await page.request.post("/api/v1/auth/register", {
    data: { email: uniqueEmail("first"), password: PASSWORD, displayName: "First Claim", handle: taken },
  });
  expect(first.status()).toBe(201);

  const clash = await page.request.post("/api/v1/auth/register", {
    data: { email: uniqueEmail("second"), password: PASSWORD, displayName: "Second Claim", handle: taken },
  });
  expect(clash.status()).toBe(409);
  expect(((await clash.json()) as { code: string }).code).toBe("handle_taken");
});

test("a note carries its author's id, so two Alexes are still two people", async ({ page }) => {
  await registerViaApi(page, "Alex");
  const me = (await (await page.request.get("/api/v1/auth/me")).json()) as { user: { handle: string } };
  const note = await createEventViaApi(page, { title: `Signed by an id ${Date.now()}` });

  await page.goto(`/events/${note.id}`);
  await expect(page.locator(".modal-card .ev-meta .user-handle")).toHaveText(`@${me.user.handle}`);
});
