-- 사용자별 채팅 기본값. 방의 model·thinking_level이 null이면 이 값을 따른다.
-- 기존 행은 지금 실제로 쓰이고 있는 값(카탈로그 기본 모델, 그 모델의 추론 끔)으로 채운다.
-- DEFAULT는 채우는 용도로만 쓰고 바로 지운다. 남겨 두면 ModelCatalog.DEFAULT_ID의 사본이 DB에 하나 더 생긴다.
ALTER TABLE public.users
    ADD COLUMN default_model character varying(255) NOT NULL DEFAULT 'google/gemini-3.5-flash-lite',
    ADD COLUMN default_thinking_level character varying(255) NOT NULL DEFAULT 'OFF';

ALTER TABLE public.users
    ALTER COLUMN default_model DROP DEFAULT,
    ALTER COLUMN default_thinking_level DROP DEFAULT;

ALTER TABLE public.users
    ADD CONSTRAINT users_default_thinking_level_check
    CHECK (default_thinking_level IN ('OFF', 'LOW', 'MEDIUM', 'HIGH'));
