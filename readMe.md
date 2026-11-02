productCommandSerivce 입니다.

# STOCKDROP Product Command Service

Java 21 / Spring Boot 4.1.1 / Spring Cloud 2025.1.4 / Gradle 9.8.2.
AccountService의 초기 구성과 스타일을 참고한 MVC 서비스입니다.
상품 초안·옵션 저장, 심사 제출·승인·반려, 이미지 업로드 권한·검증·연결과 재고 확보·확정·해제 API를 구현했습니다.
판매자 등록은 단계별 PUT으로 저장하며 브랜드·자유 상세 정보·구조화된 옵션·Account 배송 주소 스냅샷·판매자 확인 FAQ를 보존합니다.
본인 상품 관리 목록·운영자 심사 목록·상품 문의·판매자 답변도 제공합니다. AI 생성은 아직 연결하지 않았습니다.
상품은 검사 후 승인하며 승인 전에는 재고를 확보할 수 없습니다. 승인·재고·FAQ·문의 변경은 Outbox와 Kafka를 통해 별도 Product Query에 전달할 수 있습니다. 두 앱의 발행/소비 활성화가 필요합니다.
재고 확보는 5분이며, Order의 결제 진행 전이와 실제 승인 시각에 따라 확정·해제합니다.
API 목록·설정·내부 인증 계약·검증 범위는 [상품·재고 API 안내](docs/product-command-api.md)를 확인합니다.
[상품 등록 7단계 프런트 연동 안내](docs/product-registration.md)에 단계별 요청과 기존 전체 PUT 전환 방법을 정리했습니다.

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
- 최종 카테고리는 `PRODUCT_CATEGORIES`로 주입합니다. 목록 미설정 시 심사 제출을 차단합니다.
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
- 개발 스키마는 ddl-auto=update로 반영합니다. 운영 스키마 관리 방식은 배포 준비 시 정하며 운영 validate 설정은 유지합니다.

- [조사 결과·의존성과 패키지·모델 초안·미정 정책](docs/initial-design.md)
- [Figma 기반 화면 요구사항·서비스 경계·구현 계획](docs/figma-product-command-plan.md)
- [실행·운영 스키마·검증 범위](docs/development.md)
- [Eureka 등록·Gateway 라우팅과 검증 범위](docs/service-discovery.md)

상세 문서와 AGENTS.md는 기존 서비스의 규칙에 맞춰 Git에서 제외합니다.
기존 추적 파일인 이 readMe.md는 유지합니다. 문서는 로컬에서만 제공되므로 클론에 포함되지 않습니다.
Git add/commit/push는 실행하지 않습니다.
