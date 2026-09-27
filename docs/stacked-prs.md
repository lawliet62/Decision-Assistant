# Stacked PR의 순차 squash merge

사용자가 IntelliJ에서 기반 PR부터 main에 squash merge하면 GitHub Actions가 나머지 stack을 restack한다. AI, 별도 서버, 개발자 PC의 상주 프로세스는 사용하지 않는다. 설계 결정은 [ADR 0012](decisions/0012-stacked-pr-restack.md)에 기록한다.

## 지원 범위와 일상 작업

- 같은 저장소의 일렬 stack `A → B → C`를 지원한다. 서로 독립적인 stack은 여러 개 등록할 수 있다.
- 첫 PR의 base는 `main`, 다음 PR의 base는 직전 PR의 head 브랜치다. 브랜치 출발점과 PR base는 별개이므로 생성 시 둘 다 지정한다.
- 각 PR은 고유 변경을 가진 선형 commit 목록이어야 한다. merge commit, 빈 commit, fork, 분기형 의존 관계, `.github/workflows/` 수정은 자동 재배치 대상에서 제외한다.
- 등록 후에는 head를 동결한다. 리뷰로 수정해야 한다면 전체 후속 이력을 수동 정리한 뒤 열린 stack 전체를 다시 등록한다. 부모 수정의 자동 전파는 지원하지 않는다.
- 선행 PR이 **main에** squash merge됐을 때만 후속 브랜치를 자동 재작성한다. 자동화는 PR merge와 auto-merge를 하지 않는다.

자동화 도입 후 새 stack을 만들 때 에이전트가 IDE 터미널에서 다음을 실행한다. 아래 번호는 예시이며 현재 #17·#18은 등록하지 않는다.

```powershell
# 원격 변경 없이 등록 조건 확인
gh workflow run restack.yml --ref main -f action=dry-run -f prs=21,22,23

# PR·분기 기준 SHA를 등록하고 상태 검사를 생성
gh workflow run restack.yml --ref main -f action=register -f prs=21,22,23

gh run list --workflow restack.yml --limit 5
```

등록 순서는 기반부터다. 부모 head가 자식의 조상이어야 하며 최초 등록 전에 임의로 squash된 이력을 추정해 복원하지 않는다. 한 PR이나 브랜치는 두 stack에 속할 수 없다. 열린 동일 PR 목록의 재등록은 수동 수정 후의 새 snapshot을 명시적으로 승인하는 동작이다. 미완료 transaction이 있으면 재등록보다 복구를 먼저 수행한다.

IntelliJ의 PR 화면에서 `stack-ready`와 `validate`의 성공을 확인하고 가장 아래 기반 PR을 squash merge한다. 다음 PR의 App 댓글이 **병합 준비**로 바뀔 때까지 기다린다. 필요하면 Pull Requests 창을 `Ctrl+F5`로 새로고침한다. 별도로 에이전트에게 병합 사실을 알리지 않아도 된다.

## 최초 1회 설정 — 저장소 관리자

이 PR을 병합하는 것만으로 자동화가 켜지지는 않는다. 기본값은 비활성이며 사용자 계정으로 다음을 설정한다. 에이전트의 PR 병합 권한을 추가하는 절차가 아니다.

### 1. 전용 GitHub App

GitHub 개인 Settings → Developer settings → GitHub Apps에서 이 저장소 전용 private App을 등록한다. PC 앱 설치와 별도 서버는 필요 없다. Homepage URL에는 저장소 URL을 사용하고 webhook은 비활성화한다. 이벤트는 GitHub Actions가 수신하므로 App webhook 구독과 callback URL은 필요 없다.

Repository permissions:

| 권한 | 수준 | 용도 |
|---|---|---|
| Contents | Read and write | 읽기, 등록 브랜치·상태·백업 ref atomic push |
| Pull requests | Read and write | base 변경과 상태 댓글 |
| Checks | Read and write | `stack-ready` 검사 |
| Actions | Read-only | 현재 PR CI 실행 결과 확인 |
| Metadata | Read-only | GitHub 기본 권한 |

Administration, Workflows, 조직 권한은 부여하지 않는다. 이 App을 **Decision-Assistant 하나에만** 설치하고 private key를 발급한다. token은 매 실행 시 해당 저장소만 대상으로 발급하며 작업 종료 시 폐기된다. CLI를 사용하는 사람의 PAT를 자동화에 넘기지 않는다.

### 2. 인증 정보 격리

Repository Settings → Environments에서 `restack` 환경을 만든다.

