<#
Eureka 등록·health·공개 JWKS 경유·미인증/잘못된 서명 거부를 실제 로컬 서비스에서 확인합니다.
성공 로그인 토큰을 만들거나 Account 개인키를 읽지 않습니다. 서명 거부용 임시 키·토큰은 메모리에만 둡니다.
#>
$ErrorActionPreference = 'Stop'
function Assert-HttpStatus([string]$Uri, [int]$Expected, [hashtable]$Headers = @{}) {
    try { $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Headers $Headers -TimeoutSec 10 }
    catch {
        $response = $_.Exception.Response
        if ($null -eq $response) { throw ('Connection failed: ' + $Uri) }
    }
    if ([int]$response.StatusCode -ne $Expected) { throw ('Unexpected status at ' + $Uri) }
    Write-Output ('PASS HTTP ' + $Expected + ' ' + $Uri)
}

# 레지스트리에는 내부 주소만 있으며 출력은 서비스명·상태·포트로 제한합니다.
$registry = Invoke-RestMethod -Uri 'http://localhost:8761/eureka/apps' -Headers @{Accept='application/json'} -TimeoutSec 10
foreach ($expected in @(@{Name='ACCOUNT-SERVICE';Port=8080}, @{Name='GATEWAY';Port=8000}, @{Name='PRODUCT-COMMAND-SERVICE';Port=8081})) {
    $instances = @($registry.applications.application | Where-Object { $_.name -eq $expected.Name } | ForEach-Object { $_.instance })
    if (-not ($instances | Where-Object { $_.status -eq 'UP' -and [int]$_.port.'$' -eq $expected.Port })) {
        throw ('Expected UP instance missing: ' + $expected.Name)
    }
    Write-Output ('PASS Eureka ' + $expected.Name + ':' + $expected.Port + ' UP')
}
foreach ($portNumber in @(8000,8080,8081)) {
    $health = Invoke-RestMethod -Uri ('http://localhost:' + $portNumber + '/actuator/health') -TimeoutSec 10
    if ($health.status -ne 'UP') { throw ('Health is not UP: ' + $portNumber) }
    Write-Output ('PASS health ' + $portNumber + ' UP')
}
$publicKeys = Invoke-RestMethod -Uri 'http://localhost:8000/.well-known/jwks.json' -TimeoutSec 10
if (@($publicKeys.keys).Count -lt 1) { throw 'Public JWKS route returned no keys.' }
Write-Output 'PASS Gateway -> Account public JWKS route'

foreach ($baseUri in @('http://localhost:8000','http://localhost:8081')) {
    Assert-HttpStatus ($baseUri + '/api/v1/seller/products') 401
    Assert-HttpStatus ($baseUri + '/api/v1/admin/product-reviews') 401
}

function Convert-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
}
$rsa = New-Object Security.Cryptography.RSACng -ArgumentList 2048
try {
    # 실제 Account 공개키 kid를 지정하되 다른 임시 키로 서명해 각 서비스의 서명 거부를 검사합니다.
    $headerJson = @{alg='RS256';typ='JWT';kid=$publicKeys.keys[0].kid} | ConvertTo-Json -Compress
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $claimsJson = @{iss='http://localhost:8080';aud=@('stockdrop-api');sub=[guid]::NewGuid().ToString();iat=$now;exp=($now+120);token_use='access';roles=@('SELLER')} | ConvertTo-Json -Compress
    $unsigned = (Convert-Base64Url ([Text.Encoding]::UTF8.GetBytes($headerJson))) + '.' + (Convert-Base64Url ([Text.Encoding]::UTF8.GetBytes($claimsJson)))
    $signature = $rsa.SignData([Text.Encoding]::UTF8.GetBytes($unsigned), [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)
    $invalidSignatureToken = $unsigned + '.' + (Convert-Base64Url $signature)
    foreach ($baseUri in @('http://localhost:8000','http://localhost:8081')) {
        Assert-HttpStatus ($baseUri + '/api/v1/seller/products') 401 @{Authorization=('Bearer ' + $invalidSignatureToken)}
    }
    Write-Output 'PASS invalid RS256 signature rejected by Gateway and Product (not a valid-login success test)'
} finally {
    $rsa.Dispose()
    $invalidSignatureToken = $null
    $signature = $null
    $unsigned = $null
}
