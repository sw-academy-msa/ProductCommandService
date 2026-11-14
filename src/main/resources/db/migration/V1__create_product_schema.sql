-- 상품 서비스의 최초 업무 테이블입니다. 분류와 점검 기준 테이블은 V2에서 만듭니다. 엔티티 enum 값은 애플리케이션이 검증하므로 DB CHECK로 고정하지 않습니다.

create table product.active_stock_reservations (
    buyer_id uuid not null,
    product_id uuid not null,
    reservation_id uuid not null,
    primary key (reservation_id),
    unique (product_id, buyer_id)
);

create table product.outbox_events (
    attempts integer not null,
    created_at timestamp(6) with time zone not null,
    next_attempt_at timestamp(6) with time zone not null,
    published_at timestamp(6) with time zone,
    id uuid not null,
    partition_key varchar(255) not null,
    payload text not null,
    topic varchar(255) not null,
    primary key (id)
);

create table product.product_analyses (
    created_at timestamp(6) with time zone,
    id uuid not null,
    product_id uuid not null,
    seller_id uuid not null,
    fingerprint varchar(64) not null,
    request_payload text not null,
    result_payload text,
    primary key (id),
    constraint uk_analysis_input unique (product_id, fingerprint)
);

create table product.product_attributes (
    attribute_position integer not null check ((attribute_position>=0)),
    product_id uuid not null,
    attribute_name varchar(200) not null,
    attribute_value varchar(4000) not null,
    primary key (attribute_position, product_id)
);

create table product.product_faqs (
    faq_position integer check ((faq_position>=0)),
    confirmed_at timestamp(6) with time zone not null,
    confirmed_by uuid not null,
    id uuid not null,
    product_id uuid not null,
    source_inquiry_id uuid,
    suggestion_id uuid,
    question varchar(2000) not null,
    answer varchar(8000) not null,
    primary key (id)
);

create table product.product_images (
    attached boolean not null,
    position integer not null,
    representative boolean not null,
    revision_candidate boolean,
    revision_referenced boolean,
    storage_cleaned boolean not null,
    expires_at timestamp(6) with time zone,
    size_bytes bigint not null,
    id uuid not null,
    product_id uuid not null,
    seller_id uuid not null,
    object_key varchar(1000),
    upload_key varchar(1000) not null unique,
    content_type varchar(255) not null,
    primary key (id)
);

create table product.product_inquiries (
    answered_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    version bigint not null,
    answered_by uuid,
    buyer_id uuid not null,
    id uuid not null,
    product_id uuid not null,
    question varchar(2000) not null,
    answer varchar(8000),
    primary key (id)
);

create table product.product_option_dimensions (
    dimension_position integer check ((dimension_position>=0)),
    id uuid not null,
    product_id uuid not null,
    name varchar(200) not null,
    primary key (id),
    unique (product_id, name)
);

create table product.product_option_selections (
    option_id uuid not null,
    dimension_name varchar(200) not null,
    selected_value varchar(200) not null,
    primary key (option_id, dimension_name)
);

create table product.product_option_values (
    value_position integer not null check ((value_position>=0)),
    dimension_id uuid not null,
    option_value varchar(200) not null,
    primary key (value_position, dimension_id)
);

create table product.product_options (
    confirmed_quantity integer not null,
    initial_quantity integer not null,
    reserved_quantity integer not null,
    id uuid not null,
    product_id uuid not null,
    label varchar(500) not null,
    primary key (id),
    unique (product_id, label),
    check (initial_quantity >= 0 and reserved_quantity >= 0 and confirmed_quantity >= 0 and reserved_quantity + confirmed_quantity <= initial_quantity)
);

create table product.product_revisions (
    reviewed_at timestamp(6) with time zone,
    submitted_at timestamp(6) with time zone,
    version bigint not null,
    id uuid not null,
    product_id uuid not null,
    reviewer_id uuid,
    seller_id uuid not null,
    reason varchar(2000),
    previous_snapshot text,
    snapshot text not null,
    status varchar(255) not null,
    primary key (id)
);

