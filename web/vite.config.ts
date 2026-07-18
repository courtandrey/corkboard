import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

const apiTarget = process.env.API_PROXY_TARGET ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  define: {
    __MAP_STYLE_URL__: JSON.stringify(
      process.env.MAP_STYLE_URL ?? "https://tiles.openfreemap.org/styles/liberty",
    ),
    __DEFAULT_CENTER__: JSON.stringify(process.env.DEFAULT_CENTER ?? "-73.9857,40.7484"),
  },
  server: {
    port: 5173,
    proxy: {
      "/api": { target: apiTarget, changeOrigin: false },
      "/ws": { target: apiTarget, ws: true },
    },
  },
  test: {
    environment: "jsdom",
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
