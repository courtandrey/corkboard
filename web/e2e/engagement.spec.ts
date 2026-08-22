import { expect, test } from "@playwright/test";

const arrives = expect.configure({ timeout: 25_000 });

async function openThread(page: import("@playwright/test").Page, otherParty: string) {
  await expect(async () => {
    await page.getByRole("link", { name: new RegExp(otherParty) }).click();
    await expect(page.locator(".bubble").first()).toBeVisible({ timeout: 3_000 });
  }).toPass({ timeout: 25_000 });
}
import { SHOTS, createEventViaApi, registerViaApi } from "./helpers";

test("vote, hide, and report from the drawer", async ({ browser }) => {
  const authorCtx = await browser.newContext();
  const authorPage = await authorCtx.newPage();
  await authorPage.goto("/");
  await registerViaApi(authorPage, "Engagement Author");
  const note = await createEventViaApi(authorPage, { title: `Votable note ${Date.now()}` });
  await authorCtx.close();

  const voterCtx = await browser.newContext();
  const page = await voterCtx.newPage();
  await page.goto("/");
  await registerViaApi(page, "Engagement Voter");
  await page.goto(`/events/${note.id}`);
  await expect(page.locator(".modal-card .ev-title")).toHaveText(note.title);
  await expect(page.locator(".vote .vote-count")).toHaveText("0");

  await page.getByRole("button", { name: "Give a point" }).click();
  await expect(page.getByRole("button", { name: "Take your point back" })).toBeVisible();
  await expect(page.locator(".vote .vote-count")).toHaveText("1");

  await page.getByRole("button", { name: "Hide from my board" }).click();
  await expect(page.getByRole("button", { name: "Put back on my board" })).toBeVisible();
  await page.getByRole("button", { name: "Put back on my board" }).click();
  await expect(page.getByRole("button", { name: "Hide from my board" })).toBeVisible();

  await page.getByRole("button", { name: "Report this note" }).click();
  await page.getByLabel("What’s wrong with it?").selectOption("scam");
  await page.getByPlaceholder("Anything else we should know? (optional)").fill("Regression-suite report.");
  await page.getByRole("button", { name: "Send report" }).click();
  await expect(page.getByText("Report sent — the board keepers will take a look.")).toBeVisible();
  await voterCtx.close();
});

test("apply opens a conversation; replies arrive live over the websocket", async ({ browser }) => {
  const authorCtx = await browser.newContext();
  const authorPage = await authorCtx.newPage();
  await authorPage.goto("/");
  await registerViaApi(authorPage, "WS Author");
  const note = await createEventViaApi(authorPage, {
    title: `Respond to me ${Date.now()}`,
    applyable: true,
  });

  const applicantCtx = await browser.newContext();
  const applicantPage = await applicantCtx.newPage();
  await applicantPage.goto("/");
  await registerViaApi(applicantPage, "WS Applicant");
  await applicantPage.goto(`/events/${note.id}`);
  await applicantPage.getByRole("button", { name: "Respond to this note" }).click();
  await applicantPage
    .getByPlaceholder("Write a short note back — who you are, why you’re writing…")
    .fill("Hello from the regression suite!");
  await applicantPage.getByRole("button", { name: "Send response" }).click();
  await expect(applicantPage.getByText("Your note is on its way — replies land in Messages.")).toBeVisible();
  await applicantPage.getByRole("link", { name: "Open the conversation" }).click();
  await arrives(applicantPage.locator(".bubble").first()).toContainText("Hello from the regression suite!");

  await authorPage.goto("/");
  await expect(authorPage.locator(".badge").first()).toHaveText("1");

  await authorPage.getByRole("button", { name: "Notifications" }).click();
  await authorPage.locator(".bell-open").first().click();
  await expect(authorPage.locator(".topbar .badge")).toHaveCount(0);

  await authorPage.getByRole("link", { name: "Messages" }).click();
  // the list re-renders as the websocket lands, which can swallow a click on a row
  await openThread(authorPage, "WS Applicant");
  await arrives(authorPage.locator(".bubble").first()).toContainText("Hello from the regression suite!");
  await authorPage.getByPlaceholder("Write a message…").fill("Live reply, no reload needed.");
  await authorPage.getByRole("button", { name: "Send", exact: true }).click();
  await authorPage.screenshot({ path: `${SHOTS}/messages.png` });

  await arrives(applicantPage.locator(".bubble").last()).toContainText("Live reply, no reload needed.");

  await authorCtx.close();
  await applicantCtx.close();
});

test("a reply raises the bell live, for a recipient who is not in the thread", async ({ browser }) => {
  const authorCtx = await browser.newContext();
  const authorPage = await authorCtx.newPage();
  await authorPage.goto("/");
  await registerViaApi(authorPage, "Bell Author");
  const note = await createEventViaApi(authorPage, {
    title: `Ring the bell ${Date.now()}`,
    applyable: true,
  });

  const applicantCtx = await browser.newContext();
  const applicantPage = await applicantCtx.newPage();
  await applicantPage.goto("/");
  await registerViaApi(applicantPage, "Bell Applicant");
  await applicantPage.goto(`/events/${note.id}`);
  await applicantPage.getByRole("button", { name: "Respond to this note" }).click();
  await applicantPage
    .getByPlaceholder("Write a short note back — who you are, why you’re writing…")
    .fill("First contact.");
  await applicantPage.getByRole("button", { name: "Send response" }).click();
  await applicantPage.getByRole("link", { name: "Open the conversation" }).click();
  await arrives(applicantPage.locator(".bubble").first()).toContainText("First contact.");

  await authorPage.goto("/messages");
  await openThread(authorPage, "Bell Applicant");
  await arrives(authorPage.locator(".bubble").first()).toContainText("First contact.");
  await authorPage.getByRole("link", { name: "lamppostal" }).click();
  await expect(authorPage.locator(".status-line")).toBeVisible();
  await expect(authorPage.locator(".topbar .badge")).toHaveCount(0);

  await applicantPage.getByPlaceholder("Write a message…").fill("Are we still on for Sunday?");
  await applicantPage.getByRole("button", { name: "Send", exact: true }).click();

  await expect(authorPage.locator(".topbar .badge")).toHaveText("1", { timeout: 15_000 });
  await authorPage.getByRole("button", { name: "Notifications" }).click();
  await expect(authorPage.locator(".bell-item").first()).toContainText("New message about");
  await authorPage.screenshot({ path: `${SHOTS}/message-alert.png` });

  await authorCtx.close();
  await applicantCtx.close();
});
