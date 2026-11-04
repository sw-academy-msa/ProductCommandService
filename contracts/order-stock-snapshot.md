# Order 내부 확보 응답 확장

외부 Product JSON 성공 응답은 ApiResponse로 감싸지만 이 내부 재고 API는 기존 StockReservationResponse를 그대로 반환합니다. Order는 id·status·expiresAt 등을 응답 최상위에서 읽으며 data 래퍼를 추가하지 않습니다.

Order가 Product 테이블을 직접 읽거나 지연 가능한 Query 가격을 신뢰하지 않도록 기존 재고 확보 응답에 sellerId/productName/brandOrStore/dispatchBusinessDays/optionLabels를 추가했습니다. 단가·배송비·만료·수량과 함께 최초 확보 성공 시 원본에 저장하고 재요청/상태 조회에서도 같은 스냅샷을 반환합니다.

재고 확보·확정·해제와 동시성 제어 책임은 계속 Product에 있습니다. Order는 기존 내부 HTTP API를 호출해 주문·결제 Saga를 조율합니다. 상품 문의·공개 FAQ·상품 사진은 Product, 특정 주문의 배송/하자 문의와 첨부는 Order 책임입니다.

개발 스키마는 ddl-auto=update로 반영합니다. 운영 스키마 관리 방식은 배포 준비 시 정합니다. 과거 확보의 누락 정보를 임의로 추정/백필하지 않습니다. Account 기존 발급기를 재사용한 내부 서비스 토큰과 Order 자동 획득·갱신이 추가되었습니다. Product의 UUID subject/scope=product.stock/ORDER_SERVICE_SUBJECT 검증은 유지하며 principal_type=service/client_id=order-command-service도 확인합니다.
