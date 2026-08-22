import { expect, test } from "@playwright/test";
import { SHOTS, gotoBoard, myUserId, registerViaApi, setFeatureFlag } from "./helpers";

const FLAG = "IS_PERSONAL_SCOPE_ENABLED";

test.afterEach(async () => {
  await setFeatureFlag(FLAG, true);
});

async function pinHere(page: import("@playwright/test").Page, title: string, type: string) {
  await page.getByRole("button", { name: "Pin a note" }).click();
  await expect(page.getByText("Click the map where your note belongs.")).toBeVisible();
  const box = await page.locator(".map-wrap .map").boundingBox();
  await page.mouse.click(box!.x + box!.width / 2, box!.y + box!.height / 2);
  await expect(page.getByText("Drag the pin to adjust, then continue.")).toBeVisible();
  await page.getByRole("button", { name: "Looks right — write the note" }).click();
  await page.locator(".modal-card .type-pick").getByText(type).click();
  await page.getByLabel("Title").fill(title);
  await page.getByLabel("The note itself").fill("Written on my own board by the regression suite.");
  await page.getByRole("button", { name: "Pin it" }).click();
  await expect(page.locator(".modal-card .ev-title")).toHaveText(title);
}

test("a personal note lives on its own board and nowhere else", async ({ browser }) => {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.goto("/");
  await registerViaApi(page, "Private Pinner");
  await gotoBoard(page);

  const ownerId = await myUserId(page);
  await page.getByRole("button", { name: "Yours", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/boards/${ownerId}$`));
  await expect(
    page.getByRole("checkbox", { name: "Help Wanted / Offered" }),
    "the shared board's taxonomy has no place here",
  ).toHaveCount(0);
  await expect(page.getByRole("checkbox", { name: "Memories" })).toBeVisible();

  const title = `The bench in the rain ${Date.now()}`;
  await pinHere(page, title, "Memories");

  const drawer = page.locator(".modal-card");
  await expect(drawer.locator(".ev-title")).toHaveText(title);
  await expect(drawer).toContainText("Only you can see what you pin here.");
  await expect(drawer.getByRole("button", { name: "Respond to this note" })).toHaveCount(0);
  await expect(drawer.getByRole("button", { name: "Report this note" })).toHaveCount(0);
  const noteUrl = page.url();
  expect(noteUrl, "a personal note is addressed by its board, once").toMatch(
    new RegExp(`/boards/${ownerId}/events/[0-9a-f-]+$`),
  );
  await page.screenshot({ path: `${SHOTS}/personal-board.png` });

  await drawer.getByRole("button", { name: "Edit note" }).click();
  const typePick = drawer.locator(".type-pick");
  await expect(typePick).toContainText("Memories");
  await expect(typePick).toContainText("Plans");
  await expect(typePick, "the shared board's kinds are not on offer here").not.toContainText("Free Stuff");
  await expect(typePick).not.toContainText("Help Wanted");
  await expect(
    drawer.getByText("People can respond to this note"),
    "nor is a setting that cannot mean anything on a private note",
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Never mind" }).click();
  await page.getByRole("button", { name: "Close" }).click();

  await page.getByRole("button", { name: "The board", exact: true }).click();
  await expect(page).not.toHaveURL(/\/boards\//);
  const onShared = await page.request.get(
    "/api/v1/events?bbox=-180,-85,180,85&zoom=2&clustered=false&q=" +
      encodeURIComponent(title),
  );
  expect(
    ((await onShared.json()) as { items: unknown[] }).items,
    "the shared board never carries it",
  ).toHaveLength(0);

  const strangerCtx = await browser.newContext();
  const strangerPage = await strangerCtx.newPage();
  await strangerPage.goto("/");
  await registerViaApi(strangerPage, "Nosy Neighbour");
  await strangerPage.goto(`/boards/${ownerId}`);
  await expect(strangerPage, "somebody else's board hands you back the shared one").toHaveURL(
    /\/$|\/\?/,
  );
  await expect(strangerPage.locator(".toaster")).toContainText("isn’t open to you");

  await page.getByRole("button", { name: "Yours", exact: true }).click();
  await page.getByRole("link", { name: "My pins" }).first().click();
  await expect(page.locator(".modal-card")).toContainText("Yours");
  await page.getByRole("button", { name: "Close" }).click();
  await expect(page, "a modal opened over a board goes back to it").toHaveURL(
    new RegExp(`/boards/${ownerId}$`),
  );

  const strangerId = await myUserId(strangerPage);
  await strangerPage.goto(`/boards/${strangerId}`);
  await expect(strangerPage.locator(".status-line")).toContainText(
    "Your board is empty",
    { timeout: 20_000 },
  );

  const closed = await strangerPage.request.get(
    `/api/v1/boards/${ownerId}/events?bbox=-180,-85,180,85&zoom=2`,
  );
  expect(closed.status(), "and somebody else's board is not theirs to read").toBe(403);

  await ctx.close();
  await strangerCtx.close();
});

test("switching the feature off closes the board API, not just the switcher", async ({ page }) => {
  await registerViaApi(page, "Toggled Pinner");
  const ownerId = await myUserId(page);
  await gotoBoard(page);
  await expect(page.getByRole("button", { name: "Yours", exact: true })).toBeVisible();

  await page.getByRole("button", { name: "Yours", exact: true }).click();
  await pinHere(page, `Still here tomorrow ${Date.now()}`, "Plans");
  const noteUrl = page.url();
  const noteId = noteUrl.split("/").pop()!.split("?")[0];
  await page.getByRole("button", { name: "Close" }).click();

  await setFeatureFlag(FLAG, false);
  await expect(page.getByRole("button", { name: "Yours", exact: true })).toHaveCount(0);

  for (const path of [
    `/api/v1/boards/${ownerId}/events?bbox=-180,-85,180,85&zoom=2`,
    `/api/v1/boards/${ownerId}/events/${noteId}`,
  ]) {
    const res = await page.request.get(path);
    expect(res.status(), `${path} is closed while the feature is off`).toBe(403);
    expect(((await res.json()) as { code: string }).code).toBe("feature_disabled");
  }

  await page.goto(noteUrl);
  await expect(page, "a switched-off board hands the visitor back too").toHaveURL(/\/$|\/\?/);

  await setFeatureFlag(FLAG, true);
  await page.goto(noteUrl);
  await expect(page.locator(".modal-card .ev-title"), "and nothing was lost").toBeVisible();
});
