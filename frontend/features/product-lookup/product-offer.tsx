"use client";

import { useState } from "react";
import type { components } from "@/shared/api/schema";
import { SourceLink } from "./source-link";
import { confirmationTime } from "./confirmation-time";

export function ProductOffer({ offer, name }: { offer?: components["schemas"]["OfficialOffer"]; name?: string }) {
  if (!offer) return null;
  return <div className="space-y-2">
    <ProductImage key={offer.imageUrl} url={offer.imageUrl} name={name} />
    <p className="text-lg font-semibold">{offer.price?.toLocaleString("ko-KR")}원 <span className="text-xs font-normal text-zinc-500">확인 판매가</span></p>
    <SourceLink url={offer.purchaseUrl} kind="OFFICIAL_STORE" label={`${offer.seller ?? "공식 판매처"}에서 구매 정보 보기`} />
    <p className="text-xs text-zinc-500">가격 확인 시각: {confirmationTime(offer.fetchedAt) ?? "미확인"}</p>
    <p className="text-xs text-zinc-500">배송비·추가 혜택은 판매처에서 확인하세요. 가격과 재고는 달라질 수 있습니다.</p>
    {offer.policyVersion === "catalog-v1" && <p className="text-xs text-zinc-500">저장된 비교 시점의 판매 정보입니다. 확인 후 72시간이 지난 가격은 새 예산 판정에 사용할 수 없습니다. 구매 전 판매처에서 가격과 재고를 확인하세요.</p>}
  </div>;
}
function ProductImage({ url, name }: { url?: string; name?: string }) {
  const [failed, setFailed] = useState(false);
  return <div className="flex h-36 items-center justify-center overflow-hidden rounded-xl bg-zinc-50">
    {url && !failed ?
      // 브라우저에서 공식 이미지 주소를 직접 연다. Next 이미지 프록시로 외부 요청을 옮기지 않는다.
      // eslint-disable-next-line @next/next/no-img-element
      <img src={url} alt={`${name ?? "제품"} 이미지`} loading="lazy" referrerPolicy="no-referrer" onError={() => setFailed(true)} className="h-full w-full object-contain" />
      : <span className="text-sm font-normal text-zinc-500">제품 이미지 없음</span>}
  </div>;
}
