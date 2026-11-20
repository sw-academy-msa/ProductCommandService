-- 실패 이력과 requestId는 보존하면서 진행 중 및 성공 결과만 같은 지문으로 재사용합니다.
alter table product.product_analyses add column if not exists status varchar(20) not null default 'PENDING';
update product.product_analyses set status = 'COMPLETED' where result_payload is not null and status = 'PENDING';
alter table product.product_analyses drop constraint if exists uk_analysis_input;
create unique index if not exists uk_analysis_active_input
    on product.product_analyses(product_id, fingerprint) where status <> 'FAILED';
