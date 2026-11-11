productCommandSerivce 입니다.

# STOCKDROP Product Command Service

Java 21 / Spring Boot 4.1.1 / Spring Cloud 2025.1.4 / Gradle 9.8.2.
AccountService의 초기 구성과 스타일을 참고한 MVC 서비스입니다.
상품 초안·옵션 저장, 심사 제출·승인·반려, 이미지 업로드 권한·검증·연결과 재고 확보·확정·해제 API를 구현했습니다.
판매자 등록은 단계별 PUT으로 저장하며 브랜드·자유 상세 정보·구조화된 옵션·Account 배송 주소 스냅샷·판매자 확인 FAQ를 보존합니다.
본인 상품 관리 목록·운영자 심사 목록·상품 문의·판매자 답변도 제공합니다. AI 분석 요청·결과는 별도 AIService와 Kafka로 연결하며 실제 모델 호출은 별도로 활성화합니다.
승인 상품은 판매 시작 전 별도 수정 심사본을 제출하며 기존 승인본의 공개·판매를 유지합니다. 상품별 배송 주소 선택과 KST 판매 시작 월별 관리·재고 집계는 [수정 심사와 판매 관리 API](contracts/product-revisions.md)를 참고합니다.
상품은 검사 후 승인하며 승인 전에는 재고를 확보할 수 없습니다. 승인·재고·FAQ·문의 변경은 Outbox와 Kafka를 통해 별도 Product Query에 전달할 수 있습니다. 두 앱의 발행/소비 활성화가 필요합니다.
재고 확보는 5분이며, Order의 결제 진행 전이와 실제 승인 시각에 따라 확정·해제합니다.
API 목록·설정·내부 인증 계약·검증 범위는 [상품·재고 API 안내](docs/product-command-api.md)를 확인합니다.
[상품 등록 7단계 프런트 연동 안내](docs/product-registration.md)에 단계별 요청 방법을 정리했습니다.

## 로컬 실행

```powershell
# 시스템 기본 Java가 17일 수 있으므로 설치된 Java 21 경로를 지정합니다.
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
# 별도 로컬 Command PostgreSQL(5434)·영속 볼륨과 Product DB를 준비합니다.
# 기존 일반 DB(5433)의 데이터는 이 스크립트가 삭제하지 않습니다.
.\scripts\Initialize-LocalProductDatabase.ps1
.\gradlew.bat bootRun

# 전체 테스트는 격리된 PostgreSQL·S3Mock 컨테이너를 사용하므로 Docker가 필요합니다. AWS·실제 Account는 필요하지 않습니다.
.\gradlew.bat test bootJar
```

- 서버: http://localhost:8081
- 상태: http://localhost:8081/actuator/health
- Swagger(local): http://localhost:8081/swagger-ui.html
- 실제 S3 없이 실행할 수 있으며 이미지 연동은 기본 비활성입니다. 로컬 업로드는 `compose.images.yaml`과 `.env.example`의 이미지 설정을 사용합니다.
- 이미지 형식은 JPEG·PNG·WebP, 장당 상한 10MiB, 상품당 최대 10장입니다. 장당 상한은 설정으로 변경할 수 있습니다.
- 카테고리 4개(의류, 패션잡화, 문구, 일반 생활잡화)와 점검 기준 16개는 Flyway가 관리합니다. `PRODUCT_CATEGORIES`는 더 이상 사용하지 않습니다.
- 판매자 업무는 기존 Account `/api/v1/sellers/me`로 최신 자격을 확인합니다. Account 장애 시 상품 업무를 차단합니다.
- 내부 재고 API는 별도 scope와 `ORDER_SERVICE_SUBJECT`가 필요합니다. Account/Order의 서비스 토큰 발급 계약은 아직 구현되지 않았으므로 실제 Order HTTP 연동 완료를 의미하지 않습니다.
- 초기화 스크립트는 기존 Product 비밀번호와 나머지 `.env` 설정을 유지하고 DB 주소·포트만 전환합니다. 기존 DB·역할을 삭제하거나 비밀번호를 재설정하지 않습니다.
- 일반 PostgreSQL은 5433, 별도 Command PostgreSQL은 5434입니다. Product는 후자의 `product_command` DB·`product` 계정을 사용합니다.
- Command DB 관리자 비밀번호는 Git 제외 `.secrets/` 파일에서 읽습니다. 컨테이너 데이터는 `stockdrop-command-postgres-data` 볼륨에 유지됩니다.
- Product DB 내부 업무 스키마 이름은 `product`입니다. [DB 연결 안내](docs/shared-database.md)를 확인합니다.
- 구매 Gateway 연동을 위해 Eureka 등록을 기본 활성화합니다. 단독 개발 시 `EUREKA_CLIENT_ENABLED=false`로 끌 수 있습니다.
- Account가 없어도 시작할 수 있으나 Bearer 검증에는 Account JWKS 접근이 필요합니다.
- 일반 Gateway의 Product Command 라우트는 제거한 상태입니다. 외부 상품 변경·심사는 PurchaseGateway의 기존 판매자·운영자 경로를 사용하고 내부 재고 경로는 외부에 공개하지 않습니다.
- 구매자 상품 문의는 PurchaseGateway가 `POST /api/v1/products/{id}/inquiries`만 라우팅합니다. 상품 GET 조회를 Command로 연결하지 않습니다.

