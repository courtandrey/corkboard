import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";
import { SHOTS, gotoBoard, registerViaApi } from "./helpers";

type Edge = "top" | "bottom" | "left" | "right";

const EDGES: Edge[] = ["top", "bottom", "left", "right"];

async function armAndPlace(page: Page) {
  await page.getByRole("button", { name: "Pin a note" }).click();
  const map = (await page.locator(".map-wrap .map").boundingBox())!;
  await page.mouse.click(map.x + map.width / 2, map.y + map.height / 2);
  await expect(page.locator(".pin-callout")).toBeVisible();
  return map;
}

async function pinTo(page: Page, edge: Edge, margin = 10) {
  await page.evaluate(
    ([side, gap]) => {
      const map = (window as unknown as { __corkboardMap: maplibregl.Map }).__corkboardMap;
      const view = map.getContainer().getBoundingClientRect();
      const pin = document.querySelector(".draft-pin-wrap")!.getBoundingClientRect();
      const x = pin.left + pin.width / 2 - view.left;
      const y = pin.bottom - view.top;
      const margin = gap as number;

      const target =
        side === "top"
          ? [x, margin]
          : side === "bottom"
            ? [x, view.height - margin]
            : side === "left"
              ? [margin, y]
              : [view.width - margin, y];
      map.panBy([x - target[0], y - target[1]], { duration: 0 });
    },
    [edge, margin] as const,
  );
  await page.waitForTimeout(300);
}

async function report(page: Page) {
  return page.evaluate(() => {
    const doc = document.documentElement;
    const host = document.querySelector(".draft-callout-host") as HTMLElement;
    const box = (document.querySelector(".pin-callout") as HTMLElement).getBoundingClientRect();
    return {
      scrolls: doc.scrollWidth > doc.clientWidth || doc.scrollHeight > doc.clientHeight,
      shift: host.style.getPropertyValue("--callout-shift"),
      lift: host.style.getPropertyValue("--callout-lift"),
      box: { top: box.top, right: box.right, bottom: box.bottom, left: box.left },
    };
  });
}

async function checkEdges(page: Page, label: string) {
  const map = await armAndPlace(page);

  for (const edge of EDGES) {
    await pinTo(page, edge);
    const first = await report(page);
    await page.waitForTimeout(400);
    const second = await report(page);

    expect(first.scrolls, `${label}, ${edge} edge: the page must not gain a scrollbar`).toBe(false);
    expect(
      { scrolls: second.scrolls, shift: second.shift, lift: second.lift },
      `${label}, ${edge} edge: the placement must settle, not oscillate`,
    ).toEqual({ scrolls: first.scrolls, shift: first.shift, lift: first.lift });

    const { box } = second;
    expect(box.top, `${label}, ${edge} edge: inside the map`).toBeGreaterThanOrEqual(map.y - 1);
    expect(box.bottom).toBeLessThanOrEqual(map.y + map.height + 1);
    expect(box.left).toBeGreaterThanOrEqual(map.x - 1);
    expect(box.right).toBeLessThanOrEqual(map.x + map.width + 1);

    await expect(page.getByRole("button", { name: "Write the note" })).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/pin-callout-${label}-${edge}.png` });
  }
}

test("the placement callout stays on the map at every edge, without moving the page", async ({ page }) => {
  await registerViaApi(page, "Callout Checker");
  await gotoBoard(page);
  await checkEdges(page, "desktop");
});

test("phone: the callout survives the narrow viewport too", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 700 });
  await registerViaApi(page, "Callout Checker");
  await gotoBoard(page);
  await checkEdges(page, "phone");
});
