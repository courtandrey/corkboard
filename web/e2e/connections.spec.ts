import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { SHOTS, createEventViaApi, registerViaApi } from "./helpers";

async function handleOf(page: Page): Promise<string> {
  const me = (await (await page.request.get("/api/v1/auth/me")).json()) as { user: { handle: string } };
  return me.user.handle;
}

test("asking to connect, hearing about it, and chatting once it is accepted", async ({ browser }) => {
  const askerCtx = await browser.newContext();
  const asker = await askerCtx.newPage();
  await asker.goto("/");
  await registerViaApi(asker, "Nora Ferry");

  const askedCtx = await browser.newContext();
  const asked = await askedCtx.newPage();
  await asked.goto("/");
  await registerViaApi(asked, "Ivo Barnes");
  const askedHandle = await handleOf(asked);

  await asker.goto("/me/connections");
  await asker.getByLabel("find someone by user ID or name…").fill(askedHandle);
  const row = asker.locator(".person-row", { hasText: askedHandle });
  await expect(row).toHaveCount(1);
  await expect(row).toContainText("Ivo Barnes");
  await row.getByRole("button", { name: "Connect" }).click();
  await expect(asker.locator(".person-row", { hasText: askedHandle })).toContainText("Pending");
  await asker.screenshot({ path: `${SHOTS}/connections.png` });

  await asked.goto("/");
  await expect(asked.locator(".topbar .badge")).toHaveText("1", { timeout: 20_000 });
  await asked.getByRole("button", { name: "Notifications" }).click();
  await expect(asked.locator(".bell-item").first()).toContainText("Nora Ferry would like to connect");
  await asked.locator(".bell-item").first().click();
  await expect(asked).toHaveURL(/\/me\/connections/);

  const incoming = asked.locator(".connections-section", { hasText: "Asking to connect" });
  await expect(incoming).toContainText("Nora Ferry");
  await incoming.getByRole("button", { name: "Accept" }).click();

  const connected = asked.locator(".connections-section", { hasText: "Connected" });
  await expect(connected.locator(".person-row")).toHaveCount(1);
  await expect(asked.locator(".connections-section", { hasText: "Asking to connect" })).toHaveCount(0);

  await connected.getByRole("button", { name: "Nora Ferry" }).click();
  await expect(asked).toHaveURL(/\/messages\/[0-9a-f-]+$/);
  await asked.getByPlaceholder("Write a message…").fill("Good to know you.");
  await asked.getByRole("button", { name: "Send", exact: true }).click();
  await expect(asked.locator(".bubble").last()).toContainText("Good to know you.");

  await expect(
    asker.locator(".topbar .badge"),
    "the asker hears that it was accepted",
  ).toBeVisible({ timeout: 20_000 });

  await askerCtx.close();
  await askedCtx.close();
});

test("a dismissed request goes away for both sides, and can be asked again", async ({ browser }) => {
  const askerCtx = await browser.newContext();
  const asker = await askerCtx.newPage();
  await asker.goto("/");
  await registerViaApi(asker, "Persistent Pat");

  const askedCtx = await browser.newContext();
  const asked = await askedCtx.newPage();
  await asked.goto("/");
  await registerViaApi(asked, "Unsure Uli");
  const askedHandle = await handleOf(asked);

  await asker.goto("/me/connections");
  await asker.getByLabel("find someone by user ID or name…").fill(askedHandle);
  await asker.locator(".person-row", { hasText: askedHandle }).getByRole("button", { name: "Connect" }).click();
  await expect(asker.locator(".person-row", { hasText: askedHandle })).toContainText("Pending");

  await asked.goto("/me/connections");
  const incoming = asked.locator(".connections-section", { hasText: "Asking to connect" });
  await expect(incoming).toContainText("Persistent Pat");
  await incoming.getByRole("button", { name: "Dismiss" }).click();
  await expect(asked.locator(".connections-section", { hasText: "Asking to connect" })).toHaveCount(0);
  await expect(asked.locator(".empty-state")).toContainText("No connections yet");

  await asker.reload();
  await expect(
    asker.locator(".connections-section", { hasText: "Requested" }),
    "a dismissed request stops waiting",
  ).toHaveCount(0);

  await asker.getByLabel("find someone by user ID or name…").fill(askedHandle);
  const again = asker.locator(".person-row", { hasText: askedHandle });
  await expect(again.getByRole("button", { name: "Connect" }), "and can be asked again").toBeVisible();

  await askerCtx.close();
  await askedCtx.close();
});

test("a name is clickable wherever it appears, and the card says where you stand", async ({ browser }) => {
  const authorCtx = await browser.newContext();
  const author = await authorCtx.newPage();
  await author.goto("/");
  await registerViaApi(author, "Wren Halloway");
  const authorHandle = await handleOf(author);
  const note = await createEventViaApi(author, { title: `A note by Wren ${Date.now()}`, applyable: true });

  const readerCtx = await browser.newContext();
  const reader = await readerCtx.newPage();
  await reader.goto("/");
  await registerViaApi(reader, "Bo Castellan");

  await reader.goto(`/events/${note.id}`);
  await reader.locator(".ev-meta .person-link").click();
  const card = reader.locator(".modal-person");
  await expect(card).toContainText("Wren Halloway");
  await expect(card).toContainText(`@${authorHandle}`);
  await expect(card, "the card says when they joined").toContainText("on the board since");
  await card.getByRole("button", { name: "Connect" }).click();
  await expect(card).toContainText("Connection requested");
  await reader.screenshot({ path: `${SHOTS}/person-card.png` });

  await reader.keyboard.press("Escape");
  await expect(card).toHaveCount(0);
  await expect(reader.locator(".modal-card .ev-title"), "the note is still open beneath").toBeVisible();

  await author.goto("/me/connections");
  await author.locator(".connections-section", { hasText: "Asking to connect" }).getByRole("button", { name: "Accept" }).click();
  await expect(author.locator(".connections-section", { hasText: "Connected" })).toContainText("Bo Castellan");

  await reader.reload();
  await reader.locator(".ev-meta .person-link").click();
  await expect(card).toContainText("Connected");
  await card.getByRole("button", { name: "Message" }).click();
  await expect(reader).toHaveURL(/\/messages\/[0-9a-f-]+$/);

  await reader.locator(".thread-who .person-link").click();
  await expect(card).toContainText("Wren Halloway");

  await authorCtx.close();
  await readerCtx.close();
});
