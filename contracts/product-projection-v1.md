# Product Command → Query 이벤트 계약

PRODUCT_UPSERT는 envelope 전체를 직렬화한 UTF-8 본문 기준 최대 3 MiB를 허용합니다. 생산자 max.request.size 4 MiB에서 키·헤더·배치 오버헤드를 위한 여유를 두는 전송 정책이며 초과 시 업무 트랜잭션을 실패시켜 Outbox에 저장하지 않습니다. INQUIRY_UPSERT에는 이 상품 전용 크기 검사를 적용하지 않습니다. 이 제한만으로 별도 브로커·토픽·소비자 설정의 수신 한도 충족을 보장하지는 않습니다.

상품 승인·공개 FAQ 변경·재고 확보/확정/해제/안전한 만료 반환은 원본 변경과 같은 PostgreSQL 트랜잭션에서 product.outbox_events에 저장합니다. 초안과 반려는 공개 이벤트를 생성하지 않습니다. 현재 승인 취소·숨김 API는 없으며 PRODUCT_UNPUBLISHED를 발행한다고 가정하지 않습니다.

상품 토픽은 stockdrop.product.public.v1, 문의 생성/판매자 답변 토픽은 stockdrop.product.inquiries.v1입니다. key는 상품 UUID이며 소비자별 그룹을 분리합니다. envelope는 eventId, schemaVersion=1, type, aggregateId, aggregateVersion, occurredAt, product, inquiry입니다. Query의 ProjectionEvent 및 ProductSnapshot과 호환됩니다.

product.projection_sequences는 JPA 편집 버전과 독립된 단조 증가 버전을 보관합니다. 상품 쓰기는 기존 상품 행 잠금 안에서 스냅샷과 조회 버전을 함께 저장합니다. 문의는 별도 버전 계열입니다. 최초 수량, 임시 확보 수량, 확정 수량을 구분하며 실제 완판 시각을 별도 보존합니다. 공개 상품에는 출고/반품 주소, 연락처, 심사 메모, 계좌, 업로드 임시 권한을 포함하지 않습니다. 문의는 구매자 ID와 본문이 있으므로 토픽 접근 권한을 제한해야 합니다.

발행 복제본은 FOR UPDATE SKIP LOCKED로 Outbox 행을 분배합니다. 상품 행을 잠그지 않으며 Kafka 성공 응답 후 published_at을 기록합니다. 실패 시 attempts와 next_attempt_at을 갱신하고 최대 60초 간격으로 계속 재시도합니다. 상품 업무 트랜잭션은 Kafka 장애를 기다리지 않습니다. 발행 SQL 트랜잭션은 최대 20초이고 Producer 메타데이터/전송 대기도 제한합니다.

Kafka 성공 후 DB 완료 기록 전에 장애가 나면 같은 ID/본문이 재발행됩니다. 복제본 간 순서와 재시도 순서는 보장되지 않으므로 동일 상품 key와 Query 버전 비교/멱등 이력이 필요합니다. 발행 완료가 소비 완료를 의미하지 않습니다. SQL 상태·Kafka ack만으로 exactly-once를 주장하지 않습니다.

원문 이벤트/문의/전송 예외는 로그에 기록하지 않습니다. 실패 원인은 Kafka 메트릭과 연결 상태를 별도로 확인합니다. pending 수, 가장 오래된 created_at, attempts 및 Query 소비 지연/DLT 수를 모니터링해야 합니다. 무작정 완료 이벤트를 삭제하지 않습니다. 보관 기간과 조회 모델 재구축/기존 승인 상품 백필은 운영 계약으로 추가해야 합니다. 과거 상품에 대한 자동 전량 발행이나 기존 데이터 이전은 이번 구현에서 수행하지 않습니다.

실행 순서:

    docker compose -f ../ProductQueryService/compose.kafka.yaml up -d --wait
    powershell -ExecutionPolicy Bypass -File scripts/Initialize-LocalKafkaTopics.ps1

IDE에서 실행할 때 Command는 KAFKA_BOOTSTRAP_SERVERS=localhost:19092 및 PRODUCT_OUTBOX_PUBLISHER_ENABLED=true, Query는 같은 주소 및 QUERY_EVENTS_ENABLED=true로 설정하고 재시작합니다. Compose Kafka의 advertised.listeners는 호스트 IDE 실행용 localhost입니다. 앱까지 컨테이너에서 실행할 때는 컨테이너가 접근 가능한 내부/외부 listener를 따로 설정해야 하며 localhost를 그대로 사용하면 안 됩니다. 운영은 별도 Kafka EC2 주소·EBS·인증/ACL·모니터링을 구성합니다.
