package com.soksak.soksak.chatRoom;

import com.soksak.soksak.aiClient.ModelCatalog;
import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummary;
import com.soksak.soksak.chatRoom.chatSummary.ChatSummaryRepository;
import com.soksak.soksak.chatRoom.chatSummary.SummarySelector;
import com.soksak.soksak.chatRoom.dto.*;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.message.Message;
import com.soksak.soksak.message.MessageRepository;
import com.soksak.soksak.message.MessageRole;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatRoomService {
    // 수동으로 쓴 기억은 조각을 전부 지우고 하나만 남기므로 항상 첫 seq다.
    private static final int MANUAL_SEQ = 1;

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final MessageRepository messageRepository;
    private final ChatSummaryRepository chatSummaryRepository;

    @Transactional
    public ChatRoom createChatRoom(String loginId, CreateChatRoomRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        ChatCharacter character = characterRepository.findById(request.characterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARACTER_NOT_FOUND));
        // 기존 방은 계속 쓰게 두고 새 방만 막는다 — 신고 누적이 곧 위반 확정은 아니라서.
        if (character.isHidden()) {
            throw new BusinessException(ErrorCode.CHARACTER_HIDDEN);
        }

        ChatRoom chatRoom = chatRoomRepository.save(
                ChatRoom.builder()
                    .user(user)
                    .character(character)
                    .title(nextRoomTitle(loginId, character))
                    .build());

        messageRepository.save(Message.builder()
                .chatRoom(chatRoom)
                .role(MessageRole.ASSISTANT)
                .content(character.getGreeting())
                .build());
        return chatRoom;
    }

    // 같은 사용자가 같은 캐릭터로 방을 여러 개 만들면 제목을 자동으로 넘버링한다.
    // 첫 방은 캐릭터 이름 그대로, 그다음부터 "이름2", "이름3" … 식.
    // 이미 쓰고 있는 제목은 건너뛰어 중간 방을 지워도 제목이 겹치지 않는다.
    private String nextRoomTitle(String loginId, ChatCharacter character) {
        String base = character.getName();
        Set<String> used = new HashSet<>(
                chatRoomRepository.findTitlesByUserAndCharacter(loginId, character.getId()));

        if (!used.contains(base)) {
            return base;
        }
        int n = 2;
        while (used.contains(base + n)) {
            n++;
        }
        return base + n;
    }

    @Transactional(readOnly = true)
    public ChatRoomResponse getChatRoom(String loginId, Long id) {
        return ChatRoomResponse.from(getOwnedChatRoom(loginId, id));
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getMyChatRooms(String loginId) {
        return chatRoomRepository.findByUser_LoginId(loginId).stream()
                .map(ChatRoomResponse::from)
                .toList();
    }

    @Transactional
    public ChatRoomResponse updateChatRoom(String loginId, Long id, UpdateChatRoomRequest request) {
        ChatRoom chatRoom = getOwnedChatRoom(loginId, id);
        chatRoom.update(request.title());

        return ChatRoomResponse.from(chatRoom);
    }

    @Transactional
    public ChatRoomResponse updateModels(String loginId, Long id, UpdateModelRequest request) {
        ChatRoom chatRoom = getOwnedChatRoom(loginId, id);
        if (!ModelCatalog.contains(request.model()))
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        chatRoom.updateModel(request.model());

        return ChatRoomResponse.from(chatRoom);
    }

    /**
     * 추론 깊이. 모델이 그 레벨을 받을 수 있는지는 <b>여기서 막지 않는다</b> — 모델과 레벨은
     * 따로 바뀌므로(추론 없는 모델로 교체 등) 저장 단계에서 거절하면 모델을 못 바꾸게 된다.
     * 실제로 보낼 수 있는 값으로 맞추는 건 발송 직전의 ModelCatalog.resolveThinking() 몫이다.
     */
    @Transactional
    public ChatRoomResponse updateThinking(String loginId, Long id, UpdateThinkingRequest request) {
        ChatRoom chatRoom = getOwnedChatRoom(loginId, id);
        chatRoom.updateThinkingLevel(request.thinkingLevel());

        return ChatRoomResponse.from(chatRoom);
    }

    @Transactional
    public ChatRoomResponse updateConfig(String loginId, Long id, boolean writingToggle, boolean foldSpoilerToggle) {
        ChatRoom chatRoom = getOwnedChatRoom(loginId, id);
        chatRoom.toggleUpdate(writingToggle, foldSpoilerToggle);

        return ChatRoomResponse.from(chatRoom);
    }

    @Transactional
    public void deleteChatRoom(String loginId, Long id) {
        ChatRoom chatRoom = getOwnedChatRoom(loginId, id);
        chatRoomRepository.delete(chatRoom);
    }

    public ChatRoom getOwnedChatRoom(String loginId, Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        if (!chatRoom.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.CHATROOM_FORBIDDEN);
        }
        return chatRoom;
    }

    public ChatRoom getOwnedChatRoomWithDetails(String loginId, Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findByWithDetails(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));
        if (!chatRoom.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.CHATROOM_FORBIDDEN);
        }
        return chatRoom;
    }

    // 조각 전부가 아니라 '실제로 프롬프트에 실리는 것'만 돌려준다. 전부 보여주면 예산에 밀려
    // 안 쓰이는 문장까지 편집 상자에 뜨고, 그중 어느 게 실제로 기억되는지 알 길이 없어진다.
    @Transactional(readOnly = true)
    public SummaryResponse getSummary(String loginId, Long chatRoomId) {
        getOwnedChatRoom(loginId, chatRoomId);
        List<ChatSummary> summaries = chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(chatRoomId);
        return new SummaryResponse(SummarySelector.join(SummarySelector.select(summaries)));
    }

    /**
     * 사용자가 고친 기억으로 조각을 통째로 갈아 끼운다(빈 문자열이면 전부 삭제).
     * 프론트 계약은 그대로다 — 밖에서는 여전히 문자열 하나를 주고받는다.
     */
    @Transactional
    public SummaryResponse updateSummary(String loginId, Long chatRoomId, UpdateSummaryRequest request) {
        ChatRoom chatRoom = getOwnedChatRoom(loginId, chatRoomId);
        String summary = request.summary().trim();

        // 상한은 @Size(문자수)가 아니라 여기서 토큰으로 잰다. 방마다 예산이 달라질 수 있어
        // (A7) 컴파일 상수로는 표현할 수 없고, 표시·주입과 같은 자를 써야 "저장은 됐는데
        // GET에는 안 보이는 기억"이 안 생긴다.
        if (SummarySelector.estimateTokens(summary) > SummarySelector.TOKEN_BUDGET) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<ChatSummary> existing = chatSummaryRepository.findByChatRoomIdOrderBySeqAsc(chatRoomId);
        chatSummaryRepository.deleteByChatRoomId(chatRoomId);
        // ⚠️ 반드시 여기서 flush. Hibernate는 액션 순서가 정해져 있어 같은 트랜잭션 안의
        // INSERT를 DELETE보다 먼저 내보낸다. 그대로 두면 새 조각의 seq=1이 아직 안 지워진
        // 옛 조각과 부딪혀 (chat_room_id, seq) 유니크 위반으로 409가 난다.
        chatSummaryRepository.flush();

        if (summary.isEmpty()) {
            return new SummaryResponse(null);
        }

        // 수동 조각은 지워진 조각들이 덮던 구간을 그대로 물려받는다. 여기서 커서를 '지금'까지
        // 밀면 아직 요약 안 된 최근 대화가 요약된 것으로 취급돼 프롬프트에서 통째로 사라진다.
        // 요약된 적 없는 방이면 0..0 — 덮는 구간이 없다는 뜻이라 이력은 전부 그대로 실린다.
        ChatSummary saved = chatSummaryRepository.save(ChatSummary.builder()
                .chatRoom(chatRoom)
                .seq(MANUAL_SEQ)
                .fromMessageId(existing.isEmpty() ? 0L : existing.get(0).getFromMessageId())
                .toMessageId(SummarySelector.cursorOf(existing))
                .content(summary)
                // 손으로 쓴 기억은 예산에 밀려 조용히 빠지면 안 되므로 고정 후보로 넣는다.
                .importance(SummarySelector.PINNED_IMPORTANCE)
                .keywords(List.of())
                .estimatedTokens(SummarySelector.estimateTokens(summary))
                .build());

        return new SummaryResponse(saved.getContent());
    }
}
