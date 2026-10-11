package com.loopers.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ErrorTypeHttpStatusMapper;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 관리자 API({@code /api-admin/**})의 접근 경계. 관리자 역할만 허용한다.
 *
 * <p>로컬 실습용 경계이며 로그인 기능은 없다. 이 체인은 {@code /api-admin/**} 에만 적용되고, 이 빈이 있으면
 * Spring Boot 의 기본 보안 체인이 만들어지지 않으므로 고객 API({@code /api/**})에는 보안이 걸리지 않는다.
 *
 * <p>거절은 미식별·권한 부족 모두 403 이며, Security 필터는 Controller 보다 앞에서 동작해
 * {@code ApiControllerAdvice} 가 처리하지 못하므로 같은 {@link ApiResponse} 형식으로 직접 쓴다.
 */
@Configuration
public class AdminBoundaryConfig {

    @Bean
    SecurityFilterChain adminBoundary(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        return http.securityMatcher("/api-admin/**")
            .authorizeHttpRequests(rules -> rules.anyRequest().hasRole("ADMIN"))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> writeForbidden(response, objectMapper))
                .accessDeniedHandler((request, response, exception) -> writeForbidden(response, objectMapper)))
            .build();
    }

    private void writeForbidden(HttpServletResponse response, ObjectMapper objectMapper) throws IOException {
        ErrorType errorType = ErrorType.FORBIDDEN;
        response.setStatus(ErrorTypeHttpStatusMapper.toHttpStatus(errorType).value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
            response.getWriter(),
            ApiResponse.fail(errorType.getCode(), errorType.getMessage())
        );
    }
}
