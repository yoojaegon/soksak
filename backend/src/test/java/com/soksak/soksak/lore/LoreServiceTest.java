package com.soksak.soksak.lore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoreServiceTest {

    @Mock LoreRepository loreRepository;
    @Mock com.soksak.soksak.character.CharacterRepository characterRepository;

    @InjectMocks LoreService loreService;

    private Lore lore(String keys, String content, boolean alwaysOn) {
        return Lore.builder()
                .character(null)
                .keys(keys)
                .content(content)
                .alwaysOn(alwaysOn)
                .build();
    }

    @Test
    @DisplayName("alwaysOn 로어는 키워드 매칭과 무관하게 항상 주입된다")
    void selectLore_alwaysOn() {
        when(loreRepository.findByCharacter_IdAndEnabledTrueOrderByIdAsc(1L))
                .thenReturn(List.of(lore(null, "세계관 설명", true)));

        List<String> result = loreService.selectLore(1L, "안녕", List.of());

        assertThat(result).containsExactly("세계관 설명");
    }

    @Test
    @DisplayName("키워드가 유저 메시지나 최근 대화에 등장하면 주입된다 (대소문자 무시·콤마 다중키)")
    void selectLore_keywordMatch() {
        when(loreRepository.findByCharacter_IdAndEnabledTrueOrderByIdAsc(1L))
                .thenReturn(List.of(
                        lore("마법, Dragon", "용 설정", false),   // recent에서 Dragon 매칭
                        lore("검", "검 설정", false)              // userMessage에서 검 매칭
                ));

        List<String> result = loreService.selectLore(1L, "이 검은 뭐야?", List.of("저기 dragon이 있다"));

        assertThat(result).containsExactly("용 설정", "검 설정");
    }

    @Test
    @DisplayName("키워드가 안 맞거나 keys가 비어있는 비-alwaysOn 로어는 제외된다")
    void selectLore_noMatchExcluded() {
        when(loreRepository.findByCharacter_IdAndEnabledTrueOrderByIdAsc(1L))
                .thenReturn(List.of(
                        lore("드래곤", "용 설정", false),  // 매칭 안 됨
                        lore(null, "키 없음", false),     // keys 없는 비-alwaysOn
                        lore("  ", "공백 키", false)       // 공백 키
                ));

        List<String> result = loreService.selectLore(1L, "안녕", List.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("주입 결과는 레포 조회 순서(만든 순)를 그대로 유지한다")
    void selectLore_keepsOrder() {
        when(loreRepository.findByCharacter_IdAndEnabledTrueOrderByIdAsc(1L))
                .thenReturn(List.of(
                        lore(null, "첫째", true),
                        lore(null, "둘째", true),
                        lore(null, "셋째", true)
                ));

        List<String> result = loreService.selectLore(1L, "x", List.of());

        assertThat(result).containsExactly("첫째", "둘째", "셋째");
    }
}