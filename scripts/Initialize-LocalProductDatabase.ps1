<#
일반 DB와 분리된 로컬 Command PostgreSQL을 준비하고 Product 연결만 전환합니다.
기존 일반 DB·역할은 삭제하지 않으며 다른 .env 설정과 Product 비밀번호를 유지합니다.
관리자 비밀번호는 Git 제외 파일에 저장하고 비밀값·SQL 원문은 출력하지 않습니다.
#>
param([ValidateRange(1024,65535)][int]$HostPort = 5434)
$ErrorActionPreference = 'Stop'
$workspacePath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envPath = Join-Path $workspacePath '.env'
$secretDirectory = Join-Path $workspacePath '.secrets'
$adminSecretPath = Join-Path $secretDirectory 'command-postgres-admin-password'
$composePath = Join-Path $workspacePath 'compose.command-db.yaml'
$utf8 = New-Object Text.UTF8Encoding($false)

function New-LocalPassword {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return (-join ($bytes | ForEach-Object { $_.ToString('x2') }))
}
function Invoke-DockerChecked([string[]]$Arguments, [string]$InputText = $null) {
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        if ($null -eq $InputText) { $captured = & docker @Arguments 2>&1 }
        else { $captured = $InputText | & docker @Arguments 2>&1 }
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    if ($code -ne 0) { throw 'Command DB 준비 작업이 실패했습니다. 기존 DB·비밀 파일은 보존하며 원문 출력 없이 상태를 확인해야 합니다.' }
}

$originalEnv = $null
if (Test-Path -LiteralPath $envPath) {
    $originalEnv = [IO.File]::ReadAllText($envPath)
    $passwordMatches = [regex]::Matches($originalEnv, '(?m)^DB_PASSWORD=([^\r\n]*)')
    if ($passwordMatches.Count -ne 1 -or [string]::IsNullOrWhiteSpace($passwordMatches[0].Groups[1].Value)) {
        throw '기존 .env의 DB_PASSWORD를 안전하게 식별할 수 없습니다. 덮어쓰지 않습니다.'
    }
    $productPassword = $passwordMatches[0].Groups[1].Value
    $userMatch = [regex]::Match($originalEnv, '(?m)^DB_USERNAME=([^\r\n]*)')
    if ($userMatch.Success -and $userMatch.Groups[1].Value -ne 'product') { throw 'Product 이외의 DB 계정 설정이 있어 자동 전환하지 않습니다.' }
} else {
    $productPassword = New-LocalPassword
    $originalEnv = "# 로컬 전용 비밀값입니다. 커밋하지 않습니다.`nSPRING_PROFILES_ACTIVE=local`nDB_USERNAME=product`nDB_PASSWORD=$productPassword`n"
    # 중간 실패 시에도 앱 계정 비밀번호를 잃지 않도록 생성 전에 저장합니다.
    $stream = [IO.File]::Open($envPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $data = $utf8.GetBytes($originalEnv); $stream.Write($data,0,$data.Length) } finally { $stream.Dispose() }
}

$volumes = @(& docker volume ls --format '{{.Name}}')
if ($LASTEXITCODE -ne 0) { throw 'Docker 볼륨을 확인할 수 없습니다.' }
if (-not (Test-Path -LiteralPath $adminSecretPath)) {
    if ($volumes -contains 'stockdrop-command-postgres-data') {
        throw 'Command DB 볼륨은 있지만 관리자 비밀 파일이 없습니다. 새 비밀번호를 덮어 생성하지 않습니다.'
    }
    [IO.Directory]::CreateDirectory($secretDirectory) | Out-Null
    $stream = [IO.File]::Open($adminSecretPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $data = $utf8.GetBytes((New-LocalPassword)); $stream.Write($data,0,$data.Length) } finally { $stream.Dispose() }
}

$previousPort = $env:COMMAND_DB_PORT
try {
    $env:COMMAND_DB_PORT = [string]$HostPort
    Invoke-DockerChecked -Arguments @('compose','-f',$composePath,'config','--quiet')
    Invoke-DockerChecked -Arguments @('compose','-f',$composePath,'up','-d','--wait')
    $template = [IO.File]::ReadAllText((Join-Path $workspacePath 'docker/postgres/create-product.sql'))
    $literalPassword = "'" + $productPassword.Replace("'", "''") + "'"
    # SQL 리터럴을 이스케이프해 표준 입력으로만 전달하며 비밀번호는 파일/명령 인자에 복사하지 않습니다.
    $sqlInput = "SET log_statement='none';`nSET log_min_error_statement='panic';`nSET standard_conforming_strings=on;`n" + $template.Replace(":'product_password'", $literalPassword)
    Invoke-DockerChecked -Arguments @('exec','-i','stockdrop-command-postgres','sh','-c','exec psql -X -U $POSTGRES_USER -d $POSTGRES_DB -v ON_ERROR_STOP=1') -InputText $sqlInput

    # DB 준비 성공 후 URL/포트만 갱신하고 기존 사용자 설정의 나머지는 보존합니다.
    if ([IO.File]::ReadAllText($envPath) -ne $originalEnv) { throw '작업 중 .env가 변경되어 자동 덮어쓰기를 중단합니다. 새 DB는 보존합니다.' }
    $targetUrl = 'jdbc:postgresql://localhost:' + $HostPort + '/product_command'
    $updatedEnv = $originalEnv
    foreach ($setting in @(@{Key='DB_URL';Value=$targetUrl}, @{Key='COMMAND_DB_PORT';Value=[string]$HostPort})) {
        $pattern = '(?m)^' + $setting.Key + '=[^\r\n]*'
        if ([regex]::IsMatch($updatedEnv,$pattern)) { $updatedEnv = [regex]::Replace($updatedEnv,$pattern,($setting.Key + '=' + $setting.Value)) }
        else { $updatedEnv = $updatedEnv.TrimEnd() + "`n" + $setting.Key + '=' + $setting.Value + "`n" }
    }
    $pendingPath = $envPath + '.pending'
    if (Test-Path -LiteralPath $pendingPath) { throw '기존 .env.pending 파일이 있어 덮어쓰지 않습니다.' }
    [IO.File]::WriteAllText($pendingPath,$updatedEnv,$utf8)
    Move-Item -LiteralPath $pendingPath -Destination $envPath -Force
    Write-Output ('Command PostgreSQL ready on localhost:' + $HostPort + '; Product DB and credentials configured. Secrets withheld.')
} finally {
    $env:COMMAND_DB_PORT = $previousPort
    $productPassword = $null
    $literalPassword = $null
    $sqlInput = $null
    $originalEnv = $null
    $updatedEnv = $null
}