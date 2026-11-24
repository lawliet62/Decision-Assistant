#requires -Version 5.1
$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'Issue-DecisionAssistantCertificate.ps1'
$temp = Join-Path ([IO.Path]::GetTempPath()) ('certificate-test-' + [guid]::NewGuid().ToString('n'))
New-Item -ItemType Directory -Path $temp | Out-Null
$mockCloud = Join-Path $temp 'gcloud.ps1'
$dummyKey = Join-Path $temp 'fixture.ppk'

function Get-Command {
    param($Name, $CommandType, $ErrorAction)
    if ($Name -cne 'gcloud.ps1' -or $CommandType -cne 'ExternalScript') {
        throw 'Unexpected command lookup in the fixture.'
    }
    [pscustomobject]@{ Source = $mockCloud }
}

function Resolve-DnsName {
    param($Name, $Type, [switch]$DnsOnly, [switch]$NoHostsFile)
    [pscustomobject]@{ Section = 'Answer'; Type = 'A'; IPAddress = '192.0.2.10' }
}

function Read-Host {
    throw 'The retry window must stop before the approval prompt.'
}

try {
    @'
$network = 'https://compute/projects/decision-assistant-prod/global/networks/default'
switch ("$($args[1])/$($args[2])") {
    'instances/describe' {
        @{id='11'; name='decision-assistant-prod'; status='RUNNING'; networkInterfaces=@(
            @{network=$network; accessConfigs=@(@{natIP='192.0.2.10'})}
        )} | ConvertTo-Json -Depth 8
    }
    'instances/list' {
        '[{"id":"11","networkInterfaces":[{"network":"https://compute/projects/decision-assistant-prod/global/networks/default"}],"tags":{"items":["app-only"]}}]'
    }
    'firewall-rules/describe' {
        @{id='22'; direction='INGRESS'; disabled=$true; network=$network; targetTags=@('app-only')
          allowed=@(@{IPProtocol='tcp'; ports=@('80','443')})} | ConvertTo-Json -Depth 8
    }
    'ssh/certoperator@decision-assistant-prod' {
        $command = $args | Where-Object { $_ -like '--command=*' }
        if ($command -notmatch '^--command=printf %s ([A-Za-z0-9+/=]+) \| base64 -d \| bash$') {
            throw 'Unexpected SSH command in the fixture.'
        }
        $remote = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Matches[1]))
        if (!$remote.Contains('release.py" verify') -or $remote -match 'compose (stop|start|up) ') {
            throw 'Only the read-only SSH preflight is allowed.'
        }
        if (Test-Path (Join-Path $PSScriptRoot 'ssh-error')) {
            [Console]::Error.WriteLine('Mock SSH connection error')
            exit 255
        }
        'proxy stopped'
        'ACME_RETRY_WINDOW_NOT_REACHED 2030-01-02T20:15:00Z'
    }
    default { throw 'Unexpected gcloud call, possibly a mutation.' }
}
exit 0
'@ | Set-Content -LiteralPath $mockCloud -Encoding UTF8
    Set-Content -LiteralPath $dummyKey -Value 'Synthetic fixture'
    $settings = @{
        SshUser = 'certoperator'
        SshPpk = $dummyKey
        SshHostKey = ('SHA256:' + ('A' * 43))
        ReleaseCommit = ('a' * 40)
        ReleaseRecordHash = ('b' * 64)
    }

    foreach ($execute in @($false, $true)) {
        $output = (& $scriptPath @settings -Execute:$execute 6>&1) -join "`n"
        if (!$output.Contains('2030-01-02 20:15:00 UTC / 2030-01-03 05:15:00 KST') -or
            $output.Contains('gcloud 실패')) {
            throw "Expected a normal retry wait with both timestamps. Output: $output"
        }
    }

    $errorMarker = Join-Path $temp 'ssh-error'
    Set-Content -LiteralPath $errorMarker -Value ''
    $failure = $null
    try { & $scriptPath @settings -Execute } catch { $failure = $_ }
    if (!$failure -or $failure.Exception.Message -notlike '*gcloud 실패 (255)*Mock SSH connection error*') {
        throw 'An actual SSH failure must remain an error.'
    }
    Write-Host 'PASS: retry wait in both modes, UTC/KST date rollover, and distinct SSH failure.'
} finally {
    foreach ($path in @($mockCloud, $dummyKey, (Join-Path $temp 'ssh-error'))) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path }
    }
    Remove-Item -LiteralPath $temp
}
