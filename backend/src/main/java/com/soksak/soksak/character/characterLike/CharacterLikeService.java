package com.soksak.soksak.character.characterLike;

import com.soksak.soksak.character.CharacterRepository;
import com.soksak.soksak.character.ChatCharacter;
import com.soksak.soksak.character.dto.CharacterResponse;
import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterLikeService {
    private final CharacterLikeRepository characterLikeRepository;
    private final CharacterRepository characterRepository;
    private final UserRepository userRepository;

    // 좋아요 등록. 이미 눌러둔 상태면 조용히 무시(중복 요청에 멱등하게 동작).
    // 좋아요 행 insert와 캐릭터 카운터 증가를 한 트랜잭션으로 묶어 둘이 어긋나지 않게 한다.
    @Transactional
    public void like(String loginId, Long characterId) {
        if (characterLikeRepository.existsByUser_LoginIdAndCharacter_id(loginId, characterId)) return;

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        ChatCharacter character = characterRepository.findById(characterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARACTER_NOT_FOUND));

        characterLikeRepository.save(CharacterLike.builder().user(user).character(character).build());
        characterRepository.incrementLikeCount(characterId);
    }

    // 좋아요 취소. 실제로 지워진 행이 있을 때만 카운터를 내려 없던 좋아요에 음수로 새는 걸 막는다.
    @Transactional
    public void unlike(String loginId, Long characterId) {
        int deleted = characterLikeRepository.deleteByUser_LoginIdAndCharacter_id(loginId, characterId);
        if (deleted > 0) {
            characterRepository.decrementLikeCount(characterId);
        }
    }

    // 내가 좋아요한 캐릭터 목록(최근에 누른 순). 캐릭터·작성자를 join fetch로 한 번에 가져온다.
    @Transactional(readOnly = true)
    public List<CharacterResponse> getLikedCharacters(String loginId) {
        return characterLikeRepository.findByUser_LoginId(loginId).stream()
                .map(cl -> CharacterResponse.from(cl.getCharacter()))
                .toList();
    }
}
