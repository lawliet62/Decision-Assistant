# 읽기 전용 VM 점검에서 `gcloud compute ssh`가 프로젝트 메타데이터를 변경하려는 경우

## 증상과 기대 결과

2026-11-26 - 2026-11-27에 운영 VM의 백업·컨테이너 상태를 읽기 전용으로 점검하면서 `gcloud compute ssh --tunnel-through-iap --command ...`를 실행했다. 원격 명령은 정상적으로 실행됐지만, 매번 접속 전에 다음 출력이 나타났다.

```text
Updating project ssh metadata...
..failed.
```

읽기 전용 점검은 VM·프로젝트 설정을 변경하거나 변경을 시도하지 않아야 한다. 이 출력은 조회 명령이 프로젝트 메타데이터에 대한 쓰기를 시도했다는 의미다.

## 원인과 확인 범위

확인된 사실:

- VM은 OS Login을 사용하지 않는다. 접속용 공개키는 인스턴스 메타데이터의 `ssh-keys`에만 있고, 프로젝트 메타데이터는 비어 있다. `block-project-ssh-keys`도 설정되지 않았다.
- [`gcloud compute ssh`](https://cloud.google.com/sdk/gcloud/reference/compute/ssh)는 OS Login을 쓰지 않고 프로젝트 키가 차단되지 않은 VM에서 로컬 공개키를 프로젝트 메타데이터에서 찾고, 없으면 접속 전에 추가하려 한다. 실제 인증은 인스턴스 메타데이터에 이미 있는 키로 성공하므로, 쓰기가 실패해도 원격 명령 결과에는 영향이 없다.
- 실행 계정에는 `roles/compute.editor`가 있지만 쓰기는 실패했다.

추정:

- 실패 원인은 프로젝트 메타데이터 변경에 필요한 서비스 계정 사용 권한(`roles/iam.serviceAccountUser`)이 없기 때문으로 보인다. gcloud가 상세 오류를 출력하지 않아 확정하지 않았다.
- 이 추정이 맞다면 이후 다른 작업으로 해당 권한이 부여되는 순간, 같은 점검 명령이 확인 절차 없이 프로젝트 전체에 SSH 키를 추가한다. 현재의 실패는 의도된 방어가 아니라 우연한 권한 부족이다.

## 조치: 터널과 SSH 클라이언트 분리

상태: **부분 해결.** 이 절차를 따르는 점검자의 쓰기 시도만 없앴고, 서버 측 차단은 적용하지 않았다([남은 선택지](#남은-선택지)).

VM·메타데이터·IAM(Identity and Access Management)은 변경하지 않고, 점검하는 PC의 접속 절차만 바꿨다. IAP(Identity-Aware Proxy) 터널을 여는 `gcloud compute start-iap-tunnel`은 터널만 열고 메타데이터를 수정하지 않는다.

```powershell
# 1. IAP 터널만 별도 프로세스로 연다. gcloud 기본 project는 바꾸지 않고 --project로 지정한다.
$gcloud = Join-Path (Split-Path (Get-Command gcloud).Source) 'gcloud.cmd'
$tunnel = Start-Process $gcloud -PassThru -WindowStyle Hidden -ArgumentList @(
  'compute','start-iap-tunnel','<instance>','22','--local-host-port=127.0.0.1:2222',
  '--project','<project>','--zone','<zone>')
# 로컬 포트가 열릴 때까지 기다린다.
while (-not (Test-NetConnection 127.0.0.1 -Port 2222 -InformationLevel Quiet -WarningAction SilentlyContinue)) { Start-Sleep 1 }

# 2. gcloud SDK에 포함된 plink로 기존 키와 고정한 호스트 키를 사용해 접속한다.
& "<Cloud SDK>\bin\sdk\plink.exe" -batch -ssh -P 2222 `
  -i "$env:USERPROFILE\.ssh\google_compute_engine.ppk" `
  -hostkey "<확인한 ED25519 SHA256 지문>" <user>@127.0.0.1 "<읽기 전용 명령>"

# 3. 점검이 끝나면 터널 프로세스 트리를 종료한다.
taskkill /PID $tunnel.Id /T /F
```

- `<user>`는 인스턴스 메타데이터 `ssh-keys` 중 로컬 `google_compute_engine.pub`와 지문이 같은 항목의 사용자 이름이다. 공개키 본문은 출력·기록하지 않고 `ssh-keygen -lf`로 지문만 비교한다.
- 호스트 키 지문은 저장소에 기록하지 않는다. VM을 다시 만들면 바뀌는 환경별 값이기 때문이다. 이번에는 사전에 대조할 기록이 없었다. 직렬 포트 출력과 guest attribute `hostkeys/`에서도 찾지 못했다. 그래서 IAP 터널을 통한 첫 접속에서 받은 키를 신뢰(TOFU, Trust On First Use)하고, 접속 후 VM 안의 `/etc/ssh/ssh_host_ed25519_key.pub` 지문과 일치하는지 확인했다. 같은 연결로 읽은 값이므로 독립 검증은 아니다. IAP 터널은 Google 인증을 거쳐 지정 VM의 22번 포트로만 연결되므로 중간자 위험은 낮다고 판단했다.
- 결과: 같은 VM에 원격 명령을 실행했고 `Updating project ssh metadata` 출력이 나타나지 않았다.

## 함께 만난 실패

- **Windows OpenSSH의 키 거부.** `ssh -i ~/.ssh/google_compute_engine`은 `UNPROTECTED PRIVATE KEY FILE`과 `OWNER RIGHTS` 접근 제어 목록(ACL, Access Control List) 항목을 이유로 키를 무시하고 `Permission denied (publickey)`로 끝났다. 키 파일 ACL을 바꾸지 않고 gcloud가 Windows에서 사용하는 plink와 `.ppk` 키를 그대로 사용했다.
- **터널 프로세스 실행·종료.** `Start-Process gcloud`는 터널을 열지 못했다. `gcloud.cmd`를 지정해야 한다. `Stop-Process`로 `gcloud.cmd`만 종료하면 실제 터널인 python 자식 프로세스가 남아 2222번 포트가 계속 열려 있었다. `taskkill /T`로 트리 전체를 종료한 뒤 포트가 닫힌 것을 확인했다.
- **PowerShell 5.1의 인수 손상.** `--command`에 `"`, `{{ }}`, 공백이 있는 날짜를 넣으면 `gcloud.ps1`로 전달되는 과정에서 인수가 분리돼 `unrecognized arguments`가 발생한다. 복잡한 점검 스크립트는 base64로 인코딩해 `echo <base64> | base64 -d | bash`로 실행한다.

## 남은 선택지

현재 조치는 이 절차를 따르는 점검자에게만 적용된다. 다른 사람이나 agent가 기본 `gcloud compute ssh`를 쓰면 같은 쓰기 시도가 다시 생긴다. 서버 측에서 막는 방법은 다음과 같으며, 운영 설정 변경이므로 human 결정 없이 적용하지 않는다.

- 인스턴스 메타데이터에 `block-project-ssh-keys=TRUE` 설정: 이 VM에 프로젝트 전체 키가 적용되지 않는다. 현재 프로젝트 키가 없어 영향이 없고 되돌리기 쉽다.
- OS Login 전환: SSH 키 관리를 IAM으로 옮긴다. 인증 경계가 바뀌므로 별도 결정이 필요하다.
