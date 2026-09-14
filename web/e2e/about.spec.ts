import { expect, test } from "@playwright/test";
import { SHOTS, gotoBoard, registerViaApi } from "./helpers";

const SUPPORT = "support@mail.lamppostal.com";

test("the question mark by the logo opens the about card, for anyone", async ({ page }) => {
  await gotoBoard(page);

  const help = page.getByRole("link", { name: "About the board" });
  await expect(help, "a signed-out visitor is told what this place is").toBeVisible();

  const logo = (await page.locator(".topbar .logo").boundingBox())!;
  const mark = (await help.boundingBox())!;
  expect(mark.x, "the mark sits beside the logo").toBeGreaterThanOrEqual(logo.x + logo.width - 1);

  await help.click();
  await expect(page).toHaveURL(/\/about$/);

  const card = page.locator(".modal-about");
  await expect(card).toBeVisible();
  await expect(card.locator("h2")).toHaveText("About the board");
  expect(await card.locator(".about-points li").count()).toBeGreaterThan(0);

  const support = card.getByRole("link", { name: SUPPORT });
  await expect(support).toHaveAttribute("href", `mailto:${SUPPORT}`);
  await page.screenshot({ path: `${SHOTS}/about.png` });

  await page.keyboard.press("Escape");
  await expect(card).toHaveCount(0);
  await expect(page).toHaveURL(/\/$|\/\?/);
});

test("the about card is a deep link, and closes back onto the board you were on", async ({ page }) => {
  await page.goto("/");
  await registerViaApi(page, "About Reader");
  const me = (await (await page.request.get("/api/v1/auth/me")).json()) as { user: { id: string } };

  await page.goto("/about");
  await expect(page.locator(".modal-about")).toBeVisible();
  await expect(page.locator(".modal-about")).toContainText(SUPPORT);

  await page.goto(`/boards/${me.user.id}`);
  await page.getByRole("link", { name: "About the board" }).click();
  await expect(page.locator(".modal-about")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();

  await expect(page, "it hands you back to the board you came from").toHaveURL(
    new RegExp(`/boards/${me.user.id}$`),
  );
});

test("the about card never grows an inner scrollbar, and its last line is not on the edge", async ({ page }) => {
  for (const [width, height] of [[1280, 900], [1280, 420], [390, 780], [390, 460]] as const) {
    await page.setViewportSize({ width, height });
    await page.goto("/about");
    const shell = page.locator(".modal-about");
    await expect(shell).toBeVisible();
    await shell.evaluate((el) => Promise.all(el.getAnimations({ subtree: true }).map((a) => a.finished)));

    const seen = await page.evaluate(() => {
      const scrim = document.querySelector(".modal-scrim") as HTMLElement;
      const body = document.querySelector(".modal-body") as HTMLElement;
      const card = document.querySelector(".modal-card") as HTMLElement;
      const mail = document.querySelector(".about-support a") as HTMLElement;
      scrim.scrollTop = 0;
      return {
        bodyScrolls: body.scrollHeight > body.clientHeight,
        cardCrops: card.scrollHeight > card.clientHeight,
        cardTop: card.getBoundingClientRect().top,
        underMail: Math.round(card.getBoundingClientRect().bottom - mail.getBoundingClientRect().bottom),
      };
    });

    expect(seen.bodyScrolls, `no inner scroll pane at ${width}x${height}`).toBe(false);
    expect(seen.cardCrops, `and nothing is cropped instead at ${width}x${height}`).toBe(false);
    expect(seen.cardTop, `the whole card is reachable at ${width}x${height}`).toBeGreaterThanOrEqual(0);
    expect(seen.underMail, `the support address sits on the edge at ${width}x${height}`).toBeGreaterThanOrEqual(12);
  }
});
