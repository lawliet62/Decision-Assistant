#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z_][A-Za-z0-9_-]*$')]
    [string] $SshUser,
    [Parameter(Mandatory)]
    [string] $SshPpk,
    [Parameter(Mandatory)]
    [ValidatePattern('^SHA256:[A-Za-z0-9+/]{43}=?$')]
    [string] $SshHostKey,
    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string] $ReleaseCommit,
    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string] $ReleaseRecordHash,
    [ValidateRange(30, 300)]
    [int] $WaitSeconds = 120,
    [switch] $Execute
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$project = 'decision-assistant-prod'
$vm = 'decision-assistant-prod'
$zone = 'us-central1-f'
$domain = 'decision-assistant.dns.army'
$ruleName = 'decision-assistant-allow-web'
$gcloudPath = (Get-Command gcloud.ps1 -CommandType ExternalScript -ErrorAction Stop).Source
$engine = (Get-Process -Id $PID).Path
$key = Get-Item -LiteralPath $SshPpk
$keyPath = $key.FullName
if ($key.PSIsContainer -or $keyPath -match '\s|%' -or $keyPath -notlike '*.ppk') {
    throw '기존 PuTTY 키의 공백과 % 문자가 없는 .ppk 경로를 지정하세요.'
}

function Invoke-Gcloud {
    param([string[]] $Cli, [int] $Timeout = 45)

    # 별도 PowerShell 세션에서 gcloud를 실행해 대기 시간을 제한한다.
    $payload = @{ path = $gcloudPath; cli = $Cli } | ConvertTo-Json -Compress
    $encodedPayload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payload))
    $body = @'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$env:CLOUDSDK_CORE_DISABLE_PROMPTS = '1'
$call = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('__PAYLOAD__')) | ConvertFrom-Json
$cliArgs = [string[]]$call.cli
& $call.path @cliArgs
exit $LASTEXITCODE
'@
    $body = $body.Replace('__PAYLOAD__', $encodedPayload)
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $engine
    $info.Arguments = '-NoProfile -NonInteractive -EncodedCommand ' +
        [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($body))
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = [Text.Encoding]::UTF8
    $info.StandardErrorEncoding = [Text.Encoding]::UTF8
    $process = [Diagnostics.Process]::Start($info)
    try {
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit($Timeout * 1000)) {
            throw "gcloud 명령 대기 시간 초과 (${Timeout}초). 서버 작업은 완료됐을 수 있습니다."
        }
        if ($process.ExitCode -ne 0) {
            $detail = $stderr.GetAwaiter().GetResult().Trim()
            $failedOutput = $stdout.GetAwaiter().GetResult().Trim()
            throw "gcloud 실패 ($($process.ExitCode)): $detail $failedOutput"
        }
        $stdout.GetAwaiter().GetResult().Trim()
    } finally {
        if (!$process.HasExited) {
            try {
                & "$env:SystemRoot\System32\taskkill.exe" /PID $process.Id /T /F 2>$null | Out-Null
            } catch {
                Write-Warning '로컬 gcloud 프로세스 종료를 확인하지 못했습니다.'
            }
        }
        $process.Dispose()
    }
}

function Invoke-Vm {
    param([string] $Shell, [int] $Timeout = 45)

    $setup = @'
set -euo pipefail
deploy="$operator_home/deploy"
bundle="$operator_home/first-gcp-$release_short"
cd "$operator_home"
runtime=/etc/decision-assistant/runtime.conf
docker_local() {
  sudo -n -- docker --context default "$@"
}
compose() {
  docker_local compose --project-name decision-assistant \
    --env-file "$runtime" --env-file "$bundle/images.env" \
    -f "$deploy/compose.yaml" -f "$deploy/compose.release.yaml" "$@"
}
stopped() {
  local ids
  ids=$(compose ps -q "$1") || return 1
  test -z "$ids"
}
lock_operation() {
  umask 077
  exec 9>"$operator_home/.decision-assistant-cert.lock"
  flock -n 9
}
'@
    $script = "operator_home='/home/$SshUser'`nrelease_short='$($ReleaseCommit.Substring(0, 7))'`n" + $setup + "`n" + $Shell
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($script -replace "`r`n", "`n")))
    Invoke-Gcloud -Timeout $Timeout -Cli @(
        'compute', 'ssh', "$SshUser@$vm", "--project=$project", "--zone=$zone",
        '--tunnel-through-iap', '--plain', '--ssh-flag=-batch', '--ssh-flag=-i',
        "--ssh-flag=$keyPath", '--ssh-flag=-hostkey', "--ssh-flag=$SshHostKey",
        "--command=printf %s $encoded | base64 -d | bash"
    )
}

