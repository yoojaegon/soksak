package com.soksak.soksak.message;

import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.ChatRoomRepository;
import com.soksak.soksak.chatRoom.ChatRoomService;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummaryRepository;
import com.soksak.soksak.aiClient.ModelCatalog;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.credit.CreditReason;
import com.soksak.soksak.credit.CreditService;
import com.soksak.soksak.message.dto.DeleteFromResponse;
import com.soksak.soksak.message.dto.RegenTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTxService {
    private final ChatRoomService chatRoomService;
    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;
    private final CharacterRepository characterRepository;
    private final ChatSummaryRepository chatSummaryRepository;
    private final CreditService creditService;

    @Transactional
    public PreparedChat prepareAndSaveUser(String loginId, Long roomId, String content) {
        ChatRoom room = chatRoomService.getOwnedChatRoomWithDetails(loginId, roomId);

        // 값을 먼저 치른다. USER 메시지 저장과 한 트랜잭션이라 "보냈는데 안 깎였다"나 그 반대가
        // 생기지 않는다. 사용자가 중간에 나가도 생성·저장은 끝까지 가므로(MessageService.sendToken)
        // 차감한 만큼의 답은 방에 남는다 — 환불이 필요한 건 답이 아예 안 나온 경우뿐이다.
        charge(room, CreditReason.MESSAGE);

        List<Message> priorHistory = messageRepository.findByChatRoomIdOrderByIdAsc(roomId);
        List<ChatSummary> summaries = chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId);

        messageRepository.save(Message.builder()
                .chatRoom(room)
                .role(MessageRole.USER)
                .content(content)
                .build());

        characterRepository.incrementChatCount(room.getCharacter().getId());

        return new PreparedChat(room, priorHistory, summaries);
    }

    /**
     * 이 방의 모델 계수만큼 마디를 깎는다.
     * <p>
     * 계수의 출처는 카탈로그 하나다 — 방이 고른 모델이 {@code null}이거나 카탈로그에서 빠졌으면
     * {@code costOf}가 기본 모델 값으로 보는데, 실제로 발송되는 모델도 {@code resolve()}의
     * 기본값이라 둘이 같은 곳을 가리킨다.
     * <p>
     * ⚠️ 요약({@code /summarize})은 여기로 오지 않는다. 사용자가 유발한 호출이 아니라서
     * 물리면 "아무것도 안 했는데 줄었다"가 된다 — 정량제 단계에서는 무료다.
     */
    private void charge(ChatRoom room, CreditReason reason) {
        creditService.charge(room.getUser(), ModelCatalog.costOf(room.getModel()), reason, room.getId());
    }

    @Transactional
    public Message saveAssistant(Long roomId, String reply) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        Message aiMessage = messageRepository.save(Message.builder()
                .chatRoom(room)
                .role(MessageRole.ASSISTANT)
                .content(reply)
                .build());

        return aiMessage;
    }

    @Transactional
    public ChatSummary appendSummary(Long roomId, SummaryPlan plan, SummarizeResponse result) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        ChatSummary saved = chatSummaryRepository.save(ChatSummary.builder()
                .chatRoom(room)
                .seq(plan.nextSeq())
                .fromMessageId(plan.fromId())
                .toMessageId(plan.upToId())
                .content(result.summary())
                .importance(result.importance())
                .keywords(result.keywords())
                .estimatedTokens(result.tokenCount())
                .build());

        return saved;
    }

    @Transactional
    public RegenTarget prepareRegenerate(String loginId, Long roomId) {
        ChatRoom room = chatRoomService.getOwnedChatRoomWithDetails(loginId, roomId);
        List<Message> messages = messageRepository.findByChatRoomIdOrderByIdAsc(roomId);
        if (messages.isEmpty()) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        // 재생성도 LLM 호출이 온전히 한 번 더 나가므로 전송과 같이 받는다.
        // 위치는 읽는 순서일 뿐이다 — 이 메서드가 통째로 한 트랜잭션이라 아래에서 예외가 나도
        // 차감은 같이 롤백된다. 차감을 트랜잭션 밖으로 빼지만 않으면 순서는 안전에 무관하다.
        charge(room, CreditReason.REGENERATE);
        // 재생성도 프롬프트를 다시 만드므로 조각이 필요하다. 전송 경로(prepareAndSaveUser)와 달리
        // 여기만 조회가 하나 는다 — 재생성은 요약을 굴리지 않아 원래 읽을 일이 없었다.
        List<ChatSummary> summaries = chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId);

        Message last = messages.get(messages.size() - 1);
        Message lastUser;
        List<Message> priorHistory;

        if(last.getRole() == MessageRole.ASSISTANT) {
            // 직전 메시지가 USER라는 전제를 방어적으로 검증 (언더플로/역할 불일치 시 예외)
            if (messages.size() < 2) {
                throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
            }
            lastUser = messages.get(messages.size() - 2);
            if (lastUser.getRole() != MessageRole.USER) {
                throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
            }
            messageRepository.delete(last);
            priorHistory = messages.subList(0, messages.size() - 2);
        } else{
            lastUser = last;
            priorHistory = messages.subList(0, messages.size() - 1);
        }
        return new RegenTarget(room, lastUser.getContent(), List.copyOf(priorHistory), summaries);
    }

    @Transactional
    public DeleteFromResponse deleteFrom(String loginId, Long roomId, Long messageId) {
        chatRoomService.getOwnedChatRoom(loginId, roomId);   // 소유권 검증
        Message target = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));

        if(!target.getChatRoom().getId().equals(roomId)) {
            throw new BusinessException(ErrorCode.MESSAGE_FORBIDDEN);
        }

        List<Message> messages = messageRepository.findByChatRoomIdOrderByIdAsc(roomId);
        int idx = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(messageId)) { idx = i; break; }
        }

        Long cut = target.getId();
        messageRepository.deleteAll(messages.subList(idx, messages.size()));
        // 잘린 지점에 걸치거나(from < cut <= to) 그 뒤에 있는 조각을 함께 지운다.
        long removed = chatSummaryRepository.deleteByChatRoomIdAndToMessageIdGreaterThanEqual(roomId, cut);

        return new DeleteFromResponse(removed > 0);
    }
}
