import { defineConfig } from "@playwright/test";

const frontendPort = Number(process.env.E2E_FRONTEND_PORT || 5174);
const baseURL = process.env.E2E_BASE_URL || `http://127.0.0.1:${frontendPort}`;
const backendURL = process.env.E2E_BACKEND_URL || "http://127.0.0.1:8081";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 30_000,
  expect: { timeout: 8_000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    channel: process.env.E2E_BROWSER_CHANNEL || "msedge",
    headless: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure"
  },
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${frontendPort}`,
    url: baseURL,
    reuseExistingServer: true,
    timeout: 60_000,
    env: {
      ...process.env,
      VITE_BACKEND_TARGET: backendURL
    }
  }
});