function Get-WebRule {
    $result = Invoke-Gcloud -Cli @('compute', 'firewall-rules', 'describe', $ruleName, "--project=$project", '--format=json')
    $result | ConvertFrom-Json
}

$instance = (Invoke-Gcloud -Cli @('compute', 'instances', 'describe', $vm, "--project=$project", "--zone=$zone", '--format=json')) | ConvertFrom-Json
$rule = Get-WebRule
$ruleId = $rule.id
if ($instance.name -cne $vm -or $instance.status -cne 'RUNNING' -or @($instance.networkInterfaces).Count -ne 1) {
    throw '예상한 단일 인터페이스 운영 VM이 아닙니다.'
}
$nic = $instance.networkInterfaces[0]
if ($rule.direction -cne 'INGRESS' -or $rule.disabled -ne $true -or $rule.network -cne $nic.network) {
    throw '웹 규칙이 해당 VM 네트워크의 비활성 ingress 규칙인지 확인하세요.'
}
$tagProperty = $rule.PSObject.Properties['targetTags']
$saProperty = $rule.PSObject.Properties['targetServiceAccounts']
if (!$tagProperty -or !$tagProperty.Value -or ($saProperty -and $saProperty.Value) -or
    $nic.network -notmatch "/projects/$project/global/networks/[^/]+$") {
    throw '이 스크립트는 전용 프로젝트 VPC의 VM 태그 지정 규칙만 처리합니다.'
}
$targets = @($tagProperty.Value)
$all = (Invoke-Gcloud -Cli @('compute', 'instances', 'list', "--project=$project", '--format=json')) | ConvertFrom-Json
$matches = @($all | Where-Object {
    $interfaces = @($_.networkInterfaces | Where-Object { $_.network -ceq $rule.network })
    $tags = $_.PSObject.Properties['tags']
    $interfaces.Count -gt 0 -and $tags -and @($tags.Value.items | Where-Object { $_ -cin $targets }).Count -gt 0
})
if ($matches.Count -ne 1 -or $matches[0].id -cne $instance.id) {
    throw '웹 규칙의 대상이 지정한 VM 한 대로 한정되지 않습니다.'
}
$allowed = @($rule.allowed)
if ($allowed.Count -ne 1 -or $allowed[0].IPProtocol -cne 'tcp' -or
    ((@($allowed[0].ports) | Sort-Object) -join ',') -cne '443,80') {
    throw '웹 규칙은 TCP 80 및 443만 허용해야 합니다.'
}
$publicIp = @($nic.accessConfigs | ForEach-Object { $_.natIP })
$resolved = @(Resolve-DnsName -Name $domain -Type A_AAAA -DnsOnly -NoHostsFile |
    Where-Object { $_.Section -eq 'Answer' -and $_.Type -in 'A', 'AAAA' } |
    Sort-Object -Property IPAddress -Unique)
if ($publicIp.Count -ne 1 -or $resolved.Count -ne 1 -or $resolved[0].Type -ne 'A' -or $resolved[0].IPAddress -cne $publicIp[0]) {
    throw '운영 도메인이 VM의 단일 공개 IPv4와 일치하지 않습니다. AAAA가 있으면 별도로 확인하세요.'
}

$preflight = @'
command -v openssl >/dev/null
command -v timeout >/dev/null
command -v flock >/dev/null
test "$(sudo -n -- stat -c '%u:%g:%a' "$runtime")" = 0:0:600
test "$(docker_local context inspect default --format '{{.Endpoints.docker.Host}}')" = unix:///var/run/docker.sock
printf '%s  %s\n' __RELEASE_HASH__ "$bundle/release.json" | sha256sum --check --status
sudo -n -- python3 "$deploy/release.py" verify "$bundle" "$runtime"
python3 -c 'import json,sys; assert json.load(open(sys.argv[1]))["commit"] == sys.argv[2]' "$bundle/release.json" __COMMIT__
compose config --format json | python3 -c 'import json,sys; c=json.load(sys.stdin)["services"]; assert c["proxy"]["environment"]["PUBLIC_HOST"] == sys.argv[1]; assert all(not c[s].get("ports") for s in ("frontend","backend","postgres"))' __DOMAIN__
for service in postgres backend frontend; do
  id=$(compose ps -q "$service")
  test -n "$id"
  running=$(docker_local inspect -f '{{.State.Running}}' "$id")
  published=$(docker_local port "$id")
  test "$running" = true
  test -z "$published"
  printf '%s running\n' "$service"
