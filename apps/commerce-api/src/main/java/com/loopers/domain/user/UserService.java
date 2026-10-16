package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;

    public User get(Long userId) {
        return userRepository.find(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다."));
    }

    /** 사용자를 잠그고 조회한다. 잔액을 바꾸는 유스케이스의 첫 사용자 조회로 쓴다. */
    public User getForUpdate(Long userId) {
        return userRepository.findForUpdate(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다."));
    }
}