## 문서

- [Outbox 저장·Kafka 발행·Query 이벤트 계약과 로컬 실행](contracts/product-projection-v1.md)
- [Order 내부 확보 응답 스냅샷 확장과 책임 경계](contracts/order-stock-snapshot.md)
- 상품 승인·재고·FAQ·문의 변경은 업무와 같은 트랜잭션으로 Outbox에 저장합니다. Kafka 발행은 PRODUCT_OUTBOX_PUBLISHER_ENABLED=true로 별도 활성화합니다.
- 기존 업무 스키마는 개발 ddl-auto=update, 운영 validate를 유지합니다. 새 카테고리·점검 기준은 `db/category`와 `category_schema_history` 전용 Flyway 이력으로 관리합니다. 기존 운영 스키마의 최초 baseline 0은 배포 시 확인하고 실행해야 합니다.
- [상품 등록 AI 요청·질문·FAQ 연동](contracts/product-analysis-v1.md)
- [GA 관심도와 최초 공개 상품 알림 후보](contracts/ga-category-recommendations-v1.md), 최초 운영자 승인 시 `stockdrop.product.published.v1`을 동일 트랜잭션의 Outbox에 보관합니다. 수정 승인·FAQ·재고 변경은 신규 상품 알림을 만들지 않습니다.

- [조사 결과·의존성과 패키지·모델 초안·미정 정책](docs/initial-design.md)
- [Figma 기반 화면 요구사항·서비스 경계·구현 계획](docs/figma-product-command-plan.md)
- [실행·운영 스키마·검증 범위](docs/development.md)
- [Eureka 등록·Gateway 라우팅과 검증 범위](docs/service-discovery.md)

상세 문서와 AGENTS.md는 기존 서비스의 규칙에 맞춰 Git에서 제외합니다.
기존 추적 파일인 이 readMe.md는 유지합니다. 문서는 로컬에서만 제공되므로 클론에 포함되지 않습니다.
Git add/commit/push는 실행하지 않습니다.

## HTTP 응답 DTO

상품·옵션·이미지·문의·재고 응답은 전용 DTO로 반환합니다. 외부 JSON 성공 응답은 Account와 같은 ApiResponse(success/code/message/data)로 감쌉니다. 프런트는 업무 데이터를 data에서 읽으며 등록 메타데이터의 카테고리는 data.categories입니다. DTO의 from 메서드와 기존 서비스 변환 로직은 유지합니다.

일반 성공은 SuccessCode.OK(COMMON_SUCCESS), 상품·문의 생성은 HTTP 201과 SuccessCode.CREATED(COMMON_CREATED)를 사용합니다. 이미지 삭제는 HTTP 200과 data:null을 반환합니다. 오류 응답과 내부 /internal/v1/stock-reservations API는 기존 형식을 유지하므로 Order의 재고 계약은 바뀌지 않습니다. 공통 성공 응답에는 Cache-Control:no-store를 적용합니다.

## 객체 생성 규칙

응답 DTO와 업무 객체 생성은 Lombok 빌더를 사용합니다. DTO는 record 구조와 from 변환 메서드를 유지하고 필드명을 지정해 생성합니다. 엔티티 빌더는 기존 생성자에만 적용하여 UUID·초안 상태·재고 확보 만료 시각과 컬렉션 초기화를 보존합니다. JPA 기본 생성자, 업무 검증, 잠금과 상태 변경 로직은 별도로 유지합니다.
Lombok은 Spring Boot가 관리하는 버전을 compileOnly·annotationProcessor로 사용하며 운영 실행 의존성으로 추가하지 않습니다. 빌더 문자열에는 입력값을 출력하지 않습니다.

## 도메인 예외

AccountService와 같이 상품·재고·이미지·문의의 exception 패키지에 도메인별 Exception과 ErrorCode를 둡니다. 각 예외는 공통 BusinessException을 상속하고 기존 GlobalExceptionHandler가 HTTP 응답으로 변환합니다. 서비스와 엔티티는 해당 도메인 예외를 사용합니다.
내부 예외 타입을 구분하되 기존 PRODUCT_* 코드·HTTP 상태·메시지는 유지합니다. 특히 Order가 사용하는 PRODUCT_EXPIRED 계약은 바꾸지 않습니다. Account 인증·인가 실패도 기존 COMMON_UNAUTHORIZED·COMMON_FORBIDDEN 응답으로 유지합니다.

## 회원탈퇴 연동

[원본 확인, 신규 거래 차단과 장애 회수 계약](contracts/account-withdrawal-v1.md)을 확인합니다.
완료된 판매 이력은 탈퇴 차단 사유가 아닙니다. 진행 거래와 미확정 결제/보상만 확인하며 수수료는 후속 구현 범위입니다.

## 일반 업무 알림

[심사 결과, 보완 요청과 기한, 주문 확정과 배송 알림 계약](contracts/business-notifications-v1.md)을 확인합니다.
상태 변경과 Outbox는 함께 커밋하며 기존 구매 Saga와 결제 Kafka 계약은 유지합니다.

카테고리 확장 목록과 호환 범위는 [카테고리 확장 계약](contracts/category-expansion-v2.md)을 확인합니다.
