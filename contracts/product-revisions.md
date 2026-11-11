# 승인 상품 변경 심사와 판매자 관리 API

## 확정 정책

- 승인된 상품은 기존 단계 저장 API로 직접 수정하지 않는다. 판매 시작 전에 별도 수정 심사본을 제출한다.
- 상품명, 설명, 이미지 구성, 배송비, 출고 영업일, 배송 안내, 구매 유의사항, 배송 주소, 판매 일정을 변경할 수 있다. 모두 재심사한다.
- 승인 이후 가격, 옵션, 최초 수량, 구매 한도는 수정하지 않는다. 카테고리, 기존 판매처 URL, 브랜드, 상세 항목 변경은 이번 수정 계약에 포함하지 않는다.
- 심사 중에도 기존 승인본은 공개와 기존 판매 일정을 유지한다. 수정본은 승인 트랜잭션이 완료된 뒤에만 공개 이벤트로 전달한다.
- 기존 판매 시작 시각에 도달하면 수정본을 승인할 수 없다. 수정 일정으로 이미 시작한 판매를 연장하거나 재개하지 않는다. 운영자는 해당 회차를 반려 처리할 수 있다.
- 반려된 수정본은 이력을 보존하고 새로운 회차로 다시 제출한다. 동시에 진행 중인 회차는 상품당 하나만 허용한다.
- 판매 시작 후에는 FAQ와 문의 답변만 기존 API로 수정한다. 설명, 이미지, 배송 안내를 직접 변경하지 않는다.
- 주소 변경은 해당 상품의 새 승인 스냅샷부터 적용한다. 과거 주문 주소와 다른 상품을 자동 변경하지 않는다.

## 수정 심사 HTTP 계약

아래 모든 JSON 성공 응답은 기존 ApiResponse로 감싼다. 판매자 경로는 SELLER와 최신 Account 자격, 본인 소유권을 확인한다. 운영자 경로는 ADMIN만 허용한다.

| 메서드 | 경로 | 목적 |
| --- | --- | --- |
| POST | `/api/v1/seller/products/{id}/revisions` | 판매 시작 전 수정 심사본 제출, 201 |
| GET | `/api/v1/seller/products/{id}/revisions` | 본인 상품의 제출 회차와 결과 이력 |
| GET | `/api/v1/admin/product-reviews/revisions?status=IN_REVIEW&page=0&size=20` | 운영자 수정 심사 대기 목록 |
| GET | `/api/v1/admin/product-reviews/{productId}/revisions/{revisionId}` | 수정본과 검증된 배송 주소 조회 |
| GET | `/api/v1/admin/product-reviews/{productId}/revisions/{revisionId}/images/{imageId}/download-url` | 해당 회차에 포함된 이미지 열람 권한 |
| POST | `/api/v1/admin/product-reviews/{productId}/revisions/{revisionId}/decisions` | 수정본 승인 또는 반려 |

제출 요청은 변경 가능 항목의 전체 값이다. FAQ 등 다른 데이터는 포함하지 않으므로 승인 과정에서 덮어쓰지 않는다.

```json
{
  "expectedVersion": 4,
  "name": "상품명",
  "description": "상품 설명",
  "imageIds": ["00000000-0000-0000-0000-000000000001"],
  "representativeId": "00000000-0000-0000-0000-000000000001",
  "shippingFee": 3000,
  "dispatchBusinessDays": 2,
  "shippingNotice": "배송 안내",
  "purchaseNotice": "구매 전 확인",
  "startsAt": "2030-01-02T12:00:00+09:00",
  "dispatchAddressId": null,
  "returnAddressId": null
}
```

제출의 expectedVersion은 기존 상품 조회의 version이다. 결정 요청은 기존 ReviewProductRequest와 같은 `{expectedVersion, approved, reason}`이며 **수정 회차 응답의 version**을 사용한다. 반려 사유는 필수다. 입력 금액과 날짜는 예시이며 고정 정책이 아니다.

## 이미지 호출 흐름

