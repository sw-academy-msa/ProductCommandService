# 로컬 전용입니다. 운영 토픽의 파티션/보존/ACL 정책은 별도로 적용합니다.
$ErrorActionPreference = 'Stop'
$compose = Join-Path $PSScriptRoot '..\..\ProductQueryService\compose.kafka.yaml'
if (-not (Test-Path -LiteralPath $compose)) {
    throw 'ProductQueryService compose.kafka.yaml을 찾을 수 없습니다.'
}
foreach ($topic in @('stockdrop.product.public.v1', 'stockdrop.product.inquiries.v1', 'stockdrop.product.public.v1.DLT', 'stockdrop.product.inquiries.v1.DLT', 'stockdrop.product.published.v1')) {
    docker compose -f $compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic $topic --partitions 2 --replication-factor 1 --config max.message.bytes=4194304
    if ($LASTEXITCODE -ne 0) {
        throw "로컬 토픽 생성 실패: $topic"
    }
}
Write-Output '로컬 공개 상품/비공개 문의/DLT 토픽 준비 완료. 운영 ACL은 별도 설정해야 합니다.'
