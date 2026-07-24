import { expect, test } from "@playwright/test";
import { SHOTS, createEventViaApi, registerViaApi } from "./helpers";

test("a long thread opens at the newest message and scrolls back for older ones", async ({ browser }) => {
  const authorCtx = await browser.newContext();
  const authorPage = await authorCtx.newPage();
  await authorPage.goto("/");
  await registerViaApi(authorPage, "Thread Author");
  const note = await createEventViaApi(authorPage, { title: `Long thread ${Date.now()}`, applyable: true });

  const applicantCtx = await browser.newContext();
  const applicantPage = await applicantCtx.newPage();
  await applicantPage.goto("/");
  await registerViaApi(applicantPage, "Thread Applicant");
  const applied = await applicantPage.request.post(`/api/v1/events/${note.id}/apply`, {
    data: { message: "message 1" },
  });
  expect(applied.status(), await applied.text()).toBe(201);
  const { conversationId } = (await applied.json()) as { conversationId: string };

  for (let i = 2; i <= 60; i++) {
    const page = i % 2 === 0 ? authorPage : applicantPage;
    const res = await page.request.post(`/api/v1/conversations/${conversationId}/messages`, {
      data: { body: `message ${i}` },
    });
    expect(res.status(), await res.text()).toBe(201);
  }

  await authorPage.goto(`/messages/${conversationId}`);
  const bubbles = authorPage.locator(".thread-messages .bubble");
  await expect(bubbles).toHaveCount(50);
  await expect(bubbles.last()).toContainText("message 60");

  const scrolledToBottom = await authorPage.evaluate(() => {
    const list = document.querySelector(".thread-messages")!;
    return list.scrollHeight - list.scrollTop - list.clientHeight < 4;
  });
  expect(scrolledToBottom, "a thread opens on the newest message").toBe(true);

  await authorPage.getByRole("button", { name: "Read what came before" }).click();
  await expect(bubbles).toHaveCount(60);
  await expect(bubbles.first()).toContainText("message 1");
  await expect(authorPage.getByRole("button", { name: "Read what came before" })).toHaveCount(0);
  await authorPage.screenshot({ path: `${SHOTS}/messages-long-thread.png` });

  await authorCtx.close();
  await applicantCtx.close();
});
