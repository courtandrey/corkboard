import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";
import { createEventViaApi, gotoBoard, registerViaApi } from "./helpers";

const NOTE_FONT = '700 27px "Caveat"';

async function watchTitleFace(page: Page, id: string): Promise<{ text: string; ready: boolean }[]> {
  await page.addInitScript(() => {
    const seen: { text: string; ready: boolean }[] = [];
    (window as unknown as { __faces: typeof seen }).__faces = seen;
    const start = () => {
      const observer = new MutationObserver(() => {
        const el = document.querySelector(".ev-title") as HTMLElement | null;
        if (!el?.textContent) return;
        seen.push({
          text: el.textContent,
          ready: document.fonts.check('700 27px "Caveat"', el.textContent),
        });
      });
      observer.observe(document.documentElement, { childList: true, subtree: true, characterData: true });
    };
    if (document.documentElement) start();
    else document.addEventListener("readystatechange", start, { once: true });
  });

  await page.goto(`/events/${id}`);
  await page.locator(".ev-title").waitFor();
  return page.evaluate(() => (window as unknown as { __faces: { text: string; ready: boolean }[] }).__faces);
}

for (const [script, title] of [
  ["latin", `Latin note ${Date.now()}`],
  ["cyrillic", `Кириллица ${Date.now()}`],
  ["greek", `Ελληνικά ${Date.now()}`],
] as const) {
  test(`a ${script} title is never painted in the fallback face`, async ({ page }) => {
    await registerViaApi(page, "Font Reader");
    const note = await createEventViaApi(page, { title });
    await gotoBoard(page);

    const early = await page.evaluate((spec) => document.fonts.check(spec, "Кириллица"), NOTE_FONT);
    if (script === "cyrillic") expect(early, "nothing is loaded up front").toBe(false);

    const paints = await watchTitleFace(page, note.id);
    expect(paints.length, "the title did paint").toBeGreaterThan(0);
    expect(
      paints.filter((p) => !p.ready),
      "every paint of the title had its face ready",
    ).toEqual([]);
  });
}

test("the loading state covers the wait, so the card never appears half-drawn", async ({ page }) => {
  await registerViaApi(page, "Font Reader");
  const note = await createEventViaApi(page, { title: `Ждём шрифт ${Date.now()}` });
  await gotoBoard(page);

  await page.route("**/fonts/caveat-cyrillic.woff2", async (route) => {
    await new Promise((r) => setTimeout(r, 1_500));
    await route.continue();
  });

  await page.goto(`/events/${note.id}`);
  await expect(page.locator(".modal-card .empty-state")).toBeVisible();
  await expect(page.locator(".ev-title")).toHaveCount(0);

  await expect(page.locator(".ev-title")).toHaveText(/Ждём шрифт/, { timeout: 15_000 });
  expect(
    await page.evaluate((spec) => document.fonts.check(spec, "Ждём шрифт"), NOTE_FONT),
    "by the time the card is up, its face is usable",
  ).toBe(true);
});
