import type { Page } from "@playwright/test";
import { expect, request } from "@playwright/test";

export const SHOTS = "e2e/.screenshots";

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
}

export const PASSWORD = "Pw-e2e-regression-1";

export const MAILPIT = process.env.MAILPIT_URL ?? "http://localhost:8025";

export async function registerViaApi(page: Page, displayName: string): Promise<string> {
  const email = await registerUnverifiedViaApi(page, displayName);
  await confirmEmail(page, email);
  return email;
}

export async function registerUnverifiedViaApi(page: Page, displayName: string): Promise<string> {
  const email = uniqueEmail("e2e");
  const res = await page.request.post("/api/v1/auth/register", {
    data: { email, password: PASSWORD, displayName },
  });
  expect(res.status(), await res.text()).toBe(201);
  return email;
}

export async function confirmEmail(page: Page, email: string): Promise<void> {
  const link = await verificationLink(page, email);
  const opened = await page.request.get(link, { maxRedirects: 0 });
  expect([302, 303]).toContain(opened.status());
}

export async function verificationLink(page: Page, email: string): Promise<string> {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const search = await page.request.get(
      `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}`,
    );
    if (search.ok()) {
      const found = (await search.json()) as { messages: { ID: string }[] };
      if (found.messages.length > 0) {
        const message = await page.request.get(`${MAILPIT}/api/v1/message/${found.messages[0].ID}`);
        const body = (await message.json()) as { HTML: string; Text: string };
        const link = /https?:\/\/[^\s"<]+auth\/verify\?token=[^\s"<]+/.exec(body.HTML ?? body.Text);
        if (link) return link[0];
      }
    }
    await page.waitForTimeout(500);
  }
  throw new Error(`no verification mail for ${email} — is the notifier stack up?`);
}

export async function createEventViaApi(
  page: Page,
  overrides: Partial<{
    type: string;
    title: string;
    body: string;
    lng: number;
    lat: number;
    applyable: boolean;
    tags: string[];
  }> = {},
): Promise<{ id: string; title: string; lng: number; lat: number }> {
  const title = overrides.title ?? `E2E note ${Date.now()}`;
  const lng = overrides.lng ?? -73.988 + Math.random() * 0.01;
  const lat = overrides.lat ?? 40.735 + Math.random() * 0.01;
  const res = await page.request.post("/api/v1/events", {
    data: {
      type: overrides.type ?? "help",
      title,
      body: overrides.body ?? "Created by the regression suite.",
      location: { lng, lat },
      applyable: overrides.applyable ?? true,
      expiresAt: new Date(Date.now() + 20 * 86_400_000).toISOString(),
      tags: overrides.tags ?? [],
    },
  });
  expect(res.status(), await res.text()).toBe(201);
  const body = (await res.json()) as { id: string };
  return { id: body.id, title, lng, lat };
}

export async function gotoBoard(page: Page): Promise<void> {
  await page.goto("/");
  await expect(page.locator(".status-line")).toBeVisible({ timeout: 20_000 });
}

export async function clickPin(page: Page, lng: number, lat: number): Promise<void> {
  const point = await page.evaluate(
    ([x, y]) => {
      const map = (window as unknown as { __corkboardMap?: { project(l: [number, number]): { x: number; y: number } } })
        .__corkboardMap;
      if (!map) throw new Error("map hook missing");
      const p = map.project([x, y]);
      return { x: p.x, y: p.y };
    },
    [lng, lat],
  );
  const box = await page.locator(".map-wrap .map").boundingBox();
  if (!box) throw new Error("map not visible");
  await page.mouse.click(box.x + point.x, box.y + point.y - 10);
}

async function asDemoAdmin<T>(
  use: (api: import("@playwright/test").APIRequestContext, headers: Record<string, string>) => Promise<T>,
): Promise<T> {
  const api = await request.newContext({ baseURL: "http://localhost:5173" });
  try {
    const login = await api.post("/api/v1/auth/login", {
      data: {
        email: "demo@corkboard.local",
        password: process.env.SEED_DEMO_PASSWORD ?? "DemoPass123!",
        transport: "bearer",
      },
    });
    expect(login.status(), await login.text()).toBe(200);
    const token = ((await login.json()) as { token: string }).token;
    return await use(api, { Authorization: `Bearer ${token}` });
  } finally {
    await api.dispose();
  }
}

export async function grantRole(targetUserId: string, role: string): Promise<void> {
  await asDemoAdmin(async (api, headers) => {
    const granted = await api.post(`/api/v1/admin/users/${targetUserId}/roles`, {
      data: { role },
      headers,
    });
    expect(granted.status(), await granted.text()).toBe(204);
  });
}

export async function setFeatureFlag(key: string, enabled: boolean): Promise<void> {
  await asDemoAdmin(async (api, headers) => {
    const flipped = await api.patch(`/api/v1/admin/features/${key}`, { data: { enabled }, headers });
    expect(flipped.status(), await flipped.text()).toBe(200);
  });
}

export async function myUserId(page: Page): Promise<string> {
  const res = await page.request.get("/api/v1/auth/me");
  expect(res.status()).toBe(200);
  return ((await res.json()) as { user: { id: string } }).user.id;
}
