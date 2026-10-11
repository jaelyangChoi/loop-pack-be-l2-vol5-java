package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeRepositoryTest {

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void saveLike(Long userId, Long productId) {
        likeRepository.save(Like.create(userId, productId));
    }

    @DisplayName("좋아요를 저장하고 확인할 때,")
    @Nested
    class SaveAndExists {
        @DisplayName("저장한 관계가 있다고 확인된다.")
        @Test
        void returnsTrue_whenLikeIsSaved() {
            // arrange
            saveLike(1L, 10L);

            // act & assert
            assertThat(likeRepository.exists(1L, 10L)).isTrue();
        }

        @DisplayName("저장하지 않은 관계는 없다고 확인된다. 사용자나 상품이 다르면 다른 관계다.")
        @Test
        void returnsFalse_whenLikeIsNotSaved() {
            // arrange
            saveLike(1L, 10L);

            // act & assert
            assertThat(likeRepository.exists(2L, 10L)).isFalse();
            assertThat(likeRepository.exists(1L, 11L)).isFalse();
        }
    }

    @DisplayName("좋아요를 삭제할 때,")
    @Nested
    class Delete {
        @DisplayName("삭제한 행 수를 반환하고, 이후에는 관계가 없다. 이미 없으면 0을 반환한다.")
        @Test
        void returnsDeletedRowCount() {
            // arrange
            saveLike(1L, 10L);

            // act
            int first = likeRepository.delete(1L, 10L);
            int second = likeRepository.delete(1L, 10L);

            // assert
            assertThat(first).isEqualTo(1);
            assertThat(second).isZero();
            assertThat(likeRepository.exists(1L, 10L)).isFalse();
        }

        @DisplayName("다른 사용자나 다른 상품의 관계는 지우지 않는다.")
        @Test
        void keepsOtherLikes() {
            // arrange
            saveLike(1L, 10L);
            saveLike(2L, 10L);
            saveLike(1L, 11L);

            // act
            likeRepository.delete(1L, 10L);

            // assert
            assertThat(likeRepository.exists(2L, 10L)).isTrue();
            assertThat(likeRepository.exists(1L, 11L)).isTrue();
        }
    }

    @DisplayName("상품의 좋아요 수를 셀 때,")
    @Nested
    class CountByProductId {
        @DisplayName("여러 사용자의 관계를 합산하고, 취소하면 줄어든다.")
        @Test
        void countsLikesOfProduct() {
            // arrange
            saveLike(1L, 10L);
            saveLike(2L, 10L);
            saveLike(1L, 11L);

            // act & assert
            assertThat(likeRepository.countByProductId(10L)).isEqualTo(2);
            likeRepository.delete(1L, 10L);
            assertThat(likeRepository.countByProductId(10L)).isEqualTo(1);
        }

        @DisplayName("관계가 없으면 0이다.")
        @Test
        void returnsZero_whenNoLikes() {
            assertThat(likeRepository.countByProductId(10L)).isZero();
        }
    }
}
