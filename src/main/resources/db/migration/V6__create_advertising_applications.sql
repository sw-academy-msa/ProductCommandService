-- 기존 상품은 광고 미신청으로 이행하고 회차 원본은 삭제하지 않습니다.
alter table product.products add column advertising_requested boolean not null default false;
alter table product.products add column advertising_submission_locked boolean not null default false;
alter table product.products add column current_advertising_application_id uuid;
create table product.advertising_applications(
 id uuid primary key, product_id uuid not null, seller_id uuid not null, submission_id uuid not null unique,
 request_key uuid not null, requested_product_version bigint not null, frozen_product_version bigint not null,
 price_snapshot bigint not null, quantity_snapshot bigint not null, gross_amount numeric(40,0) not null,
 fee_amount bigint not null, formula_version varchar(20) not null,snapshot_hash varchar(64) not null,
 intent_status varchar(20) not null,payment_status varchar(20) not null,payment_attempt_id uuid,
 payment_result_version bigint not null,cancellation_requested boolean not null,cancellation_status varchar(20) not null,
 fulfillment_status varchar(30) not null,active boolean not null,expires_at timestamp(6) with time zone not null,
 submitted_at timestamp(6) with time zone,paid_at timestamp(6) with time zone,canceled_at timestamp(6) with time zone,
 payment_updated_at timestamp(6) with time zone,created_at timestamp(6) with time zone not null,version bigint not null,
 unique(seller_id,request_key)
);
create unique index uq_advertising_active_product on product.advertising_applications(product_id) where active;
create table product.advertising_result_receipts(event_id uuid primary key,payload_hash varchar(64) not null,processed_at timestamp(6) with time zone not null);
