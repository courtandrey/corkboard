import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { App } from "./App";
import { strings } from "./i18n/strings";

vi.mock("maplibre-gl", () => {
  class FakeMap {
    addControl() {}
    on() {}
    once() {}
    off() {}
    remove() {}
    getCanvas() {
      return { style: {} };
    }
  }
  class Passive {}
  return {
    default: {
      Map: FakeMap,
      NavigationControl: Passive,
      Marker: Passive,
      Popup: Passive,
    },
  };
});

const meta = {
  types: [
    { key: "lost_found", label: "Lost & Found", color: "#D9822B", applyableDefault: true },
    { key: "notice", label: "Notices", color: "#8A8A8A", applyableDefault: false },
  ],
  scopes: [
    { key: "global", label: "The board", types: ["lost_found", "notice"] },
    { key: "personal", label: "Your board", types: ["notice"] },
  ],
  limits: {
    displayNameMax: 50, passwordMin: 8, passwordMax: 128, titleMin: 3, titleMax: 120,
    bodyMax: 4000, tagsMax: 5, tagNameMin: 2, tagNameMax: 40, messageMax: 2000,
    reportDetailMax: 500, expiryDefaultDays: 30,
    viewportLimitDefault: 60, viewportLimitMax: 100,
  },
  reportThreshold: 5,
  googleAuth: false,
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

beforeEach(() => {
  vi.stubGlobal(
    "matchMedia",
    (query: string) =>
      ({
        matches: false,
        media: query,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
      }) as unknown as MediaQueryList,
  );
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("/api/v1/meta")) return jsonResponse(meta);
      if (url.includes("/api/v1/features")) {
        return jsonResponse({ flags: { ARE_USER_DETAILS_EDITABLE: true } });
      }
      if (url.includes("/api/v1/auth/me")) {
        return jsonResponse({ status: 401, code: "unauthenticated", detail: "Sign in" }, 401);
      }
      if (url.includes("/api/v1/tags")) return jsonResponse({ items: [] });
      return jsonResponse({ items: [], total: 0, truncated: false });
    }),
  );
});

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("board smoke", () => {
  it("renders the chrome and the server-driven type filters", async () => {
    renderAt("/");
    expect(await screen.findByText("Lost & Found")).toBeTruthy();
    expect(screen.getByText("Notices")).toBeTruthy();
    expect(screen.getByLabelText(strings.board.searchPlaceholder)).toBeTruthy();
    expect(screen.getByText(strings.board.pinANote)).toBeTruthy();
    expect(screen.getByLabelText(strings.appName)).toBeTruthy();
  });
});

describe("create flow smoke", () => {
  it("gates anonymous visitors behind sign-in", async () => {
    renderAt("/new");
    expect(await screen.findByText(strings.auth.signInToPin)).toBeTruthy();
    expect(screen.getByLabelText(strings.auth.email)).toBeTruthy();
    expect(screen.getByLabelText(strings.auth.password)).toBeTruthy();
  });
});
