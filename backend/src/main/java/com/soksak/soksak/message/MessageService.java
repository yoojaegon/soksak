package com.soksak.soksak.message;

import com.soksak.soksak.chatRoom.ChatRoomService;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.aiClient.ChatAiClient;
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
            String reply = chatAiClient.reply(preparedChat.room(), content, preparedChat.priorHistory());
            Message aiMessage = chatTxService.saveAssistant(roomId, reply);
            return MessageResponse.from(aiMessage);
        });
    }

    public SseEmitter sendMessageStream(String loginId, Long roomId, String content) {
        return startStream(roomId, () -> {
            PreparedChat p = chatTxService.prepareAndSaveUser(loginId, roomId, content);
            return new StreamJob(p.room(), content, p.priorHistory());
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
            String reply = chatAiClient.reply(t.room(), t.lastUserContent(), t.priorHistory());
            Message ai = chatTxService.saveAssistant(roomId, reply);
            return MessageResponse.from(ai);
        });
    }

    public SseEmitter regenerateStream(String loginId, Long roomId) {
        return startStream(roomId, () -> {
            RegenTarget t = chatTxService.prepareRegenerate(loginId, roomId);
            return new StreamJob(t.room(), t.lastUserContent(), t.priorHistory());
        });
    }

    @Transactional
    public void deleteFrom(String loginId, Long roomId, Long messageId) {
        chatRoomService.getOwnedChatRoom(loginId, roomId);
        Message target = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!target.getChatRoom().getId().equals(roomId)) {
            throw new BusinessException(ErrorCode.MESSAGE_FORBIDDEN);
        }
        List<Message> messages = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);;

        int idx = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(messageId)){
                idx = i;
                break;
            }
        }

        messageRepository.deleteAll(messages.subList(idx, messages.size()));
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
                        job.room(), job.content(), job.priorHistory(),
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
            // 이미 끊겼거나 완료된 경우 — 더 보낼 수 없으니 에러로 마무리만.
            emitter.completeWithError(ex);
        }
    }
}
