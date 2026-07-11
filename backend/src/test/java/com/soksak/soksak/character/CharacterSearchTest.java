package com.soksak.soksak.character;

import com.soksak.soksak.character.dto.CharacterResponse;
import com.soksak.soksak.common.Gender;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 캐릭터 검색/필터 쿼리 검증.
// - #4: 태그(enum @ElementCollection) 필터가 실제로 동작하는지(member of 바인딩).
// - #2: 검색어의 LIKE 메타문자(%,_)가 리터럴로 취급되는지(이스케이프).
// 시드한 유저·캐릭터가 DB에 남아 재실행 시 loginId 유니크 충돌이 나지 않도록 @Transactional로 롤백한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CharacterSearchTest {

    @Autowired CharacterService characterService;
    @Autowired CharacterRepository characterRepository;
    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("#4 태그 필터: 해당 장르를 가진 캐릭터만 반환한다")
    void filterByTag() {
        User owner = seedUser("tag_owner");
        seedCharacter(owner, "로맨스캐릭", "설명", Set.of(Genre.ROMANCE, Genre.COMEDY));
        seedCharacter(owner, "판타지캐릭", "설명", Set.of(Genre.FANTASY));

        Page<CharacterResponse> page =
                characterService.getCharacters(null, Genre.ROMANCE, PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(CharacterResponse::characterName)
                .contains("로맨스캐릭")
                .doesNotContain("판타지캐릭");
    }

    @Test
    @DisplayName("#2 검색어의 %는 와일드카드가 아니라 리터럴로 매칭된다")
    void escapesLikeWildcards() {
        User owner = seedUser("like_owner");
        seedCharacter(owner, "100%세일", "설명", Set.of());
        seedCharacter(owner, "1004번", "설명", Set.of());

        // 이스케이프가 없으면 "100%"가 %100%%로 풀려 "1004번"까지 잡힌다.
        Page<CharacterResponse> page =
                characterService.getCharacters("100%", null, PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(CharacterResponse::characterName)
                .contains("100%세일")
                .doesNotContain("1004번");
    }

    private User seedUser(String loginId) {
        return userRepository.save(User.builder()
                .loginId(loginId)
                .email(loginId + "@test.com")
                .nickname(loginId)
                .password("pw")
                .age(20)
                .gender(Gender.MALE)
                .build());
    }

    private void seedCharacter(User user, String name, String description, Set<Genre> tags) {
        characterRepository.save(ChatCharacter.builder()
                .user(user)
                .name(name)
                .description(description)
                .persona("페르소나")
                .greeting("안녕하세요")
                .tags(tags)
                .build());
    }
}
