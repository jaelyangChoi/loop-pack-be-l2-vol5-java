package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    User save(User user);

    Optional<User> find(Long id);

    /**
     * 사용자를 배타 잠금(FOR UPDATE)으로 조회한다. 잔액을 바꾸는 요청(충전·결제)은 먼저 잡은 트랜잭션이 끝날 때까지
     * 기다린다. 반드시 Facade 의 트랜잭션 안에서, 이 사용자를 처음 읽는 조회로 호출한다.
     */
    Optional<User> findForUpdate(Long id);
}
