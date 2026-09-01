import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { SHOTS, gotoBoard, registerViaApi, setFeatureFlag } from "./helpers";

const FLAG = "IS_SUBSCRIPTION_ENABLED";

async function myId(page: Page): Promise<string> {
  const me = (await (await page.request.get("/api/v1/auth/me")).json()) as { user: { id: string } };
  return me.user.id;
}

async function personalNote(page: Page, ownerId: string, title: string, lng: number, lat: number) {
  const res = await page.request.post(`/api/v1/boards/${ownerId}/events`, {
    data: {
      type: "plan",
      title,
      body: "Written on my own board.",
      location: { lng, lat },
      applyable: false,
      expiresAt: new Date(Date.now() + 12 * 86_400_000).toISOString(),
      tags: [],
    },
  });
  expect(res.status(), await res.text()).toBe(201);
  return (await res.json()) as { id: string };
}

async function connect(asker: Page, addresseeId: string, addressee: Page) {
  const asked = await asker.request.post("/api/v1/connections", { data: { userId: addresseeId } });
  expect(asked.status()).toBe(201);
  const { id } = (await asked.json()) as { id: string };
  expect((await addressee.request.post(`/api/v1/connections/${id}/accept`)).status()).toBe(200);
}

async function showMap(page: Page, lng: number, lat: number) {
  await page.evaluate(
    ([lng, lat]) =>
      new Promise<void>((resolve) => {
        const map = (
          window as unknown as {
            __corkboardMap: { jumpTo(o: { center: [number, number]; zoom: number }): void; once(e: string, cb: () => void): void };
          }
        ).__corkboardMap;
        map.jumpTo({ center: [lng, lat], zoom: 15 });
        map.once("idle", resolve);
        setTimeout(resolve, 10_000);
      }),
    [lng, lat],
  );
}

test("a shared board turns up in a subscriber's feed, and its notes can be answered", async ({ browser }) => {
  const ownerCtx = await browser.newContext();
  const owner = await ownerCtx.newPage();
  await owner.goto("/");
  await registerViaApi(owner, "Ines Marlow");
  const ownerId = await myId(owner);
  const stamp = Date.now();
  const note = await personalNote(owner, ownerId, `Fix the gate ${stamp}`, -73.986, 40.736);

  const readerCtx = await browser.newContext();
  const reader = await readerCtx.newPage();
  await reader.goto("/");
  await registerViaApi(reader, "Dara Quinn");
  const readerId = await myId(reader);
  await connect(owner, readerId, reader);

  await gotoBoard(reader);
  await reader.selectOption(".scope-select", "subscriptions");
  await expect(reader).toHaveURL(/\/subscriptions$/);
  await expect(reader.locator(".status-line")).toContainText("this is where the boards people share");

  await owner.goto("/me/connections");
  const row = owner.locator(".person-row", { hasText: "Dara Quinn" });
  await row.getByRole("checkbox", { name: "Let them see my board" }).click();
  await expect(row.getByRole("checkbox", { name: "Let them see my board" })).toBeChecked();
  await owner.screenshot({ path: `${SHOTS}/subscriptions-share.png` });

  await reader.reload();
  await showMap(reader, -73.986, 40.736);
  await expect(reader.locator(".status-line")).toContainText("1 note on this stretch");
  await expect(reader.locator(".panel", { hasText: "Whose boards" })).toContainText("Ines Marlow");
  await reader.screenshot({ path: `${SHOTS}/subscriptions.png` });

  await reader.goto(`/subscriptions/events/${note.id}`);
  await expect(reader.locator(".modal-card .ev-title")).toContainText(`Fix the gate ${stamp}`);
  await expect(reader.getByRole("button", { name: "Report this note" })).toHaveCount(0);
  await reader.getByRole("button", { name: "Respond to this note" }).click();
  await reader
    .getByPlaceholder("Write a short note back — who you are, why you’re writing…")
    .fill("I can bring a drill on Sunday.");
  await reader.getByRole("button", { name: "Send response" }).click();
  await expect(reader.locator(".ev-foot")).toContainText("Your note is on its way");

  await owner.goto("/me/pins");
  await expect(owner.locator(".pin-application")).toContainText("I can bring a drill on Sunday.");

  await ownerCtx.close();
  await readerCtx.close();
});

test("the feed narrows to one person, and the feature switch closes it", async ({ browser }) => {
  const oneCtx = await browser.newContext();
  const one = await oneCtx.newPage();
  await one.goto("/");
  await registerViaApi(one, "Board One");
  const oneId = await myId(one);
  const stamp = Date.now();
  await personalNote(one, oneId, `One's plan ${stamp}`, -73.9862, 40.7362);

  const otherCtx = await browser.newContext();
  const other = await otherCtx.newPage();
  await other.goto("/");
  await registerViaApi(other, "Board Other");
  const otherId = await myId(other);
  await personalNote(other, otherId, `Other's plan ${stamp}`, -73.9866, 40.7366);

  const readerCtx = await browser.newContext();
  const reader = await readerCtx.newPage();
  await reader.goto("/");
  await registerViaApi(reader, "Board Reader");
  const readerId = await myId(reader);

  for (const [page, id] of [
    [one, oneId],
    [other, otherId],
  ] as const) {
    await connect(page, readerId, reader);
    expect((await page.request.post("/api/v1/subscriptions/viewers", { data: { userId: readerId } })).status()).toBe(204);
  }

  await reader.goto("/subscriptions");
  await expect(reader.locator(".status-line")).toBeVisible({ timeout: 20_000 });
  await showMap(reader, -73.9864, 40.7364);
  await expect(reader.locator(".status-line")).toContainText("2 notes on this stretch");

  const people = reader.locator(".panel", { hasText: "Whose boards" });
  await people.locator("label", { hasText: "Board Other" }).locator("input").uncheck();
  await expect(reader.locator(".status-line")).toContainText("1 note on this stretch");
  await expect(reader).toHaveURL(/people=/);

  await setFeatureFlag(FLAG, false);
  try {
    await reader.reload();
    await expect(reader, "a switched-off feed hands the reader back").toHaveURL(/\/$|\/\?/);
    await expect(reader.locator(".scope-select")).not.toContainText("Subscriptions");
  } finally {
    await setFeatureFlag(FLAG, true);
  }

  await oneCtx.close();
  await otherCtx.close();
  await readerCtx.close();
});
