# 같은 작업 폴더에서 겹친 Gradle 테스트의 결과 파일 충돌

2026-11-17 Windows에서 `npm run check`의 integrationTest와 같은 backend를 사용하는 개별 integrationTest를 겹쳐 실행했다. 각각 `java.io.EOFException`과 `build/test-results/integrationTest/binary/in-progress-results-generic.bin`의 `NoSuchFileException`으로 종료했다. 그 전에 단독으로 수행한 API 검증은 통과했다.

동일한 Gradle 프로젝트의 테스트 결과 디렉터리를 두 실행이 공유했다는 점이 확인된다. 결과 파일의 생성·소비가 겹친 것이 원인이라는 판단이며, 제품 API의 assertion 실패와 구분한다. 이 오류를 해결하기 위해 assertion을 삭제하거나 테스트를 skip하지 않는다.

동일 작업 폴더의 Gradle 검증은 순차 실행한다. 전체 검사 중 새로운 검증을 추가했다면 현재 실행을 마친 후 필요한 검사만 수행한다. 서로 다른 시스템의 독립 검사(예: browser)와 병행할 때도 빌드 출력 경로를 공유하는지 먼저 확인한다.
