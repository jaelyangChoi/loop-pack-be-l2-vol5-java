package com.loopers.interfaces.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiControllerAdviceTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // @SpringBootTest / @WebMvcTest 대신 standalone 으로 직접 조립한다.
        // 스프링 컨텍스트와 Testcontainers(Docker) 없이 ApiControllerAdvice 의 예외 변환만 빠르게 검증하기 위함이다.
        mockMvc = MockMvcBuilders.standaloneSetup(new HeaderTestController())
            .setControllerAdvice(new ApiControllerAdvice())
            .build();
    }

    // 시나리오(헤더 처리)별로 묶어 테스트 트리를 문서처럼 읽히게 한다. 이후 다른 예외 처리도 @Nested 로 추가한다.
    @DisplayName("X-USER-ID 헤더를 받는 API 에서,")
    @Nested
    class UserIdHeader {
        @DisplayName("헤더가 없으면 INVALID_INPUT(400)으로 응답한다.")
        @Test
        void returnsInvalidInput_whenHeaderIsMissing() throws Exception {
            mockMvc.perform(get("/test/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
        }

        @DisplayName("헤더 값이 숫자가 아니면 INVALID_INPUT(400)으로 응답한다.")
        @Test
        void returnsInvalidInput_whenHeaderIsNotNumber() throws Exception {
            mockMvc.perform(get("/test/header").header("X-USER-ID", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.errorCode").value("INVALID_INPUT"));
        }

        @DisplayName("유효한 헤더면 정상 응답한다.")
        @Test
        void returnsOk_whenHeaderIsValid() throws Exception {
            mockMvc.perform(get("/test/header").header("X-USER-ID", "1"))
                .andExpect(status().isOk());
        }
    }

    // 실제 Controller 는 아직 없으므로, 헤더를 받는 최소한의 테스트 전용 Controller 를 둔다.
    // 바깥 테스트 클래스의 인스턴스에 묶이지 않도록 static 중첩 클래스로 선언한다.
    @RestController
    static class HeaderTestController {
        @GetMapping("/test/header")
        public String get(@RequestHeader("X-USER-ID") Long userId) {
            return String.valueOf(userId);
        }
    }
}
