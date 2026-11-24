import createClient from "openapi-fetch";
import type { paths } from "./schema";

export const api = createClient<paths>();

export function readCsrfToken(): string | undefined {
  if (typeof document === "undefined") return undefined;
  const prefix = "XSRF-TOKEN=";
  const value = document.cookie.split("; ").find((item) => item.startsWith(prefix));
  return value ? decodeURIComponent(value.slice(prefix.length)) : undefined;
}

export async function ensureCsrfToken(): Promise<string> {
  let token = readCsrfToken();
  if (!token) {
    const response = await fetch("/api/v1/me", {
      credentials: "same-origin",
      cache: "no-store",
    });
    if (!response.ok) throw new Error(`CSRF initialization failed: ${response.status}`);
    token = readCsrfToken();
  }
  if (!token) throw new Error("CSRF token cookie was not issued");
  return token;
}

api.use({
  async onRequest({ request }) {
    if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(request.method.toUpperCase())) {
      request.headers.set("X-XSRF-TOKEN", await ensureCsrfToken());
    }
    return request;
  },
  async onResponse({ response }) {
    if (response.status === 401 && typeof window !== "undefined") {
      window.location.assign(new URL("/login", window.location.origin));
    }
    return response;
  },
});
