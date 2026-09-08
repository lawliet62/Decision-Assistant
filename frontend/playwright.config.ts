import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  workers: 1,
  timeout: 30_000,
  use: { baseURL: "http://127.0.0.1:3100", trace: "retain-on-failure" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    { command: "node e2e/mock-backend.mjs", url: "http://127.0.0.1:18080/api/v1/me", reuseExistingServer: true },
    { command: "npm run dev -- --hostname 127.0.0.1 --port 3100", url: "http://127.0.0.1:3100/decisions/new", reuseExistingServer: true,
      env: { BACKEND_URL: "http://127.0.0.1:18080" } },
  ],
});
