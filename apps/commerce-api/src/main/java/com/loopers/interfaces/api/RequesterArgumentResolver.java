package com.loopers.interfaces.api;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link Requester} 파라미터에 식별 필터가 인증 주체로 둔 사용자 id({@code Long})를 넣는다.
 *
 * <p>인증 정보가 없거나 주체가 {@code Long} 이 아니면(식별 경로로 등록하지 않아 익명 사용자로 들어온 경우) 클라이언트의
 * 잘못이 아니라 서버 설정 실수이므로 {@code INTERNAL_ERROR} 로 거절한다.
 */
@Component
public class RequesterArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(Requester.class) && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new CoreException(
            ErrorType.INTERNAL_ERROR,
            "요청자 식별 정보가 없습니다. 식별이 필요한 경로로 등록됐는지 확인하세요."
        );
    }
}