- Deployment branches/tags를 **Selected branches and tags → Branch: main**으로 제한한다. Tag 허용 규칙은 만들지 않는다.
- 자동 실행이 목적이므로 이 환경에 매 실행 reviewer 승인을 요구하지 않는다.
- 환경 secret `RESTACK_APP_PRIVATE_KEY`에 App private key를 저장한다. 같은 이름의 repository secret은 만들지 않는다. 이 경계가 PR workflow에서 private key를 읽지 못하게 한다.
- Repository Actions variables에 `RESTACK_APP_CLIENT_ID`(문자열 Client ID), `RESTACK_APP_ID`(숫자 App ID), `RESTACK_ENABLED=false`를 등록한다. 설치 ID와 App slug는 token action이 알아낸다.
- key를 repository·PR·로그에 넣지 않는다. 키 교체 시 새 키 등록 후 이전 키를 App 설정에서 폐기한다. 이는 앱 런타임의 GCP secret 경로와 독립적인 GitHub 운영용 인증이다.

예를 들어 `gh secret set RESTACK_APP_PRIVATE_KEY --env restack`의 대화형 입력으로 저장할 수 있다. 인증 값을 명령행 인수에 직접 넣지 않는다.

### 3. main과 상태 브랜치 보호

기존 `Protect main` 규칙은 유지한다. 필요한 별도 ruleset은 다음과 같다.

| 대상 | 규칙 | bypass |
|---|---|---|
| `main` | Restrict updates | Repository admin 역할만 **For pull requests only** |
| `automation/stack-state` | Restrict creations, Restrict updates, Restrict deletions | 전용 restack App만 Always |
| `restack/**` tags | Restrict creations, Restrict updates, Restrict deletions | 전용 restack App만 Always |

main의 새 규칙은 기존 PR·CI·force push·삭제 금지 규칙과 분리한다. 관리자 예외는 이 **갱신 제한 규칙 하나**에만 적용하고, 기존 품질 규칙에는 bypass를 추가하지 않는다. App을 main의 예외 목록에 넣지 않는다. 실제 코드도 실행 전에 main의 갱신 제한과 PR/`validate` 보호가 존재하는지 확인한다.

상태 브랜치는 애플리케이션 배포 브랜치가 아니라 자동화의 transaction 기록이다. 직접 편집·삭제·PR 병합하지 않는다. 백업 tag는 자동 삭제하지 않는다. 작업 브랜치에는 restack을 막는 force push 금지 규칙을 일괄 적용하지 않는다.

이 저장소는 개인 저장소이므로 main 갱신 제한의 관리자 역할은 저장소 소유자를 뜻한다. 향후 관리자가 늘어나면 허용 대상도 다시 검토한다. 사람의 credential로 실행하는 에이전트는 GitHub가 그 사람과 구분할 수 없다. 자동화에 사람의 credential을 주지 않는 원칙은 계속 적용한다.

### 4. 비활성 점검 후 활성화

1. 워크플로와 문서 PR을 사용자가 main에 병합한다.
2. App·환경·보호 설정을 완료하고, 테스트용 3단 PR을 최신 main에서 만든다.
3. `dry-run` 등록을 실행해 원격 브랜치와 PR 설정이 바뀌지 않는지 확인한다.
4. `RESTACK_ENABLED=true`로 바꾸고 테스트 stack을 등록한다. App의 `stack-ready` check가 처음 생성된다.
5. 기존 main 필수 검사에 `stack-ready`를 **전용 App을 expected source로 지정하여** 추가한다. 기존 `validate`(GitHub Actions)를 삭제하지 않는다. 등록과 필수 검사 추가 사이에는 PR을 병합하지 않는다.
6. 첫 PR부터 사람이 squash merge하며 후속 커밋·base·CI·준비 상태를 확인한다. main과 상태 ref의 ruleset 결과에서 App의 main 갱신 금지도 확인한다. main에 실제 시험 push·merge를 실행하지 않는다.
7. 테스트 stack의 최종 파일 내용과 의도한 변경이 같고 App이 만든 backup tag가 남아 있으면 새 작업에 적용한다.

기존 CI는 모든 base의 PR과 base 편집을 검사한다. App token으로 push하므로 일반 PR CI가 발생한다. `stack-ready`는 현재 head와 base snapshot에 대응하는 `CI` 실행 성공을 확인한다. GitHub의 과거 run에 연결된 PR metadata는 나중에 바뀔 수 있으므로, CI의 `Restack snapshot <head SHA> <base SHA> <base ref>` step 이름을 latest job attempt에서 확인한다. 이 step은 실제 checkout이 이벤트의 SHA와 같은지도 검사한다. base가 다시 바뀌어 해당 실행이 없다면 CI 대기로 남는다. 과거 이벤트의 단순 re-run은 예전 base를 다시 검사할 수 있으므로 필요한 경우 PR을 닫았다가 다시 열어 현재 기준의 CI를 실행한다. 예전 성공을 수동으로 통과 처리하지 않는다.

## 상태와 실패 복구

병합 이벤트, `CI` 완료 이벤트, 15분 주기 및 수동 실행이 같은 상태 조정 코드를 사용한다. GitHub가 주기 실행을 지연하거나 pending 실행을 교체하더라도 다음 실행이 전체 등록 상태를 다시 확인한다. 실행 중인 작업은 취소하지 않는다.

상태는 `automation/stack-state`의 `state.json`에 version, stack ID, PR 번호, 브랜치, base, 기존 기반 SHA, 확인된 head SHA, 미완료 transaction을 기록한다. PR 댓글은 표시용이며 의존 관계의 source of truth가 아니다.

