-- 별도 Command PostgreSQL에 Product 원본 DB를 준비합니다. 일반 Account DB 서버에서는 실행하지 않습니다.
-- product_password는 호출 스크립트가 생성해 psql 입력으로만 전달하며 SQL 파일에는 저장하지 않습니다.
BEGIN;
DO $$
BEGIN
    IF current_database() <> 'postgres' OR current_user <> 'command_local_admin' THEN
        RAISE EXCEPTION '로컬 Command DB 관리자·관리 DB가 아닙니다. 대상 서버를 확인하세요.';
    END IF;
    -- 부분 실패 후 재실행해도 기존 비밀번호·소유권·데이터를 덮어쓰지 않습니다.
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'product'
               AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR NOT rolcanlogin)) THEN
        RAISE EXCEPTION '기존 product 역할의 권한이 예상과 다릅니다.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_database WHERE datname = 'product_command'
               AND pg_get_userbyid(datdba) <> current_user) THEN
        RAISE EXCEPTION '기존 Product DB의 소유자가 예상과 다릅니다.';
    END IF;
END $$;

SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'product') AS create_product_role \gset
\if :create_product_role
CREATE ROLE product LOGIN PASSWORD :'product_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
\endif
COMMIT;

-- CREATE DATABASE는 트랜잭션 안에서 실행할 수 없습니다. 실패 시 기존 DB를 삭제해 재시도하지 않습니다.
-- 앱은 업무 스키마만 소유하고 DB 소유권은 관리자에 남겨 public/다른 스키마 생성 권한을 막습니다.
SELECT NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'product_command') AS create_product_database \gset
\if :create_product_database
CREATE DATABASE product_command TEMPLATE template0 ENCODING 'UTF8';
\endif
REVOKE ALL ON DATABASE product_command FROM PUBLIC;
GRANT CONNECT ON DATABASE product_command TO product;
ALTER ROLE product IN DATABASE product_command SET search_path TO product;
\connect product_command

BEGIN;
-- 여기서부터 새 Product DB만 초기화합니다. 내부 업무 스키마 이름은 기존 JPA 매핑을 유지합니다.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA IF NOT EXISTS product AUTHORIZATION product;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='product' AND pg_get_userbyid(nspowner)='product') THEN
        RAISE EXCEPTION 'Product 스키마 소유자가 예상과 다릅니다.';
    END IF;
END $$;
REVOKE ALL ON SCHEMA product FROM PUBLIC;
COMMIT;
