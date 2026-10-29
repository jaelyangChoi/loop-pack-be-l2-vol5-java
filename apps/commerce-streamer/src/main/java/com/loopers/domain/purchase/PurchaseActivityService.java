package com.loopers.domain.purchase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

/** 주문 확정 사실을 구매 활동으로 저장한다. 트랜잭션은 호출하는 Facade 가 연다. */
@RequiredArgsConstructor
@Component
public class PurchaseActivityService {

    private final ProcessedOrderConfirmationRepository confirmationRepository;
    private final ProductPurchaseActivityRepository activityRepository;

    /** 새로 저장했으면 true, 같은 사실이 이미 저장돼 있어 아무것도 더하지 않았으면 false 다. 다른 사실이면 예외다. */
    public boolean record(String eventId, Long orderId, ZonedDateTime confirmedAt, List<PurchaseLine> lines) {
        if (isRecorded(eventId, orderId, confirmedAt, lines)) {
            return false;
        }
        // 완료 기록을 먼저 flush 해 유일 제약 경쟁이 품목 저장 전에 드러나게 한다.
        confirmationRepository.saveAndFlush(new ProcessedOrderConfirmation(eventId, orderId, confirmedAt));
        activityRepository.saveAllAndFlush(
            lines.stream().map(line -> new ProductPurchaseActivity(orderId, line, confirmedAt)).toList()
        );
        return true;
    }

    /** 같은 사실이 저장돼 있으면 true, 아무것도 없으면 false 다. 이미 있는데 내용이 다르면 예외다. */
    public boolean isRecorded(String eventId, Long orderId, ZonedDateTime confirmedAt, List<PurchaseLine> lines) {
        List<ProcessedOrderConfirmation> recorded = confirmationRepository.findByEventIdOrOrderId(eventId, orderId);
        if (recorded.isEmpty()) {
            return false;
        }
        if (recorded.size() > 1 || !isSameConfirmation(recorded.get(0), eventId, orderId, confirmedAt)) {
            throw new OrderConfirmationConflictException(
                "이미 다른 내용으로 처리한 주문 확정입니다. eventId=" + eventId + ", orderId=" + orderId
            );
        }
        List<PurchaseLine> recordedLines = activityRepository.findByOrderId(orderId).stream()
            .map(ProductPurchaseActivity::toLine)
            .sorted(Comparator.comparing(PurchaseLine::productId))
            .toList();
        if (!recordedLines.equals(lines)) {
            throw new OrderConfirmationConflictException(
                "이미 다른 품목으로 처리한 주문 확정입니다. eventId=" + eventId + ", orderId=" + orderId
            );
        }
        return true;
    }

    private boolean isSameConfirmation(ProcessedOrderConfirmation recorded, String eventId, Long orderId, ZonedDateTime confirmedAt) {
        return recorded.getEventId().equals(eventId)
            && recorded.getOrderId().equals(orderId)
            && recorded.getConfirmedAt().toInstant().equals(confirmedAt.toInstant());
    }
}
