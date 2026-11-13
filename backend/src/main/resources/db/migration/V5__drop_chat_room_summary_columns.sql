-- 요약은 chat_summary 조각이 갖고 커서도 조각에서 유도하므로, 방에 두던 요약 미러를 지운다.
ALTER TABLE public.chat_room
    DROP COLUMN summary,
    DROP COLUMN summarized_up_to_id;
