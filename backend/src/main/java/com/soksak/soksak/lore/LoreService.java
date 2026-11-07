package com.soksak.soksak.lore;

import com.soksak.soksak.character.CharacterService;
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
    private final CharacterService characterService;

    /** 본인 캐릭터에 로어 엔트리를 추가한다. */
    @Transactional
    public LoreResponse createLore(Long userId, Long characterId, CreateLoreRequest request) {
        ChatCharacter character = characterService.getOwnedCharacter(userId, characterId);
        Lore lore = Lore.builder()
                .character(character)
                .title(request.title())
                .keys(request.keys())
                .content(request.content())
                .alwaysOn(request.alwaysOn())
                .build();
        return LoreResponse.from(loreRepository.save(lore));
    }

    /** 캐릭터의 로어 전부를 만든 순서로 조회한다. */
    @Transactional(readOnly = true)
    public List<LoreResponse> getLoreList(Long userId, Long characterId) {
        characterService.getOwnedCharacter(userId, characterId);
        return loreRepository.findByCharacter_IdOrderByIdAsc(characterId).stream()
                .map(LoreResponse::from)
                .toList();
    }

    /** 로어 내용을 수정한다. */
    @Transactional
    public LoreResponse updateLore(Long userId, Long characterId, Long id, UpdateLoreRequest request) {
        Lore lore = getOwnedLore(userId, characterId, id);
        lore.update(request.title(), request.keys(), request.content(), request.alwaysOn());
        return LoreResponse.from(lore);
    }

    /** 로어를 켜거나 끈다. */
    @Transactional
    public LoreResponse updateEnabled(Long userId, Long characterId, Long id, boolean enabled) {
        Lore lore = getOwnedLore(userId, characterId, id);
        lore.updateEnabled(enabled);
        return LoreResponse.from(lore);
    }

    /** 로어를 삭제한다. */
    @Transactional
    public void deleteLore(Long userId, Long characterId, Long id) {
        Lore lore = getOwnedLore(userId, characterId, id);
        loreRepository.delete(lore);
    }

    /**
     * 캐릭터가 본인 것인지, 로어가 그 캐릭터에 속하는지 둘 다 확인하고 로어를 반환한다.
     * 로어 소속을 따로 보지 않으면 내 캐릭터 주소에 남의 로어 id를 넣어 고칠 수 있다.
     */
    private Lore getOwnedLore(Long userId, Long characterId, Long id) {
        characterService.getOwnedCharacter(userId, characterId);
        Lore lore = loreRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LORE_NOT_FOUND));
        if (!lore.getCharacter().getId().equals(characterId)) {
            throw new BusinessException(ErrorCode.LORE_NOT_FOUND);
        }
        return lore;
    }

    /**
     * 이번 턴 프롬프트에 넣을 로어 내용을 고른다.
     * 켜져 있는 로어 중 항상 적용이거나, 키워드가 이번 입력이나 최근 대화에 나온 것만 만든 순서대로 돌려준다.
     */
    @Transactional(readOnly = true)
    public List<String> selectLore(Long characterId, String content, List<String> recent) {
        String scan = (content + " " + String.join(" ", recent)).toLowerCase(Locale.ROOT);

        return loreRepository.findByCharacter_IdAndEnabledTrueOrderByIdAsc(characterId).stream()
                .filter(lore -> lore.isAlwaysOn() || matches(lore.getKeys(), scan))
                .map(Lore::getContent)
                .toList();
    }

    /** 쉼표로 나눈 키워드 중 하나라도 대화에 들어 있는지 본다. 대소문자는 구분하지 않는다. */
    private boolean matches(String keys, String scan) {
        if (keys == null || keys.isBlank()) return false;
        return Arrays.stream(keys.split(","))
                .map(k -> k.trim().toLowerCase(Locale.ROOT))
                .filter(k -> !k.isEmpty())
                .anyMatch(scan::contains);
    }
}
