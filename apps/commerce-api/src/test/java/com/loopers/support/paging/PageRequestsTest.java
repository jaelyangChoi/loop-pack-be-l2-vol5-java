package com.loopers.support.paging;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageRequestsTest {

    @DisplayName("페이지 요청을 만들 때,")
    @Nested
    class Of {
        @DisplayName("page 와 size 를 그대로 담는다.")
        @Test
        void keepsPageAndSize() {
            // act
            PageRequest request = PageRequests.of(2, 20);

            // assert
            assertThat(request.getPageNumber()).isEqualTo(2);
            assertThat(request.getPageSize()).isEqualTo(20);
        }

        @DisplayName("page 는 0 이상, size 는 1 이상 100 이하이면 허용한다. 경계값을 포함한다.")
        @ParameterizedTest
        @CsvSource({"0, 1", "0, 100"})
        void allowsBoundaryValues(int page, int size) {
            // act
            PageRequest request = PageRequests.of(page, size);

            // assert
            assertThat(request.getPageNumber()).isEqualTo(page);
            assertThat(request.getPageSize()).isEqualTo(size);
        }

        @DisplayName("page 가 음수이거나 size 가 범위를 벗어나면 INVALID_INPUT 으로 거절한다.")
        @ParameterizedTest
        @CsvSource({"-1, 20", "0, 0", "0, 101", "0, -5"})
        void rejectsWithInvalidInput_whenOutOfRange(int page, int size) {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> PageRequests.of(page, size));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
        }

        @DisplayName("거절 메시지에 어느 값이 문제인지 드러난다.")
        @Test
        void tellsWhichValueIsInvalid() {
            // act
            CoreException pageError = assertThrows(CoreException.class, () -> PageRequests.of(-1, 20));
            CoreException sizeError = assertThrows(CoreException.class, () -> PageRequests.of(0, 101));

            // assert
            assertThat(pageError.getCustomMessage()).isEqualTo("page 는 0 이상이어야 합니다.");
            assertThat(sizeError.getCustomMessage()).isEqualTo("size 는 1 이상 100 이하여야 합니다.");
        }
    }

    @DisplayName("요청 파라미터에 쓰는 기본값과 설명은,")
    @Nested
    class ParamDefaults {
        @DisplayName("기본값이 허용 범위 안이라 파라미터를 생략해도 유효한 페이지 요청이 된다.")
        @Test
        void defaultsAreWithinAllowedRange() {
            // act
            PageRequest request = PageRequests.of(
                Integer.parseInt(PageRequests.DEFAULT_PAGE), Integer.parseInt(PageRequests.DEFAULT_SIZE)
            );

            // assert
            assertThat(request.getPageNumber()).isZero();
            assertThat(request.getPageSize()).isBetween(1, PageRequests.MAX_PAGE_SIZE);
        }

        @DisplayName("설명 문구가 기본값과 최대 크기를 그대로 담아, 정책이 바뀌어도 문서와 어긋나지 않는다.")
        @Test
        void descriptionsContainDefaultsAndMaximum() {
            assertThat(PageRequests.PAGE_PARAM_DESCRIPTION).contains("기본 " + PageRequests.DEFAULT_PAGE);
            assertThat(PageRequests.SIZE_PARAM_DESCRIPTION)
                .contains("1~" + PageRequests.MAX_PAGE_SIZE)
                .contains("기본 " + PageRequests.DEFAULT_SIZE);
        }
    }
}
