-- Postgres는 외래 키에 인덱스를 자동으로 만들지 않는다.
-- 목록 조회와 cascade 삭제가 이 컬럼들로 행을 찾으므로 인덱스를 단다.
CREATE INDEX idx_message_chat_room ON public.message (chat_room_id);
CREATE INDEX idx_chat_room_user ON public.chat_room (user_id);
CREATE INDEX idx_chat_room_character ON public.chat_room (character_id);
CREATE INDEX idx_characters_user ON public.characters (user_id);
CREATE INDEX idx_lore_character ON public.lore (character_id);
CREATE INDEX idx_character_tags_character ON public.character_tags (character_id);
CREATE INDEX idx_character_like_character ON public.character_like (character_id);
CREATE INDEX idx_reports_reporter ON public.reports (reporter_id);
