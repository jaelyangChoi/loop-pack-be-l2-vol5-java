package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("사용자를 조회할 때,")
    @Nested
    class Get {
        @DisplayName("존재하는 사용자이면 해당 사용자를 반환한다.")
        @Test
        void returnsUser_whenUserExists() {
            // arrange
            User saved = userRepository.save(User.create());

            // act
            User found = userService.get(saved.getId());

            // assert
            assertThat(found.getId()).isEqualTo(saved.getId());
        }

        @DisplayName("존재하지 않는 사용자이면 NOT_FOUND 로 거절한다.")
        @Test
        void throwsNotFound_whenUserDoesNotExist() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> userService.get(999L));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
