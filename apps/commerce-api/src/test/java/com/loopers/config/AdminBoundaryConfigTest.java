package com.loopers.config;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminBoundaryConfigTest {

    private static final String ADMIN_BRANDS = "/api-admin/v1/brands";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("관리자 경로에 접근할 때,")
    @Nested
    class AdminPath {
        @DisplayName("식별 없는 요청은 403 FORBIDDEN 으로 ApiResponse 형식으로 거절한다.")
        @Test
        void rejectsWithForbidden_whenRequestIsAnonymous() throws Exception {
            mvc.perform(get(ADMIN_BRANDS))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.meta.result").value("FAIL"))
                .andExpect(jsonPath("$.meta.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.meta.message").value("접근 권한이 없습니다."));
        }

        @DisplayName("관리자 역할이 없는 사용자는 403 FORBIDDEN 으로 ApiResponse 형식으로 거절한다.")
        @Test
        void rejectsWithForbidden_whenUserIsNotAdmin() throws Exception {
            mvc.perform(get(ADMIN_BRANDS).with(user("customer").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.meta.errorCode").value("FORBIDDEN"));
        }

        @DisplayName("거절한 변경 요청은 DB 를 바꾸지 않는다.")
        @Test
        void keepsDatabase_whenModifyingRequestIsRejected() throws Exception {
            // act
            mvc.perform(post(ADMIN_BRANDS)
                    .with(user("customer").roles("USER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"루퍼스\"}"))
                .andExpect(status().isForbidden());
            mvc.perform(post(ADMIN_BRANDS)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"루퍼스\"}"))
                .andExpect(status().isForbidden());

            // assert
            assertThat(brandRepository.findAllActive(PageRequest.of(0, 10)).getTotalElements()).isZero();
        }
    }
}
