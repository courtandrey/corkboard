import { expect, test } from "@playwright/test";
import {
  SHOTS,
  createEventViaApi,
  gotoBoard,
  registerUnverifiedViaApi,
  registerViaApi,
} from "./helpers";

test("the account modal shows the address in use and renames the resident everywhere", async ({ page }) => {
  const email = await registerViaApi(page, "Original Name");
  const note = await createEventViaApi(page, { title: `Signed note ${Date.now()}` });
  await gotoBoard(page);

  await page.getByRole("button", { name: "Your account" }).click();
  await page.getByRole("menuitem", { name: "Account" }).click();

  const card = page.getByRole("dialog", { name: "Your account" });
  await expect(card, "the address they signed up with is shown").toContainText(email);
  await expect(card).toContainText("email confirmed");
  await expect(card.getByLabel("Display name")).toHaveValue("Original Name");

  await page.screenshot({ path: `${SHOTS}/account.png` });

  await card.getByLabel("Display name").fill("Renamed Resident");
  await card.getByRole("button", { name: "Save" }).click();

  await expect(page.locator(".toaster")).toContainText("Name updated");
  await expect(page.locator(".whoami")).toContainText("Renamed Resident");

  // the name travels with everything they have written
  await page.goto(`/events/${note.id}`);
  await expect(page.locator(".modal-card .ev-meta")).toContainText("Renamed Resident");

  await page.reload();
  await expect(page.locator(".whoami"), "and it outlives the session cache").toContainText("Renamed Resident");
});

test("the account modal refuses an empty name and can be dismissed unchanged", async ({ page }) => {
  await registerViaApi(page, "Unchanged Resident");
  await gotoBoard(page);
  await page.goto("/me/account");

  const card = page.getByRole("dialog", { name: "Your account" });
  await card.getByLabel("Display name").fill("");
  await card.getByRole("button", { name: "Save" }).click();
  await expect(card, "an empty name never leaves the browser").toBeVisible();

  await card.getByLabel("Display name").fill("Unchanged Resident");
  await card.getByRole("button", { name: "Never mind" }).click();
  await expect(card).toHaveCount(0);
  await expect(page.locator(".whoami")).toContainText("Unchanged Resident");
});

test("the account modal never grows an inner scrollbar, however short the window", async ({ page }) => {
  await registerViaApi(page, "Sudarkin Andrey");

  for (const height of [900, 500, 260]) {
    await page.setViewportSize({ width: 1280, height });
    await page.goto("/me/account");
    const shell = page.locator(".modal-account");
    await shell.evaluate((el) => Promise.all(el.getAnimations({ subtree: true }).map((a) => a.finished)));

    const seen = await page.evaluate(() => {
      const scrim = document.querySelector(".modal-scrim") as HTMLElement;
      const body = document.querySelector(".modal-body") as HTMLElement;
      scrim.scrollTop = 0;
      const card = document.querySelector(".modal-card") as HTMLElement;
      return {
        bodyScrolls: body.scrollHeight > body.clientHeight,
        // the card hides its overflow, so a card too small for its content silently crops it
        cardCrops: card.scrollHeight > card.clientHeight,
        cardTop: card.getBoundingClientRect().top,
      };
    });

    expect(seen.bodyScrolls, `no inner scroll pane at ${height}px`).toBe(false);
    expect(seen.cardCrops, `and nothing is cropped instead at ${height}px`).toBe(false);
    expect(seen.cardTop, `the whole card is reachable at ${height}px`).toBeGreaterThanOrEqual(0);
  }
});

test("the identity line stays on one line, even at its longest", async ({ page }) => {
  await registerUnverifiedViaApi(page, "Sudarkin Andrey");
  await page.goto("/me/account");
  await page.locator(".modal-card").waitFor();

  const seen = await page.evaluate(() => {
    const meta = document.querySelector(".account-identity-text .form-hint") as HTMLElement;
    const lines = () => {
      const wrapped = meta.getBoundingClientRect().height;
      meta.style.whiteSpace = "nowrap";
      const single = meta.getBoundingClientRect().height;
      meta.style.whiteSpace = "";
      return Math.round(wrapped / single);
    };
    const asRendered = lines();
    meta.textContent = "email not confirmed yet · on the board since 12/24/2026";
    return { asRendered, atLongest: lines() };
  });

  expect(seen.asRendered, "the line as it actually reads").toBe(1);
  expect(seen.atLongest, "and at the longest copy it can carry").toBe(1);
});

test("phone: the card is still a full-bleed sheet, not the wider desktop card", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 700 });
  await registerViaApi(page, "Sudarkin Andrey");
  await page.goto("/me/account");

  const shell = (await page.locator(".modal-shell").boundingBox())!;
  expect(shell.width).toBeCloseTo(375, 0);
});