done
stopped proxy
printf 'proxy stopped\n'
logs=$(compose logs --no-color --tail 300 proxy)
retry=$(grep -Ei 'acme\.zerossl\.com' <<<"$logs" | grep -Eio 'retry after [0-9][0-9T :.+Z-]{8,30}( UTC)?' | tail -n 1 || true)
if [[ -n "$retry" ]]; then
  if until=$(date -u -d "${retry:12}" +%s 2>/dev/null); then
    if (( until > $(date -u +%s) )); then
      retry_at=$(date -u -d "@$until" +%Y-%m-%dT%H:%M:%SZ)
      printf 'ACME_RETRY_WINDOW_NOT_REACHED %s\n' "$retry_at"
      exit 0
    fi
  fi
  printf 'Last recorded %s\n' "$retry"
fi
'@
$preflight = $preflight.Replace('__RELEASE_HASH__', $ReleaseRecordHash).Replace('__COMMIT__', $ReleaseCommit).Replace('__DOMAIN__', $domain)
foreach ($line in ((Invoke-Vm -Shell $preflight) -split '\r?\n')) {
    if ($line -cmatch '^ACME_RETRY_WINDOW_NOT_REACHED(?: |$)') {
        $culture = [Globalization.CultureInfo]::InvariantCulture
        $retryAt = [DateTimeOffset]::ParseExact(
            $line.Substring('ACME_RETRY_WINDOW_NOT_REACHED'.Length).Trim(),
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            $culture,
            [Globalization.DateTimeStyles]::AssumeUniversal
        )
        $utc = $retryAt.UtcDateTime.ToString('yyyy-MM-dd HH:mm:ss', $culture)
        $kst = $retryAt.ToOffset([TimeSpan]::FromHours(9)).ToString('yyyy-MM-dd HH:mm:ss', $culture)
        Write-Host "ACME 재시도 대기 | $utc UTC / $kst KST | 방화벽·컨테이너 변경 없이 종료합니다."
        return
    }
    if ($line) { Write-Host $line }
}
Write-Host "$project / $zone / $vm / $domain / web disabled"
if (!$Execute) {
    Write-Host '사전 점검만 완료했습니다. 실행하려면 -Execute를 지정하세요.'
    return
}
$approval = "ISSUE $domain"
if ((Read-Host "frontend 임시 정지 및 웹 포트 일시 개방에 동의하면 '$approval' 입력") -cne $approval) {
    throw '승인 문구가 일치하지 않아 중단했습니다.'
}