1. 기존 상품과 공개 이미지 목록을 조회한다.
2. 새 이미지는 기존 image-uploads와 completion API를 사용한다. 승인 상품의 새 업로드는 판매 시작 전만 가능하다.
3. 승인 상품의 완료 이미지는 후보로 저장하며 공개 이미지 목록에 즉시 연결하지 않는다. completion 응답의 imageId로 프런트 미리보기를 유지한다. 새로고침 시 후보 ID를 잃었다면 다시 업로드하거나 제출 이력에서 조회한다.
4. 유지할 기존 이미지와 검증 완료된 후보의 ID를 합쳐 최대 10개, 중복 없이 순서와 대표 이미지를 지정해 수정 심사본을 제출한다.
5. 승인될 때 원본 필드와 이미지 구성 변경, Outbox 저장을 같은 트랜잭션으로 반영한다. 반려 시 기존 이미지 구성은 유지한다.

기존 승인 이미지에 대한 직접 삭제와 순서 변경은 계속 거부한다. 미검증 이미지, 타인 상품 이미지, 정리 완료된 후보는 제출할 수 없다. 심사 회차가 참조한 이미지와 교체된 기존 이미지는 자동 고아 객체 정리에서 제외한다. 이력 보존 기간과 영구 삭제 정책은 별도 확정 전까지 임의 만료시키지 않는다. S3 검증은 기존 파일 크기, MIME, 파일 서명 검사를 재사용하며 악성코드 검사나 전체 이미지 디코딩을 의미하지 않는다.

## 상품별 출고지와 반품지 선택

기존 PUT `/api/v1/seller/products/{id}/registration/pricing-shipping`에 선택 입력 `dispatchAddressId`, `returnAddressId`를 추가한다. 각각 생략하면 Account의 현재 기본 주소를 사용한다. 수정 심사 제출도 같은 규칙이다.

주소 목록은 브라우저가 일반 Gateway를 통해 Account의 `/api/v1/sellers/me/address-book/DISPATCH`, `/RETURN`에서 읽는다. Product는 선택 ID를 Account 본인 주소 단건 API로 검증한 뒤 주소 값만 스냅샷으로 저장한다. Account HTTP 호출은 Product DB 잠금 전에 수행하며, Account 장애나 소유권 확인 실패 시 상품 변경을 저장하지 않는다. 연락처와 계좌를 수집하지 않으며 다른 서비스 SQL을 실행하지 않는다.

## 판매 관리 조회

- GET `/api/v1/seller/products`에 선택 조건 `month=2030-02`, `saleState=SCHEDULED|ON_SALE|ENDED`를 추가한다. 기존 status, page, size를 유지한다.
- 월은 **Asia/Seoul 기준 판매 시작 월**이다. 초안처럼 시작 시각이 없으면 월 필터에 포함하지 않는다.
- 판매 상태는 승인된 상품만 대상으로 서버 시각을 사용한다. startsAt 이상, endsAt 미만이 ON_SALE이며 품절과 시간 종료를 혼동하지 않는다.
- GET `/api/v1/seller/products/sales-summary?month=2030-02`는 본인 상품 수, 예정 수, 진행 수, 종료 수와 최초 수량, 확보 수량, 확정 수량, 가용 수량을 반환한다.
- 상품 수는 해당 조건의 초안과 심사 상태도 포함한다. 수량 합계는 승인 상품만 포함한다. 임시 확보를 확정 판매량으로 합치지 않는다.
- 상품 단건의 기존 options 응답은 옵션별 수량을 제공한다. 수수료 기준의 취소·반품 제외 최종 판매율, 주문 매출과 실제 수수료는 여기서 계산하지 않는다.

## 데이터와 이벤트 경계

product.product_revisions는 제출 입력, Account 검증 주소, 제출 당시 기존 승인본 DTO, 결정 사유와 운영자, 시각, 회차 버전을 저장한다. 상품 행을 먼저 잠가 중복 제출과 승인 경합을 직렬화한다. 승인할 때 가격과 원본 재고, 옵션 ID를 복사하거나 초기화하지 않는다.

미승인 수정본 제출과 반려는 공개 Product Query 이벤트를 생성하지 않는다. 승인은 기존 PRODUCT_UPSERT 계약의 버전을 사용한다. 새로운 Kafka 토픽이나 Consumer를 추가하지 않는다. Query 반영은 비동기이며 이번 변경만으로 실제 서비스 간 연동 검증을 완료한 것은 아니다.

개발은 엔티티 기반 ddl-auto=update를 유지한다. 운영 validate 전에 새 테이블과 nullable 이미지 이력 플래그의 스키마 준비가 필요하다. 이 작업은 운영 DB 변경, 데이터 이전, AWS 변경, Git add/commit/push를 수행하지 않는다.
