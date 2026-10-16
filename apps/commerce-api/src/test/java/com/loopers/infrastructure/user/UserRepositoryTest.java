package com.loopers.infrastructure.user;

import com.loopers.domain.common.Money;
import com.loopers.domain.user.PointBalance;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("사용자를 저장하고 조회할 때,")
    @Nested
    class SaveAndFind {
        @DisplayName("저장한 포인트 잔액이 그대로 조회된다.")
        @Test
        void returnsSavedBalance_whenUserIsSaved() {
            // arrange
            User user = User.create();
            user.chargePoint(new Money(10_000));

            // act
            User saved = userRepository.save(user);
            Optional<User> found = userRepository.find(saved.getId());

            // assert
            assertThat(found).isPresent();
            assertThat(found.get().getPointBalance()).isEqualTo(new PointBalance(new Money(10_000)));
        }

        @DisplayName("존재하지 않는 id 이면 빈 결과를 반환한다.")
        @Test
        void returnsEmpty_whenUserDoesNotExist() {
            // act
            Optional<User> found = userRepository.find(999L);

            // assert
            assertThat(found).isEmpty();
        }
    }
}
