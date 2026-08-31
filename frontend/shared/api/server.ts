import { cookies } from "next/headers";
import type { CurrentUserResponse, DecisionResponse, DecisionSummary } from "./types";

const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

async function get<T>(path: string): Promise<T> {
  const cookieHeader = (await cookies()).toString();
  const response = await fetch(`${backendUrl}${path}`, {
    cache: "no-store",
    headers: cookieHeader ? { cookie: cookieHeader } : undefined,
  });
  if (!response.ok) {
    throw new ApiError(response.status, path);
  }
  return response.json() as Promise<T>;
}

export const serverApi = {
  me: () => get<CurrentUserResponse>("/api/v1/me"),
  decisions: () => get<DecisionSummary[]>("/api/v1/decisions"),
  decision: (id: string) => get<DecisionResponse>(`/api/v1/decisions/${id}`),
};

export class ApiError extends Error {
  constructor(public readonly status: number, path: string) {
    super(`Backend request failed: ${status} (${path})`);
  }
}
