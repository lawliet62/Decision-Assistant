"use client";

import { useRef, useState, useSyncExternalStore, type FormEvent } from "react";
import { ensureCsrfToken, readCsrfToken } from "@/shared/api/client";

const subscribeToHydration = () => () => {};
const tokenError = "로그아웃을 준비하지 못했습니다. 다시 시도하세요.";

export function UserMenu({ email }: { email: string }) {
  const hydrated = useSyncExternalStore(subscribeToHydration, () => true, () => false);
  const tokenInput = useRef<HTMLInputElement>(null);
  const phase = useRef<"idle" | "loading" | "resuming">("idle");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function logout(event: FormEvent<HTMLFormElement>) {
    if (phase.current === "loading") {
      event.preventDefault();
      return;
    }

    const token = readCsrfToken();
    if (token && tokenInput.current) {
      tokenInput.current.value = token;
      return;
    }

    event.preventDefault();
    if (phase.current === "resuming") {
      setError(tokenError);
      return;
    }

    const form = event.currentTarget;
    phase.current = "loading";
    setLoading(true);
    setError("");
    try {
      await ensureCsrfToken();
      if (form.isConnected) {
        phase.current = "resuming";
        form.requestSubmit();
      }
    } catch {
      setError(tokenError);
    } finally {
      phase.current = "idle";
      setLoading(false);
    }
  }

  return (
    <div className="flex items-center gap-3 text-sm">
      <span className="max-w-52 truncate text-zinc-500">{email}</span>
      <form method="post" action="/logout" onSubmit={logout}>
        <input ref={tokenInput} type="hidden" name="_csrf" />
        <button
          type="submit"
          disabled={!hydrated || loading}
          className="rounded-full border border-zinc-300 bg-white px-4 py-2 font-medium text-zinc-700 hover:border-zinc-500 disabled:opacity-50"
        >
          로그아웃
        </button>
        {error && <p role="alert">{error}</p>}
      </form>
    </div>
  );
}
