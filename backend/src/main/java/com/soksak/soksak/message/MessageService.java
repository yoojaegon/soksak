package com.soksak.soksak.message;

import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.chatRoom.ChatRoomService;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.aiClient.ChatAiClient;
import com.soksak.soksak.aiClient.ModelCatalog;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.credit.CreditService;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final CreditService creditService;
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
            // 차감은 위에서 이미 커밋됐다. 답을 못 만들면 스트림 경로와 같은 규칙으로 돌려준다.
            try {
                String reply = chatAiClient.reply(preparedChat.room(), content,
                        preparedChat.priorHistory(), preparedChat.summaries());
                Message aiMessage = chatTxService.saveAssistant(roomId, reply);
                return MessageResponse.from(aiMessage);
            } catch (Exception e) {
                tryRefund(preparedChat.room());
                throw e;
            }
        });
    }

    public SseEmitter sendMessageStream(String loginId, Long roomId, String content) {
        ensureCredit(loginId, roomId);
        return startStream(roomId, () -> {
            PreparedChat p = chatTxService.prepareAndSaveUser(loginId, roomId, content);
            trySummarize(p);
            return new StreamJob(p.room(), content, p.priorHistory(), p.summaries());
        });
    }

    @Transactional
    public List<MessageResponse> getMessages(String loginId, Long roomId) {
        chatRoomService.getOwnedChatRoom(loginId, roomId);
        return messageRepository.findByChatRoomIdOrderByIdAsc(roomId).stream()
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
            try {
                String reply = chatAiClient.reply(t.room(), t.lastUserContent(), t.priorHistory(), t.summaries());
                Message ai = chatTxService.saveAssistant(roomId, reply);
                return MessageResponse.from(ai);
            } catch (Exception e) {
                tryRefund(t.room());
                throw e;
            }
        });
    }

    public SseEmitter regenerateStream(String loginId, Long roomId) {
        ensureCredit(loginId, roomId);
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

    /**
     * 스트림을 열기 <b>전에</b> 잔액을 본다 — 여기서 던져야 평범한 402 JSON으로 나간다.
     * <p>
     * {@code startStream} 안으로 들어가면 {@code prepare}는 이미 emitter를 돌려준 뒤 별도
     * 스레드에서 돌기 때문에, 부족을 알리는 길이 SSE {@code event: error}밖에 없다. 동작은 하지만
     * 프론트가 "스트림 안의 에러"를 따로 다뤄야 해서, 다른 4xx와 같은 모양으로 맞춰 준다.
     * <p>
     * ⚠️ <b>이건 가드가 아니다.</b> 여기서 읽고 {@code charge}까지 사이에 다른 요청이 낄 수 있다
     * (TOCTOU). 진짜 보호는 {@code CreditService.charge}의 원자적 UPDATE고, 둘 다 있어야 한다.
     * <p>
     * ⚠️ 방을 한 번 더 읽는 비용이 붙는다. 소유권 확인은 어차피 {@code prepare} 안에서 다시 하므로
     * 중복이지만, 인덱스 조회 하나로 "스트림이 열리기 전에 거절"을 사는 값이라 싼 편이다.
     */
    private void ensureCredit(String loginId, Long roomId) {
        ChatRoom room = chatRoomService.getOwnedChatRoom(loginId, roomId);
        creditService.ensureAffordable(room.getUser().getId(), ModelCatalog.costOf(room.getModel()));
    }

    private SseEmitter startStream(Long roomId, Supplier<StreamJob> prepare) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        chatStreamExecutor.execute(() -> {
            Lock lock = roomLocks[Math.floorMod(roomId, LOCK_STRIPES)];
            if (!lock.tryLock()) {
                sendError(emitter, ErrorCode.ROOM_BUSY);
                return;
            }
            // 브라우저가 아직 듣고 있는가. 끊겨도 생성과 저장은 끝까지 간다(sendToken 참고) —
            // 이 플래그는 "더 보내봐야 소용없다"는 표시일 뿐 흐름을 멈추지 않는다.
            AtomicBoolean listening = new AtomicBoolean(true);
            // 환불 판정에 쓴다. null이면 prepare가 실패한 것 = 차감도 같이 롤백됐다는 뜻이라
            // 돌려줄 게 없다. 값이 들어온 뒤의 실패만 "받아놓고 답을 못 준" 경우다.
            StreamJob job = null;
            try{
                job = prepare.get();
                String full = chatAiClient.replyStream(
                        job.room(), job.content(), job.priorHistory(), job.summaries(),
                        token -> sendToken(emitter, listening, token));
                Message ai = chatTxService.saveAssistant(roomId, full);
                if (listening.get()) {
                    try {
                        emitter.send(SseEmitter.event().name("done").data(MessageResponse.from(ai)));
                    } catch (IOException | IllegalStateException e) {
                        // 마지막 순간에 끊긴 것. 저장은 이미 끝났으므로 알릴 상대가 없을 뿐 실패가 아니다.
                        log.debug("done 전송 실패 — 이미 닫힌 연결", e);
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                tryRefund(job == null ? null : job.room());
                sendError(emitter, e);
            } finally {
                lock.unlock();
            }
        });
        return emitter;
    }

    /**
     * 토큰 한 조각을 프론트로. 문자열 대신 Map으로 보내 JSON 직렬화 → 개행이 있어도 SSE 프레이밍이 안 깨진다.
     * <p>
     * ⚠️ <b>전송 실패를 위로 던지지 않는다.</b> 예전엔 {@code UncheckedIOException}으로 올려
     * 스트림 읽기를 멈췄는데, 그러면 사용자가 탭을 닫는 순간 생성이 중단되고 ASSISTANT가 저장되지
     * 않아 <b>내 말만 남은 방</b>이 됐다(그 파편이 요약 배치에까지 섞였다). 지금은 받을 사람이
     * 없어도 끝까지 읽어서 저장한다 — 다시 들어오면 답이 완성돼 있다.
     * <p>
     * 대가는 둘이다: 사용자가 안 보더라도 토큰 요금은 끝까지 나가고, 방 락도 생성이 끝날 때까지
     * 잡혀 있다(끊고 곧바로 다시 보내면 {@code ROOM_BUSY}). 마디를 받은 이상 답을 남겨 주는 쪽이
     * 맞다고 보고 치르는 값이다.
     * <p>
     * {@code IllegalStateException}도 같이 잡는다 — 끊긴 뒤 컨테이너가 emitter를 이미 완료 처리하면
     * {@code send}가 {@code IOException}이 아니라 이쪽을 던진다.
     */
    private void sendToken(SseEmitter emitter, AtomicBoolean listening, String token) {
        if (!listening.get()) {
            return;     // 이미 끊긴 뒤 — 남은 토큰마다 예외를 만들어 낼 이유가 없다
        }
        try {
            emitter.send(SseEmitter.event().name("token").data(Map.of("content", token)));
        } catch (IOException | IllegalStateException e) {
            log.debug("클라이언트가 스트림을 끊음 — 생성과 저장은 계속한다", e);
            listening.set(false);
        }
    }

    /**
     * 실패로 끝난 턴의 마디를 돌려준다.
     * <p>
     * ⚠️ 여기까지 오는 건 <b>답을 한 글자도 남기지 못한 경우</b>뿐이다. 사용자가 중간에 나간 것은
     * 더 이상 예외가 아니라서({@link #sendToken}) 이 경로로 오지 않는다 — 그쪽은 답이 저장되므로
     * 받은 값을 돌려줄 이유가 없다.
     * <p>
     * 환불이 실패해도 원래 오류를 덮지 않게 통째로 감싼다. 사용자에게 알려야 하는 건 "답이 안
     * 나왔다"지 "환불이 안 됐다"가 아니다 — 후자는 로그의 몫이고, 원장을 보면 차감만 남아 있다.
     */
    private void tryRefund(ChatRoom room) {
        if (room == null) {
            return;     // 준비 단계에서 터진 것 = 차감도 같은 트랜잭션에서 롤백됐다
        }
        try {
            creditService.refund(room.getUser().getId(),
                    ModelCatalog.costOf(room.getModel()), room.getId());
        } catch (Exception e) {
            log.warn("마디 환불 실패 (roomId={})", room.getId(), e);
        }
    }

    // 예외 종류에 따라 적절한 ErrorCode로 변환해 에러 이벤트를 보낸다.
    private void sendError(SseEmitter emitter, Exception e) {
        if (e instanceof BusinessException be) {
            sendError(emitter, be.getErrorCode());   // ROOM_BUSY / 방 소유권 / AI_UNAVAILABLE 등 그대로
        } else {
            // 옛 UncheckedIOException 분기(클라이언트 이탈)는 여기로 오지 않는다 — sendToken이
            // 더 이상 던지지 않고 플래그만 내린다. 남은 건 진짜 예기치 못한 오류뿐이다.
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
