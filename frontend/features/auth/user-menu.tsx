"use client";

import { useEffect, useState } from "react";
import { ensureCsrfToken } from "@/shared/api/client";

export function UserMenu({ email }: { email: string }) {
  const [csrfToken, setCsrfToken] = useState("");

  useEffect(() => {
    ensureCsrfToken().then(setCsrfToken).catch(() => setCsrfToken(""));
  }, []);

  return (
    <div className="flex items-center gap-3 text-sm">
      <span className="max-w-52 truncate text-zinc-500">{email}</span>
      <form method="post" action="/logout">
        <input type="hidden" name="_csrf" value={csrfToken} />
        <button
          type="submit"
          disabled={!csrfToken}
          className="rounded-full border border-zinc-300 bg-white px-4 py-2 font-medium text-zinc-700 hover:border-zinc-500 disabled:opacity-50"
        >
          로그아웃
        </button>
      </form>
    </div>
  );
}
