import { expect, test } from "@playwright/test";
import {
  SHOTS,
  createEventViaApi,
  gotoBoard,
  grantRole,
  myUserId,
  registerViaApi,
} from "./helpers";

async function reportOnce(browser: import("@playwright/test").Browser, eventId: string, reason: string) {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.goto("/");
  await registerViaApi(page, `Reporter ${Date.now()}${Math.random()}`);
  const res = await page.request.post(`/api/v1/events/${eventId}/report`, { data: { reason } });
  expect(res.status(), await res.text()).toBe(202);
  await ctx.close();
}

test("a moderator sees the worst-reported notes first and can take one down", async ({ browser }) => {
  const authorCtx = await browser.newContext();
  const authorPage = await authorCtx.newPage();
  await authorPage.goto("/");
  await registerViaApi(authorPage, "Reported Author");
  const quiet = await createEventViaApi(authorPage, { title: `Mildly disliked ${Date.now()}` });
  const loud = await createEventViaApi(authorPage, { title: `Widely reported ${Date.now()}` });

  await reportOnce(browser, quiet.id, "spam");
  for (const reason of ["spam", "spam", "scam"]) await reportOnce(browser, loud.id, reason);

  const modCtx = await browser.newContext();
  const modPage = await modCtx.newPage();
  await modPage.goto("/");
  await registerViaApi(modPage, "Board Keeper");
  await grantRole(await myUserId(modPage), "moderator");
  await gotoBoard(modPage);

  await modPage.getByRole("button", { name: "Your account" }).click();
  await modPage.getByRole("menuitem", { name: "Moderation" }).click();

  const rows = modPage.locator(".report-row");
  await expect(rows.first()).toContainText("Widely reported");
  await expect(rows.first()).toContainText("3");
  await expect(rows.first(), "the reasons are broken down").toContainText("It’s spam × 2");
  await modPage.screenshot({ path: `${SHOTS}/moderation.png` });

  const loudRow = modPage.locator(".report-row", { hasText: loud.title });
  await loudRow.getByRole("button", { name: "Take down" }).click();
  await expect(modPage.locator(".toaster")).toContainText("Taken off the board");
  await expect(loudRow, "a note taken down leaves the queue with it").toHaveCount(0);

  const gone = await authorPage.request.get(`/api/v1/events/${loud.id}`);
  expect(((await gone.json()) as { status: string }).status).toBe("taken_down");

  const quietRow = modPage.locator(".report-row", { hasText: quiet.title });
  await quietRow.getByRole("button", { name: "Approve" }).click();
  await expect(modPage.locator(".toaster")).toContainText("Reports cleared");
  await expect(quietRow, "and an approved note leaves it with its reports settled").toHaveCount(0);

  const kept = await authorPage.request.get(`/api/v1/events/${quiet.id}`);
  expect(((await kept.json()) as { status: string }).status).toBe("active");

  await authorCtx.close();
  await modCtx.close();
});

test("an ordinary resident is never offered moderation, and cannot reach it by URL", async ({ page }) => {
  await registerViaApi(page, "Ordinary Resident");
  await gotoBoard(page);

  await page.getByRole("button", { name: "Your account" }).click();
  await expect(page.getByRole("menuitem", { name: "Moderation" })).toHaveCount(0);
  await page.keyboard.press("Escape");

  await page.goto("/admin/reports");
  await expect(page.getByText("This is for board keepers only.")).toBeVisible();
  await expect(page.locator(".report-row"), "and no queue is fetched for them").toHaveCount(0);
});

test("a keeper takes a note down from the note itself, after confirming", async ({ browser }) => {
  const authorCtx = await browser.newContext();
  const authorPage = await authorCtx.newPage();
  await authorPage.goto("/");
  await registerViaApi(authorPage, "Drawer Author");
  const note = await createEventViaApi(authorPage, { title: `Taken from the drawer ${Date.now()}` });

  const modCtx = await browser.newContext();
  const modPage = await modCtx.newPage();
  await modPage.goto("/");
  await registerViaApi(modPage, "Drawer Keeper");
  await grantRole(await myUserId(modPage), "moderator");

  await modPage.goto(`/events/${note.id}`);
  await expect(modPage.locator(".modal-card .ev-title")).toHaveText(note.title);
  await expect(
    modPage.getByRole("button", { name: "Report this note" }),
    "a keeper takes notes down rather than reporting them to themselves",
  ).toHaveCount(0);

  await modPage.getByRole("button", { name: "Take down" }).click();
  await expect(modPage.getByText("Take this note off the board?")).toBeVisible();

  await modPage.getByRole("button", { name: "Leave it up" }).click();
  const stillUp = await authorPage.request.get(`/api/v1/events/${note.id}`);
  expect(((await stillUp.json()) as { status: string }).status).toBe("active");

  await modPage.getByRole("button", { name: "Take down" }).click();
  await modPage.getByRole("button", { name: "Yes, take it down" }).click();
  await expect(modPage.locator(".toaster")).toContainText("Taken off the board");

  const gone = await authorPage.request.get(`/api/v1/events/${note.id}`);
  expect(((await gone.json()) as { status: string }).status).toBe("taken_down");

  await authorPage.goto("/me/pins");
  await expect(authorPage.getByRole("heading", { name: "Taken down", exact: true })).toBeVisible();
  const keeperRow = authorPage.locator(".pin-row", { hasText: note.title });
  await expect(keeperRow).toBeVisible();
  await expect(
    keeperRow.getByRole("button", { name: "Take down" }),
    "the author cannot take down what a keeper already took down",
  ).toHaveCount(0);

  await authorPage.goto("/");
  await expect(authorPage.locator(".topbar .badge")).toHaveText("1", { timeout: 15_000 });
  await authorPage.getByRole("button", { name: "Notifications" }).click();
  await expect(authorPage.locator(".bell-item").first()).toContainText("taken off the board by a keeper");

  await authorCtx.close();
  await modCtx.close();
});

test("an ordinary resident still gets Report, and no take-down", async ({ page }) => {
  await registerViaApi(page, "Report-only Author");
  const note = await createEventViaApi(page, { title: `Reportable ${Date.now()}` });
  await page.request.post("/api/v1/auth/logout");

  await registerViaApi(page, "Report-only Reader");
  await page.goto(`/events/${note.id}`);

  await expect(page.getByRole("button", { name: "Report this note" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Take down" })).toHaveCount(0);
});
