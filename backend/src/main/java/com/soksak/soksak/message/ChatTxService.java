package com.soksak.soksak.message;

import com.soksak.soksak.aiClient.dto.SummarizeResponse;
import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.ChatRoomRepository;
import com.soksak.soksak.chatRoom.ChatRoomService;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummaryRepository;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
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

    @Transactional
    public PreparedChat prepareAndSaveUser(String loginId, Long roomId, String content) {
        ChatRoom room = chatRoomService.getOwnedChatRoomWithDetails(loginId, roomId);
        List<Message> priorHistory = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        List<ChatSummary> summaries = chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(roomId);

        messageRepository.save(Message.builder()
                .chatRoom(room)
                .role(MessageRole.USER)
                .content(content)
                .build());

        characterRepository.incrementChatCount(room.getCharacter().getId());

        return new PreparedChat(room, priorHistory, summaries);
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
        List<Message> messages = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
        if (messages.isEmpty()) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }
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

        List<Message> messages = messageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
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
