import { expect, test } from "@playwright/test";
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
  await expect(page.locator(".drawer .note-title-large")).toHaveText(note.title);
  await expect(page.locator(".drawer")).toContainText("0 points");

  await page.getByRole("button", { name: "Give a point" }).click();
  await expect(page.getByRole("button", { name: "Take your point back" })).toBeVisible();
  await expect(page.locator(".drawer")).toContainText("1 point");

  await page.getByRole("button", { name: "Hide from my board" }).click();
  await expect(page.getByRole("button", { name: "Put back on my board" })).toBeVisible();
  await page.getByRole("button", { name: "Put back on my board" }).click();
  await expect(page.getByRole("button", { name: "Hide from my board" })).toBeVisible();

  await page.getByRole("button", { name: "Report this note" }).click();
  await page.getByLabel("What’s wrong with it?").selectOption("scam");
  await page.getByLabel("Anything else we should know? (optional)").fill("Regression-suite report.");
  await page.getByRole("button", { name: "Send report" }).click();
  await expect(page.getByText("Thanks — the board keepers will take a look.")).toBeVisible();
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
  await applicantPage.getByRole("button", { name: "✋ Respond to this note" }).click();
  await applicantPage
    .getByPlaceholder("Write a short note back — who you are, why you’re writing…")
    .fill("Hello from the regression suite!");
  await applicantPage.getByRole("button", { name: "Send", exact: true }).click();
  await expect(applicantPage.getByText("Your note is on its way — replies land in Messages.")).toBeVisible();
  await applicantPage.getByRole("link", { name: "Open the conversation" }).click();
  await expect(applicantPage.locator(".bubble")).toContainText("Hello from the regression suite!");

  await authorPage.goto("/");
  await expect(authorPage.locator(".badge").first()).toHaveText("1");
  await authorPage.getByRole("link", { name: "Messages" }).click();
  await authorPage.getByText("WS Applicant").click();
  await expect(authorPage.locator(".bubble")).toContainText("Hello from the regression suite!");
  await authorPage.getByPlaceholder("Write a message…").fill("Live reply, no reload needed.");
  await authorPage.getByRole("button", { name: "Send", exact: true }).click();
  await authorPage.screenshot({ path: `${SHOTS}/messages.png` });

  await expect(applicantPage.locator(".bubble").last()).toContainText("Live reply, no reload needed.", {
    timeout: 10_000,
  });

  await authorCtx.close();
  await applicantCtx.close();
});
