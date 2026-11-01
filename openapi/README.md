# Product Command API 계약

실제 컨트롤러와 DTO가 계약의 기준이며 `product-command.openapi.json`은 Swagger 생성 결과의 검토용 스냅샷입니다.
경로·요청·응답 변경 후 `./gradlew.bat test bootJar`로 검증하고 `build/generated/product-command.openapi.json`과 비교해 갱신합니다.
실제 토큰·서명 URL·계정 정보는 이 디렉토리에 저장하지 않습니다.
실행 설정과 상태 전이·멱등·권한 제약은 루트 README와 `docs/product-command-api.md`를 확인합니다.
내부 재고 API는 외부 Gateway에 공개하지 않으며 Order 서비스 인증 계약이 설정되기 전에는 차단됩니다.
신규 등록은 `/api/v1/seller/products/{id}/registration/*` 단계별 PUT을 사용합니다. 기존 상품 전체 PUT은 호환용 폐기 예정 API입니다.
이미지는 기존 S3 직접 업로드 절차를 유지하며, 상품 문의 생성 POST만 PurchaseGateway에 추가로 연결했습니다.
