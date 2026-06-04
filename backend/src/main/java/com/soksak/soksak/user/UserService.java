package com.soksak.soksak.user;

import com.soksak.soksak.user.dto.CreateUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public User createUser(CreateUserRequest request) {
        User user = User.builder()
                .email(request.getEmail())
                .loginId(request.getLoginId())
                .nickname(request.getNickname())
                .password(request.getPassword())
                .build();
        return userRepository.save(user);
    }
}
