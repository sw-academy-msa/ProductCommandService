-- 기존 상품 데이터를 변경하지 않는 초안 삭제 기록입니다.
CREATE TABLE product.product_draft_deletions (
    product_id uuid PRIMARY KEY,
    seller_id uuid NOT NULL,
    deleted_at timestamp(6) with time zone NOT NULL
);
