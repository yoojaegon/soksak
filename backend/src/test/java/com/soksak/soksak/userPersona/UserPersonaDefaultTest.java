package com.soksak.soksak.userPersona;

import com.soksak.soksak.common.BusinessException;
import com.soksak.soksak.common.ErrorCode;
import com.soksak.soksak.common.Gender;
import com.soksak.soksak.user.User;
import com.soksak.soksak.user.UserRepository;
import com.soksak.soksak.userPersona.dto.CreateUserPersonaRequest;
import com.soksak.soksak.userPersona.dto.UserPersonaResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.soksak.soksak.TestcontainersConfig;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class UserPersonaDefaultTest {

    @Autowired UserPersonaService userPersonaService;
    @Autowired UserPersonaRepository userPersonaRepository;
    @Autowired UserRepository userRepository;

    private static final String LOGIN_ID = "persona-owner";

    private Long userId;

    @BeforeEach
    void setUp() {
        cleanUp();
        userId = userRepository.save(User.builder()
                .loginId(LOGIN_ID)
                .email("persona@soksak.com")
                .nickname("페르소나주인")
                .password("x")
                .birthYear(2000)
                .gender(Gender.MALE)
                .build()).getId();
    }

    // 다른 테스트가 남긴 행은 건드리지 않고, 이 테스트가 만든 유저와 페르소나만 지운다.
    // 페르소나가 남아 있으면 users를 지우는 다른 테스트가 FK에 걸린다.
    @AfterEach
    void cleanUp() {
        userRepository.findByLoginId(LOGIN_ID).ifPresent(user -> {
            userPersonaRepository.deleteAll(userPersonaRepository.findByUser_IdOrderByIdAsc(user.getId()));
            userRepository.delete(user);
        });
    }

    private UserPersonaResponse create(String name, Boolean isDefault) {
        return userPersonaService.createPersona(userId, new CreateUserPersonaRequest(name, "본문", isDefault));
    }

    private List<UserPersona> defaults() {
        return userPersonaRepository.findByUser_IdOrderByIdAsc(userId).stream()
                .filter(UserPersona::isDefault)
                .toList();
    }

    @Test
    @DisplayName("첫 페르소나는 isDefault를 보내지 않아도 기본이 된다")
    void firstIsAlwaysDefault() {
        assertThat(create("첫째", null).isDefault()).isTrue();
    }

    @Test
    @DisplayName("isDefault를 보내지 않으면 기존 기본은 그대로다")
    void notRequestedKeepsDefault() {
        UserPersonaResponse first = create("첫째", null);
        UserPersonaResponse second = create("둘째", false);

        assertThat(second.isDefault()).isFalse();
        assertThat(defaults()).extracting(UserPersona::getId).containsExactly(first.id());
    }

    @Test
    @DisplayName("isDefault=true로 만들면 새 페르소나가 기본이 되고 기존 기본은 해제된다")
    void requestedDefaultSwitches() {
        create("첫째", null);
        UserPersonaResponse second = create("둘째", true);

        assertThat(second.isDefault()).isTrue();
        assertThat(defaults()).extracting(UserPersona::getId).containsExactly(second.id());
    }

    @Test
    @DisplayName("기본 지정과 기본으로 만들기가 동시에 와도 기본은 하나만 남는다")
    void concurrentDefaultChangesKeepOneDefault() throws Exception {
        create("첫째", null);
        UserPersonaResponse second = create("둘째", false);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int n = i;
            tasks.add(() -> userPersonaService.setDefault(userId, second.id()));
            tasks.add(() -> create("동시" + n, true));
        }
        runTogether(tasks);

        assertThat(defaults()).hasSize(1);
    }

    @Test
    @DisplayName("남은 두 개를 동시에 지워도 하나는 남는다")
    void concurrentDeletesKeepLastOne() throws Exception {
        UserPersonaResponse first = create("첫째", null);
        UserPersonaResponse second = create("둘째", false);

        List<Object> results = runTogether(List.of(
                () -> { userPersonaService.deletePersona(userId, first.id()); return "ok"; },
                () -> { userPersonaService.deletePersona(userId, second.id()); return "ok"; }
        ));

        assertThat(userPersonaRepository.findByUser_IdOrderByIdAsc(userId)).hasSize(1);
        assertThat(defaults()).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof BusinessException be
                && be.getErrorCode() == ErrorCode.USER_PERSONA_LAST_ONE).hasSize(1);
    }

    // 작업들을 출발선에 세워 두고 한꺼번에 출발시킨다. 결과는 반환값 또는 던진 예외다.
    private List<Object> runTogether(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }
}
