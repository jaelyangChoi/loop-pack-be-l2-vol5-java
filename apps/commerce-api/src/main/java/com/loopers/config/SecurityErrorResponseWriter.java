package com.loopers.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ErrorTypeHttpStatusMapper;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security 필터 단계의 거절을 {@link ApiResponse} 형식으로 쓴다.
 * 필터는 Controller 보다 앞에서 동작해 {@code ApiControllerAdvice} 가 처리하지 못하므로 같은 형식을 직접 쓴다.
 */
@RequiredArgsConstructor
@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    /** @param message {@code null} 이면 {@link ErrorType} 의 기본 메시지를 쓴다. */
    public void write(HttpServletResponse response, ErrorType errorType, String message) throws IOException {
        response.setStatus(ErrorTypeHttpStatusMapper.toHttpStatus(errorType).value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
            response.getWriter(),
            ApiResponse.fail(errorType.getCode(), message != null ? message : errorType.getMessage())
        );
    }
}
