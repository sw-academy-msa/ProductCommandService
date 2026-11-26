# 판매 등록 광고 신청

광고 OFF는 기존 심사 제출을 유지한다. ON은 본인 SELLER의 서버 원본 판매가와 옵션 최초 수량 합계로 `floor(price * quantity / 20)`을 한 번 계산한다. BigInteger로 중간 곱셈을 수행하고 저장 가능한 원 단위 정수로 변환한다. 최소 1,000원 미만은 신청할 수 없고 세금을 더하지 않는다. 배송비, 쿠폰, 확보 및 판매 수량은 산정에 넣지 않는다.

신규 경로는 `/api/v1/seller/products/{id}/advertising-quote`, `/advertising-submissions`, `/advertising-submissions/{applicationId}`, `/advertising-submissions/{applicationId}/abandon`이다. POST에는 Idempotency-Key UUID가 필요하다. 준비 요청은 expectedVersion, termsVersion=advertising-v1, advertisingAgreed=true를 받으며 202는 결제 준비 접수만 뜻한다. GET은 no-store이며 서버 capability와 최소 금액을 제공한다.

신청을 만들기 전에 기존 일반 제출과 같은 검증을 실행한다. 결제 준비 중에는 상품 단계 수정, 이미지 변경, 삭제, 다른 제출을 금지한다. 중단 요청은 취소 의도를 Outbox에 남기고 금융 종료 결과까지 잠금을 유지한다. PAID 및 SUBMITTED 회차의 판매자 중단은 허용하지 않는다.

명령은 `product.advertising.commands.v1`, 결과는 `payment.advertising.results.v1`, key는 applicationId다. envelope와 금융 계약은 PaymentService의 contracts/advertising-flow.md를 따른다. Product 명령 버전은 준비 1, 최초 취소 2이며 같은 취소를 다시 발행하지 않는다. 결과는 식별자, quoteHash, 원본 금액, KRW, V1 및 증가하는 resultVersion을 확인한다. 같은 이벤트 ID의 다른 본문은 거절하고 낡은 결과는 금융 투영을 변경하지 않는다.

PAID를 확인한 서버는 현재 회차, 동결 상품 버전, 기한, 탈퇴 gate 및 최초 제출 검증을 확인한 뒤 IN_REVIEW와 SUBMITTED를 원자적으로 저장한다. 현재 Account의 승인 판매자 자격 상실 경로인 계정 탈퇴는 기존 영속 gate로 직렬화하며 사용자 JWT를 이벤트나 DB에 보관하지 않는다. 영구 제출 실패는 ABORTED와 SUBMISSION_FAILED 취소 명령을 같은 트랜잭션으로 기록한다. 일시적인 정책 또는 DB 오류는 소비 재시도로 남긴다.

최초 심사 반려는 PRODUCT_REJECTED 전액 취소를 요청한다. 취소 확정 전 새 광고 신청은 금지하지만 광고 OFF 재심사는 허용한다. 이전 회차는 삭제하지 않고 새 신청에는 새 applicationId와 submissionId를 발급한다. 승인된 상품의 수정 심사에는 광고 재산정 및 광고 취소를 추가하지 않는다.

관리 목록은 광고 회차를 배치 조회하고 상세와 동일한 advertising 응답을 제공한다. 광고 미신청은 requested=false 및 금액과 금융 상태 null이다. 승인한 PAID 회차는 fulfillmentStatus=READY가 되며 쿠폰은 운영자가 수동 생성한다. 이번 변경은 쿠폰 생성이나 SNS 게시를 자동화하지 않는다.

V6__create_advertising_applications.sql은 기존 상품에 광고 기본 false와 신규 회차 및 영수증을 추가한다. 상품별 활성 회차의 부분 유일 인덱스가 중복 결제를 막는다. 공개 ProductQuery 이벤트 및 응답에는 금융 필드를 추가하지 않는다.

기본 OFF 환경 변수와 예정 SSM 이름은 다음과 같다. 실제 SSM 조회, 쓰기 및 배포는 수행하지 않는다.

- PRODUCT_ADVERTISING_ENABLED: `/stockdrop/prod/product-command-service/PRODUCT_ADVERTISING_ENABLED`
- PRODUCT_ADVERTISING_CONSUMER_ENABLED: `/stockdrop/prod/product-command-service/PRODUCT_ADVERTISING_CONSUMER_ENABLED`

Payment V5와 전용 토픽 및 ACL을 준비하고 Payment와 Product V6를 기능 OFF로 배포한다. 양쪽 consumer, Payment processing 및 기존 Outbox publisher를 먼저 활성화한 뒤 Payment 접수, Product 접수, FE 순으로 활성화한다. 롤백은 신규 접수와 FE만 먼저 OFF하고 기존 회차 결과 소비, Outbox와 금융 복구는 drain까지 유지한다.
