-- 로어 컬럼 제약을 엔티티와 맞추고, 더 이상 쓰지 않는 priority 컬럼을 지운다.
-- 기존 행에 null이나 길이를 넘는 값이 있으면 실패하고 트랜잭션째 되돌아간다.
ALTER TABLE public.lore
    ALTER COLUMN always_on SET NOT NULL,
    ALTER COLUMN enabled SET NOT NULL,
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN title TYPE character varying(50),
    ALTER COLUMN trigger_keys TYPE character varying(200),
    DROP COLUMN IF EXISTS priority;
