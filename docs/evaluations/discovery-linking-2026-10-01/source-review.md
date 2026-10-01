# 공식 상품 연결·설명 검토

2026-10-01 KST. Agent가 공개 공식 페이지와 실제 provider 응답을 검토했다. 청음·착용·실구매는 하지 않았으며, 다음 직접 조회를 앱의 자동 사양 확보로 소급하지 않는다.

## 같은 원문으로 파서 변경 효과 확인

[SourceComparison.java](SourceComparison.java)는 공개 페이지 6개를 각각 한 번 읽어 **같은 HTML·최종 URL·시각**을 변경 전후 파서에 전달했다. 원문은 메모리에서만 사용한다. [결과](source-comparison.json)는 식별자·가격 발췌·확보한 사양 항목·판정만 남긴다. 검색 순위나 LLM 출력 차이 없이 파서 효과를 확인하는 별도 진단이며 서비스 15조건 성과에는 더하지 않는다.

| 공개 페이지 | 변경 전 | 변경 후 | 해석 |
| --- | --- | --- | --- |
| [HT16 블랙 990](https://qcystore.co.kr/product/detail.html?product_no=990) | VARIANT_UNSUPPORTED | ACCEPTED, 31,800원 | `선택=블랙/화이트`만 있는 동가 그룹 복구 |
| [HT16 화이트 979](https://qcystore.co.kr/product/detail.html?product_no=979) | VARIANT_UNSUPPORTED | ACCEPTED, 31,800원 | 위 페이지와 같은 모델 식별자, 두 모델로 세지 않음 |
| [HT19 1012](https://qcystore.co.kr/product/detail.html?product_no=1012) | VARIANT_UNSUPPORTED | ACCEPTED, 50,900원 | 동일한 색상 옵션 표현 복구 |
| [T13 ANC 633](https://qcystore.co.kr/product/detail.html?product_no=633) | ACCEPTED | ACCEPTED, 28,200원 | 기존 지원 유지 |
| [LG 15U40N-GA76K](https://www.lge.co.kr/notebook/15u40n-ga76k) | RESOLVED_URL_MISMATCH | CANONICAL_URL_MISMATCH | 경로 별칭은 연결하지만 다른 모델 canonical은 계속 제외 |
| [LG 15U50U-GROWK](https://www.lge.co.kr/product/notebook/15u50u-growk) | CONDITIONAL_PRICE | CONDITIONAL_PRICE | 회원 가격 제외 유지 |

새로 확보한 QCY 상품에서도 사양 근거 블록은 비어 있었다. 상품명에 ANC가 있다는 사실만으로 ANC 성능·기본 Android 연결을 확정하지 않는다. 이미지 전용 정보 여부도 블록 누락만으로 단정하지 않는다.

비교 전 클래스는 `c45d1f9`의 bootJar에서 추출한 `BOOT-INF/classes`, 비교 후 클래스는 `bdd1f00`의 컴파일 결과다. 의존성은 같은 bootJar의 `BOOT-INF/lib`다. `SourceComparison`을 해당 lib classpath로 컴파일하고 `oldClasses newClasses outputJson`을 인자로 실행한다. 전체 애플리케이션·Secret 로더를 기동하지 않고 파서만 서로 독립된 classloader로 읽는다. 직접 HTTP 조회이므로 Firecrawl 수집 응답의 재현은 아니다.

## 실제 수집의 원인 진단

[identity-diagnostics.json](identity-diagnostics.json)은 수정 전 실제 API 실행 중 요청 URL·provider 최종 URL·canonical·옵션 속성만 선별한 자료다. LG 구경로 세 개는 같은 모델의 신경로로 이동했지만 canonical이 다른 모델을 가리켰다. QCY 세 페이지는 한국어 `선택` 옵션이어서 제외됐다. 지난 15조건의 모든 URL 불일치 18건이 같은 원인이었다고 단정하지 않는다.

IntelliJ 디버거는 Windows 명령줄 길이 제한(`CreateProcess error=206`)으로 실행되지 않았다. 에이전트가 만든 두 logpoint는 제거했고 기존 사용자 breakpoint는 유지했다. fallback은 기존 provider를 감싼 일회성 JVM 진단으로 같은 API·동의·quota 경로를 실행했다. 초기 classpath 누락으로 앱 기동에 실패한 시도는 상품 검색·수집·AI 호출이 0회였다. `META-INF/spring.factories`를 포함한 뒤 실제 진단 4조건을 실행했다.

초기 수정 코드 `00fc75c`의 이어폰 3조건에서는 색상 페이지 중복이 최종 반환에서 제거되지 않는 결함을 발견했다. 완료된 조건 사이에서 해당 평가 JVM과 runner만 종료하고 결과를 [diagnostics.json](diagnostics.json)에 보존했다. 최종 후보 중복 제거를 추가한 뒤 고정 빌드 `bdd1f00`로 15조건을 새로 실행했다. 초기 수정 결과를 최종 평가와 섞거나 중복 후보를 성과로 세지 않는다.

## 반환 후보의 가격·설명 검토 기준

T13 ANC·HT16·HT19는 위의 직접 구매 영역 가격과 일치한다. [T17APP 563](https://qcystore.co.kr/product/detail.html?product_no=563)은 별도 1회 공개 조회에서 [판매가 29,900원](t17-price-review.json)을 확인했다. 이 확인은 구매 영역의 가격 검토이며 재고·모든 옵션의 실결제 총액 보증이 아니다. 공개 페이지 직접 조회는 총 7회이고 Serper·Firecrawl·AI 호출 집계와 분리한다. 웹 도구로 별도 확인을 시도한 3개 URL 중 2개는 cache miss였으며 파서 비교나 성능 집계에 사용하지 않았다.

설명은 확인한 가격·예산과 ANC·기본 호환성 미확인을 일관되게 제시하는지, 확인하지 않은 출퇴근·통화·운동·음악 성능을 보장하지 않는지 검토한다. 가격과 미확인 안내만 있는 설명은 1/3점이며, 유용한 사양-용도 연결이 없으면 2·3점을 주지 않는다. 근거 없는 MEETS가 발견되지 않아도 MEETS 표본 자체가 없으면 정확도가 높다고 결론 내리지 않는다.

회귀 테스트는 같은 모델의 LG 경로만 연결하는지, 추가 구성·다른 모델·다른 가격·회원가를 계속 제외하는지, 색상 페이지를 하나로 반환하면서 서로 다른 출처의 사양을 합치지 않는지 검증한다. 뒤이어 `bcde1e9`에서 과거 공식 QCY 후보의 제외 이력을 새 색상 식별자에도 연결했다. 저장 데이터를 재작성하지 않으며 이 변경은 신규 Decision으로만 실행한 15조건 경로에 영향을 주지 않는다. 추가 탐색 동작은 `DiscoveryExclusionsTest`로 별도 검증했다.
