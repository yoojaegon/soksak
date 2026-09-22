package com.soksak.soksak.message;

import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.chatRoom.ChatRoomService;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.aiClient.ChatAiClient;
import com.soksak.soksak.message.dto.DeleteFromResponse;
import com.soksak.soksak.message.dto.MessageResponse;
import com.soksak.soksak.message.dto.RegenTarget;
import com.soksak.soksak.message.dto.StreamJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final ChatRoomService chatRoomService;
    private final ChatTxService chatTxService;
    private final ChatAiClient chatAiClient;
    private final ExecutorService chatStreamExecutor;
    private static final Long STREAM_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final int LOCK_STRIPES = 256;
    private final Lock[] roomLocks = Stream.generate(ReentrantLock::new)
            .limit(LOCK_STRIPES).toArray(Lock[]::new);


    public MessageResponse sendMessage(String loginId, Long roomId, String content) {
        return withRoomLock(roomId, () -> {
            PreparedChat preparedChat = chatTxService.prepareAndSaveUser(loginId, roomId, content);
            // trySummarize가 p.summaries()에 방금 만든 조각을 더한다 — 반드시 그 뒤에 넘길 것.
            trySummarize(preparedChat);
            String reply = chatAiClient.reply(preparedChat.room(), content,
                    preparedChat.priorHistory(), preparedChat.summaries());
            Message aiMessage = chatTxService.saveAssistant(roomId, reply);
            return MessageResponse.from(aiMessage);
        });
    }

    public SseEmitter sendMessageStream(String loginId, Long roomId, String content) {
        return startStream(roomId, () -> {
            PreparedChat p = chatTxService.prepareAndSaveUser(loginId, roomId, content);
            trySummarize(p);
            return new StreamJob(p.room(), content, p.priorHistory(), p.summaries());
        });
    }

    @Transactional
    public List<MessageResponse> getMessages(String loginId, Long roomId) {
        chatRoomService.getOwnedChatRoom(loginId, roomId);
        return messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId).stream()
                .map(MessageResponse::from).toList();
    }

    // 메세지 수정
    @Transactional
    public MessageResponse updateMessage(String loginId, Long roomId, Long id, String content) {
        chatRoomService.getOwnedChatRoom(loginId, roomId);
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        if (!message.getChatRoom().getId().equals(roomId)) {
            throw new BusinessException(ErrorCode.MESSAGE_FORBIDDEN);
        }
        message.update(content);

        return MessageResponse.from(message);
    }

    // ai 응답 재생성
    public MessageResponse regenerate(String loginId, Long roomId) {
        return withRoomLock(roomId, () -> {
            RegenTarget t = chatTxService.prepareRegenerate(loginId, roomId);
            String reply = chatAiClient.reply(t.room(), t.lastUserContent(), t.priorHistory(), t.summaries());
            Message ai = chatTxService.saveAssistant(roomId, reply);
            return MessageResponse.from(ai);
        });
    }

    public SseEmitter regenerateStream(String loginId, Long roomId) {
        return startStream(roomId, () -> {
            RegenTarget t = chatTxService.prepareRegenerate(loginId, roomId);
            return new StreamJob(t.room(), t.lastUserContent(), t.priorHistory(), t.summaries());
        });
    }

    // 락 안에서 돈다 — AI 응답을 만드는 중에 지워버리면, 이미 계획된 요약 조각이 사라진
    // 메시지의 id를 가리킨 채로 저장된다. 그 사이의 삭제 요청은 ROOM_BUSY로 돌려보낸다.
    public DeleteFromResponse deleteFrom(String loginId, Long roomId, Long messageId) {
        return withRoomLock(roomId, () -> chatTxService.deleteFrom(loginId, roomId, messageId));
    }

    private <T> T withRoomLock(Long roomId, Supplier<T> action) {
        Lock lock = roomLocks[Math.floorMod(roomId, LOCK_STRIPES)];
        if (!lock.tryLock()){
            throw new BusinessException(ErrorCode.ROOM_BUSY);
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    // 답변 생성 앞에서 돈다 — 방금 만든 요약이 이번 턴 프롬프트에 실리게 하려는 것.
    // 조회는 없다. prepareAndSaveUser가 이미 읽어온 방/이력으로만 판단한다.
    private void trySummarize(PreparedChat p) {
        try {
            SummaryPlan plan = SummaryPlan.of(p.priorHistory(), p.summaries());
            if (plan == null) return;

            SummarizeResponse result = chatAiClient.summarize(plan.previousSummaries(), plan.batch());
            ChatSummary saved = chatTxService.appendSummary(p.room().getId(), plan, result);

            // p.summaries()는 이 턴의 프롬프트를 만들 때 그대로 쓰인다. SSE 경로는 별도 스레드라
            // 위 저장의 영속성 컨텍스트가 다르니, 여기서 직접 더해줘야 방금 만든 요약이 이번 턴에
            // 실린다. 이 한 줄이 그 유일한 경로다 — 지우면 새 요약이 한 턴 늦게 반영된다.
            p.summaries().add(saved);
        } catch (Exception e) {
            // 요약이 실패해도 채팅은 계속된다 — 요약 안 된 구간은 원문 그대로 프롬프트에 실린다.
            log.warn("요약 실패 (roomId={})", p.room().getId(), e);
        }
    }

    private SseEmitter startStream(Long roomId, Supplier<StreamJob> prepare) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        chatStreamExecutor.execute(() -> {
            Lock lock = roomLocks[Math.floorMod(roomId, LOCK_STRIPES)];
            if (!lock.tryLock()) {
                sendError(emitter, ErrorCode.ROOM_BUSY);
                return;
            }
            try{
                StreamJob job = prepare.get();
                String full = chatAiClient.replyStream(
                        job.room(), job.content(), job.priorHistory(), job.summaries(),
                        token -> sendToken(emitter, token));
                Message ai = chatTxService.saveAssistant(roomId, full);
                emitter.send(SseEmitter.event().name("done").data(MessageResponse.from(ai)));
                emitter.complete();
            } catch (Exception e) {
                sendError(emitter, e);
            } finally {
                lock.unlock();
            }
        });
        return emitter;
    }

    // 토큰 한 조각을 프론트로. 문자열 대신 Map으로 보내 JSON 직렬화 → 개행이 있어도 SSE 프레이밍이 안 깨진다.
    private void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data(Map.of("content", token)));
        } catch (IOException e) {
            // 클라이언트가 연결을 끊음 → 예외로 전파해 스트림 읽기를 멈춘다(그 결과 assistant 미저장).
            throw new UncheckedIOException(e);
        }
    }

    // 예외 종류에 따라 적절한 ErrorCode로 변환해 에러 이벤트를 보낸다.
    private void sendError(SseEmitter emitter, Exception e) {
        if (e instanceof BusinessException be) {
            sendError(emitter, be.getErrorCode());   // ROOM_BUSY / 방 소유권 / AI_UNAVAILABLE 등 그대로
        } else if (e instanceof UncheckedIOException) {
            // 사용자가 탭을 닫거나 새로고침한 것(sendToken이 올린 것) — 고장이 아니라 정상 경로다.
            // 알릴 상대가 이미 없으므로 닫기만 한다. warn+스택으로 찍으면 진짜 오류가 묻힌다.
            log.debug("클라이언트가 스트림을 끊음", e);
            emitter.complete();
        } else {
            log.warn("스트리밍 처리 중 예기치 못한 오류", e);
            sendError(emitter, ErrorCode.INTERNAL_ERROR);
        }
    }

    // ErrorCode를 error 이벤트로 실어보내고 스트림을 닫는다.
    private void sendError(SseEmitter emitter, ErrorCode code) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(Map.of("code", code.name(), "message", code.getMessage())));
            emitter.complete();
        } catch (Exception ex) {
            // 이미 끊겼거나 완료된 경우 — 더 보낼 수 없으니 조용히 닫는다.
            // completeWithError로 예외를 넘겨봐야 받을 클라이언트가 없고, 응답이 커밋된 뒤라
            // 에러 페이지도 못 그려 ERROR 로그만 남는다. 흔적은 debug 한 줄이면 충분하다.
            log.debug("스트림 종료 알림 실패 — 이미 닫힌 연결", ex);
            emitter.complete();
        }
    }
}