create table product.products (
    dispatch_business_days integer,
    per_account_limit integer,
    registration_format_version integer not null,
    addresses_captured_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone,
    ends_at timestamp(6) with time zone,
    price bigint,
    reviewed_at timestamp(6) with time zone,
    shipping_fee bigint,
    starts_at timestamp(6) with time zone,
    submitted_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    version bigint not null,
    change_id uuid,
    id uuid not null,
    reviewer_id uuid,
    seller_id uuid not null,
    dispatch_postal_code varchar(20),
    return_postal_code varchar(20),
    brand_or_store varchar(200),
    category varchar(200),
    name varchar(200),
    dispatch_detail_address varchar(500),
    dispatch_road_address varchar(500),
    return_detail_address varchar(500),
    return_road_address varchar(500),
    review_reason varchar(2000),
    source_url varchar(2000),
    disposal_reason varchar(4000),
    purchase_notice varchar(4000),
    shipping_notice varchar(4000),
    description varchar(20000),
    status varchar(255) not null,
    primary key (id)
);

create table product.projection_sequences (
    sold_out_at timestamp(6) with time zone,
    version bigint not null,
    id varchar(255) not null,
    primary key (id)
);

create table product.stock_reservation_items (
    quantity integer not null,
    option_id uuid not null,
    reservation_id uuid not null,
    primary key (option_id, reservation_id)
);

create table product.stock_reservation_option_labels (
    option_id uuid not null,
    reservation_id uuid not null,
    option_label varchar(500),
    primary key (option_id, reservation_id)
);

create table product.stock_reservations (
    dispatch_business_days integer,
    created_at timestamp(6) with time zone not null,
    expires_at timestamp(6) with time zone not null,
    finished_at timestamp(6) with time zone,
    payment_approved_at timestamp(6) with time zone,
    shipping_fee bigint not null,
    unit_price bigint not null,
    buyer_id uuid not null,
    id uuid not null,
    order_id uuid not null unique,
    payment_attempt_id uuid,
    product_id uuid not null,
    seller_id uuid,
    brand_or_store varchar(200),
    product_name varchar(200),
    status varchar(255) not null,
    primary key (id)
);

create table product.withdrawal_fence (
    canceled boolean not null,
    account_id uuid not null,
    id uuid not null,
    primary key (id)
);

create table product.withdrawal_gate (
    blocked boolean not null,
    account_id uuid not null,
    operation_id uuid,
    primary key (account_id)
);

create index idx_outbox_pending
   on product.outbox_events (published_at, next_attempt_at, created_at);

create index idx_image_product_attached
   on product.product_images (product_id, attached, position);

create index idx_inquiry_product
   on product.product_inquiries (product_id, created_at);

create index idx_product_revision_history
   on product.product_revisions (product_id, submitted_at);

create index idx_product_revision_queue
   on product.product_revisions (status, submitted_at);

create index idx_product_seller
   on product.products (seller_id);

create index idx_reservation_expiry
   on product.stock_reservations (status, expires_at);

create index idx_reservation_product_buyer
   on product.stock_reservations (product_id, buyer_id);

alter table if exists product.product_attributes
   add constraint FKcex46yvx4g18b2pn09p79h1mc
   foreign key (product_id)
   references product.products;

alter table if exists product.product_faqs
   add constraint FKl9gr0t2b7v472jchwfrjjm4w5
   foreign key (product_id)
   references product.products;

alter table if exists product.product_option_dimensions
   add constraint FKq71woon92f6ckxeaaydfs7s3q
   foreign key (product_id)
   references product.products;

alter table if exists product.product_option_selections
   add constraint FK3lmoud3x69pes2nl9uhilykbc
   foreign key (option_id)
   references product.product_options;

alter table if exists product.product_option_values
   add constraint FKaqa986eh888ym9u8y4cr36sde
   foreign key (dimension_id)
   references product.product_option_dimensions;

alter table if exists product.product_options
   add constraint FK8vv4f8fru80wxocwgxwsrow61
   foreign key (product_id)
   references product.products;

alter table if exists product.stock_reservation_items
   add constraint FKd5pyb9swta0l83lrji6vc97jx
   foreign key (reservation_id)
   references product.stock_reservations;

alter table if exists product.stock_reservation_option_labels
   add constraint FKkrtlsutt8bx4fd6pum4808wpk
   foreign key (reservation_id)
   references product.stock_reservations;
