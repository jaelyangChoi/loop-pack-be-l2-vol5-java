package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeTest {

    @DisplayName("좋아요를 생성할 때,")
    @Nested
    class Create {
        @DisplayName("사용자와 상품을 식별자로 가지고, 좋아요한 시각이 채워진다.")
        @Test
        void keepsIds_andSetsLikedAt() {
            // act
            Like like = Like.create(1L, 10L);

            // assert
            assertThat(like.getId().userId()).isEqualTo(1L);
            assertThat(like.getId().productId()).isEqualTo(10L);
            assertThat(like.getLikedAt()).isNotNull();
        }

        @DisplayName("사용자 또는 상품이 없으면 INVALID_INPUT 으로 거절한다.")
        @Test
        void rejectsWithInvalidInput_whenUserOrProductIsNull() {
            // act
            CoreException withoutUser = assertThrows(CoreException.class, () -> Like.create(null, 10L));
            CoreException withoutProduct = assertThrows(CoreException.class, () -> Like.create(1L, null));

            // assert
            assertThat(withoutUser.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(withoutProduct.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
        }
    }

    @DisplayName("좋아요 식별자는,")
    @Nested
    class Identity {
        @DisplayName("사용자와 상품이 같으면 같은 좋아요로 본다.")
        @Test
        void isEqual_whenUserAndProductAreSame() {
            // assert
            assertThat(new LikeId(1L, 10L)).isEqualTo(new LikeId(1L, 10L));
            assertThat(new LikeId(1L, 10L)).isNotEqualTo(new LikeId(2L, 10L));
            assertThat(new LikeId(1L, 10L)).isNotEqualTo(new LikeId(1L, 11L));
        }
    }
}
