package com.loopers.interfaces.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 요청자(사용자) id 를 주입한다. 식별 필터({@code CustomerIdentityFilter})가 확인한 id 이며
 * {@link RequesterArgumentResolver} 가 처리한다.
 *
 * <p>요청자를 받는 API 는 {@code SecurityConfig.IDENTIFIED_PATHS} 에 등록돼 있어야 한다. 등록을 빠뜨리면 이 파라미터를
 * 만드는 시점에 서버 설정 실수로 거절되므로, Facade 는 요청자가 항상 존재하는 사용자의 id 라고 전제할 수 있다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Requester {
}
