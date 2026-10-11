package com.loopers.domain.like;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeServiceTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Register {
        @DisplayName("관계를 저장한다.")
        @Test
        void savesLike() {
            // act
            likeService.like(1L, 10L);

            // assert
            assertThat(likeRepository.exists(1L, 10L)).isTrue();
        }

        @DisplayName("같은 관계를 다시 등록해도 성공하고, 관계는 하나만 유지된다.")
        @Test
        void keepsSingleLike_whenLikedTwice() {
            // act
            likeService.like(1L, 10L);
            likeService.like(1L, 10L);

            // assert
            assertThat(likeService.count(10L)).isEqualTo(1);
        }

        @DisplayName("다른 사용자의 좋아요는 각각 센다.")
        @Test
        void countsEachUser() {
            // act
            likeService.like(1L, 10L);
            likeService.like(2L, 10L);

            // assert
            assertThat(likeService.count(10L)).isEqualTo(2);
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {
        @DisplayName("관계를 지운다.")
        @Test
        void removesLike() {
            // arrange
            likeService.like(1L, 10L);

            // act
            likeService.unlike(1L, 10L);

            // assert
            assertThat(likeRepository.exists(1L, 10L)).isFalse();
            assertThat(likeService.count(10L)).isZero();
        }

        @DisplayName("관계가 없어도 성공하고, 다른 관계는 그대로다.")
        @Test
        void succeeds_whenLikeDoesNotExist() {
            // arrange
            likeService.like(2L, 10L);

            // act
            likeService.unlike(1L, 10L);

            // assert
            assertThat(likeService.count(10L)).isEqualTo(1);
        }
    }

    @DisplayName("상품의 좋아요 수를 셀 때,")
    @Nested
    class Count {
        @DisplayName("관계가 없으면 0이다.")
        @Test
        void returnsZero_whenNoLikes() {
            assertThat(likeService.count(10L)).isZero();
        }
    }
}
