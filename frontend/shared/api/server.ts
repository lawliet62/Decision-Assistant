import type { DecisionResponse, DecisionSummary } from "./types";

const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

async function get<T>(path: string): Promise<T> {
  const response = await fetch(`${backendUrl}${path}`, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`Backend request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export const serverApi = {
  decisions: () => get<DecisionSummary[]>("/api/v1/decisions"),
  decision: (id: string) => get<DecisionResponse>(`/api/v1/decisions/${id}`),
};
