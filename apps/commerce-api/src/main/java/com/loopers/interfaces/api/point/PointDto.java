package com.loopers.interfaces.api.point;

import com.loopers.application.point.PointInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class PointDto {
    public record ChargeRequest(Long amount) {
        // 누락(null)은 요청 형식 오류라 여기서 거절한다. 0 이하 같은 도메인 규칙은 Money·PointBalance 가 판단한다.
        public long requiredAmount() {
            if (amount == null) {
                throw new CoreException(ErrorType.INVALID_INPUT, "amount 는 필수입니다.");
            }
            return amount;
        }
    }

    public record PointResponse(long balance) {
        public static PointResponse from(PointInfo info) {
            return new PointResponse(info.balance());
        }
    }
}
