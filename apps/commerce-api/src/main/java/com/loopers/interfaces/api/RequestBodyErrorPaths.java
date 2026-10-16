package com.loopers.interfaces.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 요청 본문(JSON)을 읽지 못했을 때, 문제가 된 필드의 위치를 사람이 읽을 수 있는 경로로 만든다.
 * 배열 원소는 필드 이름이 없으므로 순서(인덱스)로 표기한다. 예: {@code items[1].quantity}
 */
final class RequestBodyErrorPaths {

    private RequestBodyErrorPaths() {
    }

    static String of(List<JsonMappingException.Reference> references) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : references) {
            if (ref.getFieldName() != null) {
                appendField(path, ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            } else {
                appendField(path, "?");
            }
        }
        return path.toString();
    }

    static String of(JsonStreamContext context) {
        List<String> segments = new ArrayList<>();
        for (JsonStreamContext current = context; current != null; current = current.getParent()) {
            if (current.inObject() && current.getCurrentName() != null) {
                segments.add(current.getCurrentName());
            } else if (current.inArray()) {
                segments.add("[" + current.getCurrentIndex() + "]");
            }
        }
        Collections.reverse(segments);
        StringBuilder path = new StringBuilder();
        for (String segment : segments) {
            if (segment.startsWith("[")) {
                path.append(segment);
            } else {
                appendField(path, segment);
            }
        }
        return path.toString();
    }

    /**
     * 정수 필드가 int·long 범위를 넘을 때의 안내 메시지. Jackson 은 이때 필드 경로 없는 파서 예외를 던지므로
     * 파서의 현재 위치로 경로를 만든다. 경로를 얻지 못하면 {@code null} 이다.
     */
    static String rangeMessage(InputCoercionException e) {
        if (!(e.getProcessor() instanceof JsonParser parser)) {
            return null;
        }
        try {
            String path = of(parser.getParsingContext());
            if (path.isEmpty()) {
                return null;
            }
            String targetType = e.getTargetType() != null ? e.getTargetType().getSimpleName() : "정수";
            return String.format("필드 '%s'의 값 '%s'이(가) 예상 타입(%s)의 범위를 넘었습니다.", path, parser.getText(), targetType);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void appendField(StringBuilder path, String name) {
        if (!path.isEmpty()) {
            path.append('.');
        }
        path.append(name);
    }
}
