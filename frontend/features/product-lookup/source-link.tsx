type Kind = "VIDEO" | "BLOG" | "OFFICIAL_STORE" | "WEB";
function kindOf(url: string): Kind {
  try {
    const host = new URL(url).hostname;
    if (["youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "vimeo.com", "www.vimeo.com"].includes(host)) return "VIDEO";
    if (["blog.naver.com", "m.blog.naver.com", "medium.com"].includes(host) || host.endsWith(".tistory.com")) return "BLOG";
  } catch { /* 저장된 출처의 표시만 수행한다. */ }
  return "WEB";
}
export function SourceLink({ url, kind, label }: { url?: string; kind?: Kind; label?: string }) {
  if (!url) return null;
  const type = kind ?? kindOf(url);
  const title = type === "VIDEO" ? "영상" : type === "BLOG" ? "블로그" : type === "OFFICIAL_STORE" ? "공식 판매처" : "웹페이지";
  return <a href={url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-xs font-normal text-emerald-800">
    <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
      {type === "VIDEO" ? <><rect x="2" y="4" width="16" height="12" rx="3" /><path d="m8 7 5 3-5 3Z" /></> : type === "OFFICIAL_STORE" ? <><path d="m2 7 2-4h12l2 4M3 7h14v10H3Z" /><path d="M8 17v-6h4v6" /></> : <><rect x="4" y="2" width="12" height="16" rx="2" /><path d="M7 7h6M7 10h6M7 13h4" /></>}
    </svg>{label ?? `${title} 보기`} ↗
  </a>;
}
