package com.loopers.config;

import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 고객 요청자 식별. 식별이 필요한 경로에서만 동작하며 {@code X-USER-ID} 헤더의 형식과 사용자 존재를 확인한다.
 *
 * <p>통과하면 사용자 id({@code Long})를 인증 주체로 두어 Controller 가 {@code @Requester} 로 받는다.
 * 거절은 지금까지의 오류 계약대로 헤더 누락·형식 오류는 400, 없는 사용자는 404 이며 다음으로 넘기지 않는다.
 * 그 밖의 경로(카탈로그 등)는 헤더를 읽지도, 사용자를 조회하지도 않는다.
 *
 * <p>Bean 으로 등록하면 Spring Boot 가 모든 요청의 서블릿 필터로도 등록하므로, {@link SecurityConfig} 가 직접 만들어
 * 고객 보안 체인에만 넣는다.
 */
public class CustomerIdentityFilter extends OncePerRequestFilter {

    static final String USER_ID_HEADER = "X-USER-ID";

    private final RequestMatcher identifiedPaths;
    private final UserService userService;
    private final SecurityErrorResponseWriter errorWriter;

    public CustomerIdentityFilter(
        RequestMatcher identifiedPaths, UserService userService, SecurityErrorResponseWriter errorWriter
    ) {
        this.identifiedPaths = identifiedPaths;
        this.userService = userService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !identifiedPaths.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String header = request.getHeader(USER_ID_HEADER);
        if (header == null) {
            errorWriter.write(response, ErrorType.INVALID_INPUT,
                String.format("필수 요청 헤더 '%s'가 누락되었습니다.", USER_ID_HEADER));
            return;
        }

        Long userId;
        try {
            userId = Long.valueOf(header);
        } catch (NumberFormatException e) {
            errorWriter.write(response, ErrorType.INVALID_INPUT,
                String.format("요청 헤더 '%s'의 값 '%s'이(가) 잘못되었습니다.", USER_ID_HEADER, header));
            return;
        }

        try {
            userService.get(userId);
        } catch (CoreException e) {
            errorWriter.write(response, e.getErrorType(), e.getCustomMessage());
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(userId, null, AuthorityUtils.NO_AUTHORITIES));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
