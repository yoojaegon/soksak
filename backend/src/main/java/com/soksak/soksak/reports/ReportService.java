package com.soksak.soksak.reports;

import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.chatRoom.ChatRoom;
import com.soksak.soksak.chatRoom.ChatRoomService;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.message.Message;
import com.soksak.soksak.message.MessageRepository;
import com.soksak.soksak.message.MessageRole;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 신고는 중복 확인 없이 매번 저장한다(증거·스냅샷이 매번 남게). 숨김 판정에서만 사람 단위로 센다 —
// 창 = max(캐릭터 마지막 수정, 지금 - windowDays) 안의 서로 다른 신고자 수 ≥ hideThreshold면 숨긴다.
// 한 사람이 기간을 두고 반복 신고해도 창 안에선 항상 1명이라 혼자서는 못 숨긴다.
// MINOR도 같은 기준이다 — 1건 즉시 숨김은 한 사람이 아무 캐릭터나 내릴 수 있어 신고 남용에 열려 있다.
// 관리자 화면이 없는 동안은 숨김·MINOR의 warn 로그가 운영자 알림 역할을 한다.
@Service
@Slf4j
public class ReportService {
    private final ReportRepository reportRepository;
    private final CharacterRepository characterRepository;
    private final UserRepository userRepository;
    private final ChatRoomService chatRoomService;
    private final MessageRepository messageRepository;
    private final int hideThreshold;
    private final int windowDays;

    public ReportService(
            ReportRepository reportRepository,
            CharacterRepository characterRepository,
            UserRepository userRepository,
            ChatRoomService chatRoomService,
            MessageRepository messageRepository,
            @Value("${reports.hide-threshold:30}") int hideThreshold,
            @Value("${reports.window-days:30}") int windowDays
    ) {
        this.reportRepository = reportRepository;
        this.characterRepository = characterRepository;
        this.userRepository = userRepository;
        this.chatRoomService = chatRoomService;
        this.messageRepository = messageRepository;
        this.hideThreshold = hideThreshold;
        this.windowDays = windowDays;
    }

    @Transactional
    public void reportCharacter(String loginId, Long characterId, ReportTarget target, ReportReason reason, String detail) {
        if (target == ReportTarget.CHAT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);   // 채팅 신고는 메시지 경로로만
        }
        ChatCharacter character = characterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARACTER_NOT_FOUND));
        if (character.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.REPORT_OWN_CHARACTER);
        }
        User reporter = findUser(loginId);

        reportRepository.save(Report.builder()
                .reporter(reporter)
                .character(character)
                .target(target)
                .reason(reason)
                .detail(detail)
                .contentSnapshot(snapshot(character, target))
                .build());

        if (reason == ReportReason.MINOR) {
            log.warn("캐릭터 MINOR 신고 characterId={} target={}", character.getId(), target);
        }
        if (character.isHidden()) {
            return;
        }
        LocalDateTime since = windowStart(character);
        long reporters = reportRepository.countReportersSince(character.getId(), since);
        if (reporters >= hideThreshold) {
            hide(character, "신고 누적", reporters);
        }
    }

    @Transactional
    public void reportMessage(String loginId, Long roomId, Long messageId, ReportReason reason, String detail) {
        ChatRoom room = chatRoomService.getOwnedChatRoomWithDetails(loginId, roomId);
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        if (!message.getChatRoom().getId().equals(roomId)) {
            throw new BusinessException(ErrorCode.MESSAGE_FORBIDDEN);
        }
        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new BusinessException(ErrorCode.REPORT_NOT_ASSISTANT);
        }

        // 재생성·되돌리기로 메시지가 바뀌거나 지워져도 무엇을 신고했는지 남도록 앞뒤를 복사해 둔다.
        String userTurn = messageRepository
                .findFirstByChatRoomIdAndIdLessThanAndRoleOrderByIdDesc(roomId, messageId, MessageRole.USER)
                .map(Message::getContent)
                .orElse("");
        reportRepository.save(Report.builder()
                .reporter(room.getUser())
                .character(room.getCharacter())
                .messageId(messageId)
                .target(ReportTarget.CHAT)
                .reason(reason)
                .detail(detail)
                .contentSnapshot("[USER]\n" + userTurn + "\n\n[ASSISTANT]\n" + message.getContent())
                .build());

        // 채팅 응답은 우리 파이프라인이 만든 것이라 캐릭터를 숨기지 않는다. MINOR만 운영자가 바로 보게 남긴다.
        if (reason == ReportReason.MINOR) {
            log.warn("채팅 응답 MINOR 신고 characterId={} messageId={}", room.getCharacter().getId(), messageId);
        }
    }

    private LocalDateTime windowStart(ChatCharacter character) {
        LocalDateTime edited = (character.getContentUpdatedAt() != null)
                ? character.getContentUpdatedAt()
                : character.getCreatedAt();
        LocalDateTime windowFloor = LocalDateTime.now().minusDays(windowDays);
        return edited.isAfter(windowFloor) ? edited : windowFloor;
    }

    private void hide(ChatCharacter character, String why, long reporters) {
        character.hide();
        log.warn("캐릭터 자동 숨김 characterId={} 사유={} 신고자수={}", character.getId(), why, reporters);
    }

    // 신고 뒤 제작자가 고쳐도 당시 무엇이 문제였는지 알 수 있게 대상 부분을 복사해 둔다.
    private static String snapshot(ChatCharacter character, ReportTarget target) {
        if (target == ReportTarget.IMAGE) {
            return character.getImageUrl();
        }
        return "[이름] " + character.getName()
                + "\n[설명] " + character.getDescription()
                + "\n[설정]\n" + character.getPersona()
                + "\n[첫 인사]\n" + character.getGreeting();
    }

    private User findUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