실제 재배치 순서:

1. main과 등록 PR들의 snapshot을 확인한다.
2. 별도 임시 Git 저장소에서 `rebase --onto`로 직접 자식은 main 위로, 그 다음 자식은 새 부모 위로 옮긴다. Git hook·rerere·외부 merge driver를 설정하거나 PR 프로그램을 실행하지 않는다.
3. 모든 후속 PR의 재배치가 성공해야 진행한다. 충돌, 빈 변경, commit 수 감소는 중단한다.
4. 명시적인 old SHA lease와 atomic push로 모든 후속 head, backup tag, transaction 상태를 함께 갱신한다. 서버가 atomic push를 지원하지 않으면 실패하며 일반 push로 대체하지 않는다.
5. 필요한 PR base를 갱신한 뒤 transaction을 완료한다. push 후 API 장애는 기록을 읽어 이 단계만 재개한다.
6. 기존 CI가 PR 코드를 일반 read-only token으로 검증한다. privileged restack 작업은 PR artifact나 코드를 실행하지 않는다.

| 표시 | 조치 |
|---|---|
| 부모 병합 대기 | 선행 PR을 main에 squash merge |
| CI 대기 | 현재 head/base의 CI 완료 확인 |
| 병합 준비 | diff 확인 후 사람이 squash merge |
| CI 실패 | 실패 로그 확인·수동 수정; 자동 rollback 없음 |
| 수동 조치 필요 | 댓글의 충돌·head/base 변경·API 장애 확인 |

재시도는 IDE 터미널에서 실행한다.

```powershell
gh workflow run restack.yml --ref main -f action=reconcile
gh run list --workflow restack.yml --limit 5
```

Git 충돌이면 push 전이므로 원격 후속 브랜치는 그대로다. 충돌을 수동 해결하고 나머지 stack도 새 부모 위로 정리한 후, 아직 열린 전체 PR 목록을 다시 등록한다. 예를 들어 #21 병합 후 수동 복구했다면 `-f prs=22,23`으로 등록한다. 생략한 선행 PR들이 실제 main에 병합됐는지 확인하며, 열린 PR 일부를 빼거나 다른 stack과 합치는 등록은 거부한다. 마지막 PR 하나만 남으면 `-f prs=23`도 가능하다. 이력이나 등록 기록을 추측하여 삭제하지 않는다.

CI 실패는 이미 갱신된 원격에서 수정하며 자동으로 예전 코드로 돌아가지 않는다. PR의 commit이 바뀌면 기존 리뷰를 그대로 신뢰하지 말고 사람이 새 diff를 검토한다.

복구 tag 형식은 `restack/<stack ID>/<병합된 부모의 원래 head SHA>/<후속 PR 번호>`다. rollback이 필요한 경우 자동화를 끈 뒤 대상 tag와 현재 원격 SHA를 확인하고, 다음과 같이 **사용자 승인 아래 해당 브랜치만** 복구한다. 예시 변수에는 확인한 값을 넣는다.

```powershell
git fetch origin --tags
git show --stat $backupTag
git ls-remote origin "refs/heads/$targetBranch"
git push "--force-with-lease=refs/heads/${targetBranch}:$observedRemoteSha" origin "${backupTag}:refs/heads/$targetBranch"
```

복구는 상태 기록과 PR base까지 정합성을 검토해야 한다. 복구 직후 자동화를 다시 켜지 않는다. 복구 tag는 사용자의 미커밋 변경을 보관하지 않는다.

비상 정지는 `gh variable set RESTACK_ENABLED --body false`로 이후 자동 실행을 막고 현재 실행을 취소한다. 완전한 정지가 필요하면 App 설치를 suspend한다. 중간에 취소해도 atomic push는 전부 성공하거나 전부 실패하며 API 후속 단계는 journal로 복구한다. 자동화를 끄면 필수 `stack-ready`가 없는 PR은 병합이 대기할 수 있으므로 운영자가 상태를 확인한다.

## 로컬 작업 공간과 검증

원격 restack은 IntelliJ에서 연 로컬 브랜치를 전환하거나 reset하지 않는다. 다음 작업 전 `git fetch origin`, `git status`, 원격과의 diff를 확인한다. rebase된 원격 브랜치에 무조건 pull/merge하지 말고 로컬 미커밋·미push 작업부터 보존한다.

`npm run test:restack`은 외부 API·App key 없이 임시 bare Git 저장소와 API fixture로 검증한다. `npm run check:quick`과 `npm run check`에도 포함된다. 테스트는 정상 순차 squash, 실제 충돌, 동시 push, atomic 거부, 부모 삭제, API 부분 실패 복구, 중복 실행, dry-run, CI snapshot 및 인증 경계를 확인한다.

참고: [GitHub 병합 이벤트](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows), [Ruleset 규칙](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets), [GitHub App token action](https://github.com/actions/create-github-app-token).
