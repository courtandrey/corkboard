import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 45_000,
  workers: 1,
  expect: { timeout: 10_000 },
  use: {
    baseURL: "http://localhost:5173",
    viewport: { width: 1280, height: 720 },
    screenshot: "only-on-failure",
  },
});
