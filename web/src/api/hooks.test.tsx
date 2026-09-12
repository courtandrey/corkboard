import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEventDetail } from "./hooks";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

beforeEach(() => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      if (url.pathname.startsWith("/api/v1/boards/")) return jsonResponse({ id: "n1", title: "on my board" });
      return jsonResponse({ status: 404, code: "not_found", detail: "gone" }, 404);
    }),
  );
});

function wrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
}

describe("useEventDetail", () => {
  it("caches a note per board, so one board's answer is never shown for another's address", async () => {
    const wrap = wrapper();

    const personal = renderHook(() => useEventDetail("n1", "owner-1"), { wrapper: wrap });
    await waitFor(() => expect(personal.result.current.data).toBeTruthy());

    const shared = renderHook(() => useEventDetail("n1", null), { wrapper: wrap });
    expect(
      shared.result.current.data,
      "the shared board must not borrow what the personal board answered",
    ).toBeUndefined();

    await waitFor(() => expect(shared.result.current.error).toBeTruthy());
    expect(shared.result.current.data).toBeUndefined();
  });
});
