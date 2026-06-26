package com.soksak.soksak.lore;

import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.lore.dto.CreateLoreRequest;
import com.soksak.soksak.lore.dto.LoreResponse;
import com.soksak.soksak.lore.dto.UpdateLoreRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoreService {
    private final LoreRepository loreRepository;
    private final CharacterRepository characterRepository;

    /** 본인 캐릭터에 로어 엔트리를 추가한다. */
    @Transactional
    public LoreResponse createLore(String loginId, Long characterId, CreateLoreRequest request) {
        ChatCharacter character = getOwnedCharacter(loginId, characterId);
        Lore lore = Lore.builder()
                .character(character)
                .title(request.title())
                .keys(request.keys())
                .content(request.content())
                .alwaysOn(request.alwaysOn())
                .priority(request.priority())
                .build();   // enabled는 생성 시 true (엔티티 빌더 기본값)
        return LoreResponse.from(loreRepository.save(lore));
    }

    // 로어북 목록을 불러온다.
    @Transactional(readOnly = true)
    public List<LoreResponse> getLoreList(String loginId, Long characterId) {
        getOwnedCharacter(loginId, characterId);
        return loreRepository.findByCharacter_IdOrderById(characterId).stream()
                .map(LoreResponse::from)
                .toList();
    }

    // 로어북 단일 호출
    @Transactional(readOnly = true)
    public LoreResponse getLore(String loginId, Long characterId, Long id) {
        return LoreResponse.from(getOwnedLore(loginId, characterId, id));
    }

    // 로어북 수정
    @Transactional
    public LoreResponse updateLore(String loginId, Long characterId, Long id, UpdateLoreRequest request) {
        Lore lore = getOwnedLore(loginId, characterId, id);
        lore.update(request.title(), request.keys(), request.content(), request.alwaysOn(), request.priority());
        return LoreResponse.from(lore);
    }

    // 로어북 활성화 토글 수정
    @Transactional
    public LoreResponse updateEnabled(String loginId, Long characterId, Long id, boolean enabled) {
        Lore lore = getOwnedLore(loginId, characterId, id);
        lore.updateEnabled(enabled);
        return LoreResponse.from(lore);
    }

    @Transactional
    public void deleteLore(String loginId, Long characterId, Long id) {
        Lore lore = getOwnedLore(loginId, characterId, id);
        loreRepository.delete(lore);
    }

    /** 캐릭터가 존재하고 로그인한 본인 소유인지 검증 후 반환한다. */
    private ChatCharacter getOwnedCharacter(String loginId, Long characterId) {
        ChatCharacter character = characterRepository.findById(characterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARACTER_NOT_FOUND));
        if (!character.getUser().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.CHARACTER_FORBIDDEN);
        }
        return character;
    }

    /** 본인 캐릭터 소유를 검증하고, 그 캐릭터에 속한 로어를 찾아 반환한다. */
    private Lore getOwnedLore(String loginId, Long characterId, Long id) {
        getOwnedCharacter(loginId, characterId);
        Lore lore = loreRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LORE_NOT_FOUND));
        if (!lore.getCharacter().getId().equals(characterId)) {
            throw new BusinessException(ErrorCode.LORE_NOT_FOUND);
        }
        return lore;
    }

    // 대화에서 로어북 활성화 키워드를 찾거나 항상 활성화 되어있는 로어북을 반환한다.
    @Transactional(readOnly = true)
    public List<String> selectLore(Long character_id, String content, List<String> recent) {
        String scan = (content + " " + String.join(" ", recent)).toLowerCase(Locale.ROOT);

        return loreRepository.findByCharacter_IdAndEnabledTrueOrderByPriorityAsc(character_id).stream()
                .filter(lore -> lore.isAlwaysOn() || matches(lore.getKeys(), scan))
                .map(Lore::getContent)
                .toList();
    }


    // 문자열에서 콤마로 구분된 활성화 키가 있는지 검색
    private boolean matches(String keys, String scan) {
        if (keys == null || keys.isBlank()) return false;
        return Arrays.stream(keys.split(","))
                .map(k -> k.trim().toLowerCase(Locale.ROOT))
                .filter(k -> !k.isEmpty())
                .anyMatch(scan::contains);
    }
}
