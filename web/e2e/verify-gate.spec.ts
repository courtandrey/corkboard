import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";
import {
  SHOTS,
  createEventViaApi,
  gotoBoard,
  registerUnverifiedViaApi,
  registerViaApi,
  verificationLink,
} from "./helpers";

async function openSomeoneElsesNote(page: Page) {
  await registerViaApi(page, "Confirmed Author");
  const note = await createEventViaApi(page, { title: `Gate target ${Date.now()}`, applyable: true });
  await page.request.post("/api/v1/auth/logout");

  const email = await registerUnverifiedViaApi(page, "Unconfirmed Reader");
  await page.goto(`/events/${note.id}`);
  await expect(page.locator(".modal-card .ev-title")).toHaveText(note.title);
  return { note, email };
}

async function withoutRequests(page: Page, pattern: RegExp, action: () => Promise<void>) {
  const sent: string[] = [];
  const watch = (request: { method: () => string; url: () => string }) => {
    if (request.method() !== "GET" && pattern.test(request.url())) sent.push(request.url());
  };
  page.on("request", watch);
  await action();
  page.off("request", watch);
  expect(sent, "the blocked action must not reach the backend").toEqual([]);
}

test("blocked actions explain themselves instead of doing nothing", async ({ page }) => {
  const { email } = await openSomeoneElsesNote(page);

  for (const [label, click] of [
    ["vote", () => page.locator(".vote").click()],
    ["report", () => page.getByRole("button", { name: "Report this note" }).click()],
    ["respond", () => page.getByRole("button", { name: "Respond to this note" }).click()],
  ] as const) {
    await withoutRequests(page, /\/api\/v1\/events\//, async () => {
      await click();
      await expect(page.getByRole("heading", { name: "One step left" }), `${label} opens the gate`).toBeVisible();
    });
    const gate = page.getByRole("dialog", { name: "One step left" });
    await expect(gate).toContainText(email);
    if (label === "vote") {
      const row = (await gate.locator(".modal-actions").boundingBox())!;
      const primary = (await gate.getByRole("button", { name: "Send it again" }).boundingBox())!;
      const ghost = (await gate.getByRole("button", { name: "Not now" }).boundingBox())!;
      expect(primary.height, "the icon must not make one button taller").toBe(ghost.height);
      expect(primary.y).toBe(ghost.y);
      expect(primary.x, "the primary sits flush left").toBeCloseTo(row.x, 0);
      expect(ghost.x + ghost.width, "the way out sits flush right").toBeCloseTo(row.x + row.width, 0);
      await page.screenshot({ path: `${SHOTS}/verify-gate.png` });
    }
    await page.getByRole("button", { name: "Not now" }).click();
    await expect(page.getByRole("heading", { name: "One step left" })).toHaveCount(0);
  }
});

test("hiding a note is allowed while unconfirmed — it only changes your own view", async ({ page }) => {
  await openSomeoneElsesNote(page);

  await page.getByRole("button", { name: "Hide from my board" }).click();
  await expect(page.locator(".toaster")).toContainText("board");
  await expect(page.getByRole("heading", { name: "One step left" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Put back on my board" })).toBeVisible();
});

test("the read-only banner can be dismissed and stays dismissed", async ({ page }) => {
  await gotoBoard(page);
  const email = await registerUnverifiedViaApi(page, "Banner Dismisser");
  await page.reload();

  const banner = page.locator(".verify-banner");
  await expect(banner).toContainText(email);
  await banner.getByRole("button", { name: "Dismiss" }).click();
  await expect(banner).toHaveCount(0);

  await page.reload();
  await expect(page.locator(".verify-banner"), "dismissal survives a reload").toHaveCount(0);

  await page.getByRole("button", { name: "Pin a note" }).click();
  await expect(page.getByRole("heading", { name: "One step left" })).toBeVisible();
});

test("confirming elsewhere lifts the read-only state in an open tab, with no reload", async ({ page }) => {
  await gotoBoard(page);
  const email = await registerUnverifiedViaApi(page, "Waiting Resident");
  await page.reload();
  await expect(page.locator(".verify-banner")).toContainText(email);

  await page.getByRole("button", { name: "Pin a note" }).click();
  await expect(page.getByRole("heading", { name: "One step left" })).toBeVisible();

  const link = await verificationLink(page, email);
  const opened = await page.request.get(link, { maxRedirects: 0 });
  expect([302, 303]).toContain(opened.status());

  await expect(page.locator(".toaster")).toContainText("Email confirmed");
  await expect(page.getByRole("heading", { name: "One step left" })).toHaveCount(0);
  await expect(page.locator(".verify-banner")).toHaveCount(0);

  await page.getByRole("button", { name: "Pin a note" }).click();
  await expect(page.getByText("Click the map where your note belongs.")).toBeVisible();
});

test("a confirmed session stops asking the server who it is", async ({ page }) => {
  await page.clock.install();
  await gotoBoard(page);
  await registerViaApi(page, "Settled Resident");
  await page.reload();
  await expect(page.locator(".whoami")).toContainText("Settled Resident");

  const calls: string[] = [];
  page.on("request", (r) => {
    if (r.url().includes("/api/v1/auth/me")) calls.push(r.url());
  });

  await page.clock.fastForward("10:00");
  await page.evaluate(() => {
    document.dispatchEvent(new Event("visibilitychange"));
    window.dispatchEvent(new Event("focus"));
  });

  await page.getByRole("button", { name: "Pin a note" }).click();
  await expect(page.getByText("Click the map where your note belongs.")).toBeVisible();
  await page.keyboard.press("Escape");
  await page.locator(".topbar-links").getByRole("link", { name: "Messages" }).click();
  await expect(page).toHaveURL(/\/messages/);
  await page.getByRole("link", { name: "lamppostal" }).click();
  await expect(page.locator(".status-line")).toBeVisible();
  await page.waitForTimeout(1_000);

  expect(calls, "a confirmed session has nothing left to ask about").toEqual([]);
});
