package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.payment.pg.PgPaymentRequestCommand;
import com.loopers.application.payment.pg.PgStatus;
import com.loopers.application.payment.pg.PgTransactionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 테스트 PG 의 응답 본문 해석. 형식이 조금이라도 어긋나면 값을 지어내지 않고 해석하지 못한 것으로 다룬다. */
final class PgResponseParser {

    private static final String REJECTED_CODE = "TEST_PG_REJECTED_BEFORE_CREATE";
    private static final String UNPARSEABLE_TRANSACTION = "해석할 수 없는 거래 조회 응답";
    private static final String UNPARSEABLE_TRANSACTION_LIST = "해석할 수 없는 거래 목록 응답";

    private final ObjectMapper objectMapper;

    PgResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 202 본문이 요청과 맞는 PENDING 거래일 때만 값이 있다. */
    Optional<PgTransactionInfo> acceptedTransaction(String body, PgPaymentRequestCommand request) {
        return parseTransaction(readTree(body))
            .filter(transaction -> transaction.status() == PgStatus.PENDING
                && transaction.providerOrderId().equals(request.providerOrderId())
                && transaction.amount() == request.amount());
    }

    // 정확한 code + transactionCreated=false + 요청 orderId·amount 일치. 하나라도 어긋나면 효과가 없다는 증거가 아니다.
    boolean isConfirmedRejection(String body, PgPaymentRequestCommand request) {
        JsonNode root = readTree(body);
        if (root == null || !root.isObject()) {
            return false;
        }
        return textEquals(root.get("code"), REJECTED_CODE)
            && isFalse(root.get("transactionCreated"))
            && textEquals(root.get("orderId"), request.providerOrderId())
            && longEquals(root.get("amount"), request.amount());
    }

    /** 거래 조회 응답. 해석할 수 없으면 조회 실패다. */
    PgTransactionInfo transaction(String body) {
        return parseTransaction(readTree(body))
            .orElseThrow(() -> new PgStatusLookupFailedException(UNPARSEABLE_TRANSACTION));
    }

    /** 거래 목록 조회 응답. 한 건이라도 해석할 수 없으면 조회 실패다. */
    List<PgTransactionInfo> transactions(String body) {
        JsonNode root = readTree(body);
        if (root == null || !root.isArray()) {
            throw new PgStatusLookupFailedException(UNPARSEABLE_TRANSACTION_LIST);
        }
        List<PgTransactionInfo> transactions = new ArrayList<>();
        for (JsonNode element : root) {
            transactions.add(parseTransaction(element)
                .orElseThrow(() -> new PgStatusLookupFailedException(UNPARSEABLE_TRANSACTION_LIST)));
        }
        return transactions;
    }

    private JsonNode readTree(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Optional<PgTransactionInfo> parseTransaction(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        JsonNode transactionKey = node.get("transactionKey");
        JsonNode orderId = node.get("orderId");
        JsonNode amount = node.get("amount");
        JsonNode status = node.get("status");
        if (!isText(transactionKey) || !isText(orderId) || !isLong(amount) || !isText(status)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PgTransactionInfo(
                transactionKey.asText(), orderId.asText(), amount.asLong(), PgStatus.valueOf(status.asText())
            ));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean isText(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank();
    }

    // 금액은 JSON 정수여야 한다. 문자열 "7000" 이나 소수는 받아들이지 않는다.
    private boolean isLong(JsonNode node) {
        return node != null && node.isIntegralNumber() && node.canConvertToLong();
    }

    private boolean textEquals(JsonNode node, String expected) {
        return node != null && node.isTextual() && node.asText().equals(expected);
    }

    private boolean longEquals(JsonNode node, long expected) {
        return isLong(node) && node.asLong() == expected;
    }

    private boolean isFalse(JsonNode node) {
        return node != null && node.isBoolean() && !node.asBoolean();
    }
}