$touched = $false
$remoteUncertain = $false
$enableUncertain = $false
$closed = $false
$proxyStopped = $false
try {
    $fresh = Get-WebRule
    if ($fresh.id -cne $ruleId -or $fresh.disabled -ne $true -or
        ($fresh | ConvertTo-Json -Depth 20 -Compress) -cne ($rule | ConvertTo-Json -Depth 20 -Compress)) {
        throw '사전 점검 이후 웹 규칙이 변경됐습니다.'
    }
    $touched = $true
    $remoteUncertain = $true
    Invoke-Vm -Shell 'lock_operation; stopped proxy; compose stop frontend; stopped frontend' | Out-Null
    $remoteUncertain = $false
    $enableUncertain = $true
    Invoke-Gcloud -Cli @('compute', 'firewall-rules', 'update', $ruleName, "--project=$project", '--no-disabled', '--quiet') | Out-Null
    $enableUncertain = $false
    $issue = @'
lock_operation
stopped frontend
started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
compose up -d --no-deps --no-build --pull never proxy
deadline=$((SECONDS + __WAIT__))
# Compare the served leaf with the public certificate saved under the ZeroSSL issuer.
zerossl_cert=/data/caddy/certificates/acme.zerossl.com-v2-dv90/__DOMAIN__/__DOMAIN__.crt
while (( SECONDS < deadline )); do
  if stored=$(compose exec -T proxy cat "$zerossl_cert" 2>/dev/null | openssl x509 -noout -fingerprint -sha256 2>/dev/null) &&
     chain=$(timeout 6 openssl s_client -connect 127.0.0.1:443 -servername __DOMAIN__ -verify_hostname __DOMAIN__ -verify_return_error </dev/null 2>/dev/null); then
    served=$(printf '%s\n' "$chain" | openssl x509 -noout -fingerprint -sha256)
    if [[ "$served" == "$stored" ]]; then
      printf '%s\n' "$chain" | openssl x509 -noout -issuer -enddate -fingerprint -sha256
      echo 'ACME_ISSUED'
      exit 0
    fi
  fi
  logs=$(compose logs --no-color --since "$started" proxy 2>&1)
  zerossl_logs=$(grep -Ei '(acme|api)\.zerossl\.com' <<<"$logs" || true)
  if grep -Eiq 'rateLimited|too many certificates|HTTP 429|status code 429' <<<"$zerossl_logs"; then
    echo 'ACME_RATE_LIMIT'
    grep -Eio 'retry after [0-9][0-9T :.+Z-]{8,30}( UTC)?' <<<"$zerossl_logs" | tail -n 1 || true
    exit 0
  fi
  sleep 3
done
echo 'ACME_WAIT_TIMEOUT'
exit 0
'@
    $remoteUncertain = $true
    $result = Invoke-Vm -Timeout ($WaitSeconds + 45) -Shell ($issue.Replace('__DOMAIN__', $domain).Replace('__WAIT__', [string]$WaitSeconds))
    Write-Host $result
    if ($result -notmatch '(?m)^(ACME_ISSUED|ACME_RATE_LIMIT|ACME_WAIT_TIMEOUT)\r?$') {
        throw '발급 작업의 완료 상태를 확인하지 못했습니다.'
    }
    $remoteUncertain = $false
    if ($result -notmatch '(?m)^ACME_ISSUED\r?$') {
        throw '운영 인증서 발급을 확인하지 못했습니다.'
    }
} catch {
    Write-Warning "인증서 작업 실패: $_"
    throw
} finally {
    if ($touched) {
        for ($attempt = 1; $attempt -le 2; $attempt++) {
            try {
                if ((Get-WebRule).id -cne $ruleId) { throw '웹 규칙 ID가 변경됐습니다.' }
                Invoke-Gcloud -Cli @('compute', 'firewall-rules', 'update', $ruleName, "--project=$project", '--disabled', '--quiet') | Out-Null
                break
            } catch {
                Write-Warning "웹 규칙 재차단 시도 $attempt 실패: $_"
            }
        }
        try {
            Invoke-Vm -Shell 'lock_operation; compose stop proxy; stopped proxy' | Out-Null
            $proxyStopped = $true
        } catch {
            Write-Warning "Caddy 정지 확인 실패: $_"
        }
        try {
            $finalRule = Get-WebRule
            $closed = $finalRule.id -ceq $ruleId -and $finalRule.disabled -eq $true
            Write-Host "웹 규칙 별도 조회 disabled=$($finalRule.disabled), id=$($finalRule.id)"
        } catch {
            Write-Warning "최종 웹 규칙 조회 실패: $_"
        }
        if ($closed -and $proxyStopped -and !$remoteUncertain -and !$enableUncertain) {
            try {
                Write-Host (Invoke-Vm -Shell 'lock_operation; stopped proxy; compose start frontend; id=$(compose ps -q frontend); test -n "$id"; echo "frontend running; proxy stopped"')
            } catch {
                throw "frontend 재개 결과를 확인하지 못했습니다. 웹 규칙과 컨테이너 상태를 수동으로 확인하세요. $_"
            }
        } else {
            throw "자동 복구 보류: 웹 규칙 비활성 조회=$closed, Caddy 정지 확인=$proxyStopped, 원격 작업 미확인=$remoteUncertain, 방화벽 활성 작업 미확인=$enableUncertain. 수동 복구가 필요합니다."
        }
    }
}
