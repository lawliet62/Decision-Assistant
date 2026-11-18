import { defineConfig } from "@playwright/test";
import base from "./playwright.config";

// 문서 이미지는 명시적으로 요청할 때만 생성한다. 일반 E2E는 저장소 이미지를 수정하지 않는다.
export default defineConfig({
  ...base,
  testMatch: "ui-gallery.capture.ts",
  timeout: 120_000,
  retries: 0,
  use: { ...base.use, locale: "ko-KR", timezoneId: "Asia/Seoul" },
  webServer: Array.isArray(base.webServer)
    ? base.webServer.map(server => ({ ...server, reuseExistingServer: false }))
    : base.webServer,
});
