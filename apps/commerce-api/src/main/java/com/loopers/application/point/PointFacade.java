package com.loopers.application.point;

import com.loopers.domain.common.Money;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PointFacade {

    private final UserService userService;

    @Transactional
    public PointInfo charge(Long userId, long amount) {
        User user = userService.get(userId);
        user.chargePoint(new Money(amount));
        return PointInfo.from(user);
    }

    @Transactional(readOnly = true)
    public PointInfo getBalance(Long userId) {
        User user = userService.get(userId);
        return PointInfo.from(user);
    }
}
