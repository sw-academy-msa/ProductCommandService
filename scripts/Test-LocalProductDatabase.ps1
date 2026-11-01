<#
별도 Command 인스턴스에서 Product 비밀번호 인증·자기 DB 쓰기·일반 DB 물리 분리를 검증합니다.
검증 테이블은 rollback하고 실제 비밀번호는 명령 인자나 출력으로 전달하지 않습니다.
#>
param([string]$ContainerName = 'stockdrop-command-postgres')
$ErrorActionPreference = 'Stop'
$workspacePath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envPath = Join-Path $workspacePath '.env'
$passwordLine = [IO.File]::ReadAllLines($envPath) | Where-Object { $_.StartsWith('DB_PASSWORD=') } | Select-Object -First 1
if (-not $passwordLine) { throw '로컬 DB_PASSWORD 설정이 없습니다.' }
$productPassword = $passwordLine.Substring('DB_PASSWORD='.Length)
$verificationSql = @'
SELECT current_database() AS database, current_user AS username, current_schema() AS schema;
DO $$
BEGIN
    IF current_database() <> 'product_command' OR current_user <> 'product' OR current_schema() <> 'product' THEN
        RAISE EXCEPTION 'Unexpected database identity';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)) THEN
        RAISE EXCEPTION 'Product role has elevated privileges';
    END IF;
    IF has_schema_privilege(current_user, 'public', 'CREATE') THEN
        RAISE EXCEPTION 'Product can create objects in public';
    END IF;
    IF has_database_privilege(current_user, 'product_command', 'CREATE') THEN
        RAISE EXCEPTION 'Product unexpectedly owns database-level DDL permissions';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_database WHERE datname='stockdrop')
       OR EXISTS (SELECT 1 FROM pg_roles WHERE rolname='account')
       OR EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='account') THEN
        RAISE EXCEPTION 'General Account DB resources unexpectedly share this Command instance';
    END IF;
END $$;
BEGIN;
CREATE TABLE product.__product_connection_probe (id integer PRIMARY KEY, value text NOT NULL);
INSERT INTO product.__product_connection_probe VALUES (1, 'probe');
UPDATE product.__product_connection_probe SET value = 'updated' WHERE id = 1;
DO $$
BEGIN
    IF (SELECT value FROM product.__product_connection_probe WHERE id = 1) <> 'updated' THEN
        RAISE EXCEPTION 'DML probe failed';
    END IF;
END $$;
DELETE FROM product.__product_connection_probe WHERE id = 1;
ROLLBACK;
SELECT to_regclass('product.__product_connection_probe') IS NULL AS probe_rolled_back;
SELECT 'PASS: dedicated Command instance, password authentication, scoped DDL/DML rollback' AS result;
'@
try {
    # 첫 줄은 셸에서 환경변수로 읽고 나머지만 psql이 소비합니다. 비밀번호가 argv에 들어가지 않습니다.
    $inputStream = "$productPassword`n$verificationSql"
    # Windows PowerShell의 native stderr를 즉시 출력하지 않고 종료코드와 함께 안전하게 처리합니다.
    $previousErrorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $result = $inputStream | & docker exec -i $ContainerName sh -c 'IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -X -h 127.0.0.1 -U product -d product_command -v ON_ERROR_STOP=1' 2>&1
        $verificationExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousErrorPreference }
    if ($verificationExitCode -ne 0) { throw 'Product DB 접속 또는 권한 검증이 실패했습니다. 비밀값 보호를 위해 원문을 출력하지 않습니다.' }
    # 방어적으로 비밀번호를 마스킹한 결과만 출력합니다.
    $result | ForEach-Object { $_.ToString().Replace($productPassword, '[REDACTED]') }
} finally {
    $productPassword = $null
    $passwordLine = $null
    $inputStream = $null
}
