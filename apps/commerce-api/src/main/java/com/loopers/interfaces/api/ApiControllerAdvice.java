package com.loopers.interfaces.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class ApiControllerAdvice {
    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handle(CoreException e) {
        log.warn("CoreException : {}", e.getCustomMessage() != null ? e.getCustomMessage() : e.getMessage(), e);
        return failureResponse(e.getErrorType(), e.getCustomMessage());
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadRequest(MethodArgumentTypeMismatchException e) {
        String name = e.getName();
        String type = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
        String value = e.getValue() != null ? e.getValue().toString() : "null";
        String message = String.format("요청 파라미터 '%s' (타입: %s)의 값 '%s'이(가) 잘못되었습니다.", name, type, value);
        return failureResponse(ErrorType.INVALID_INPUT, message);
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadRequest(MissingServletRequestParameterException e) {
        String name = e.getParameterName();
        String type = e.getParameterType();
        String message = String.format("필수 요청 파라미터 '%s' (타입: %s)가 누락되었습니다.", name, type);
        return failureResponse(ErrorType.INVALID_INPUT, message);
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadRequest(MissingRequestHeaderException e) {
        String message = String.format("필수 요청 헤더 '%s'가 누락되었습니다.", e.getHeaderName());
        return failureResponse(ErrorType.INVALID_INPUT, message);
    }

    // @Valid 요청 본문의 제약(@NotNull 등) 위반. 처리하지 않으면 Throwable 핸들러로 가서 500 이 된다.
    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadRequest(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(this::fieldErrorMessage)
            .collect(Collectors.joining(", "));
        return failureResponse(ErrorType.INVALID_INPUT, message.isEmpty() ? null : message);
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadRequest(HttpMessageNotReadableException e) {
        String errorMessage;
        Throwable rootCause = e.getRootCause();

        if (rootCause instanceof InvalidFormatException invalidFormat) {
            String fieldName = pathOf(invalidFormat.getPath());

            String valueIndicationMessage = "";
            if (invalidFormat.getTargetType().isEnum()) {
                Class<?> enumClass = invalidFormat.getTargetType();
                String enumValues = Arrays.stream(enumClass.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
                valueIndicationMessage = "사용 가능한 값 : [" + enumValues + "]";
            }

            String expectedType = invalidFormat.getTargetType().getSimpleName();
            Object value = invalidFormat.getValue();

            errorMessage = String.format("필드 '%s'의 값 '%s'이(가) 예상 타입(%s)과 일치하지 않습니다. %s",
                fieldName, value, expectedType, valueIndicationMessage);

        } else if (rootCause instanceof MismatchedInputException mismatchedInput) {
            String fieldPath = pathOf(mismatchedInput.getPath());
            errorMessage = String.format("필수 필드 '%s'이(가) 누락되었습니다.", fieldPath);

        } else if (rootCause instanceof JsonMappingException jsonMapping) {
            String fieldPath = pathOf(jsonMapping.getPath());
            errorMessage = String.format("필드 '%s'에서 JSON 매핑 오류가 발생했습니다: %s",
                fieldPath, jsonMapping.getOriginalMessage());

        } else if (rootCause instanceof InputCoercionException coercion && rangeMessageOf(coercion) != null) {
            errorMessage = rangeMessageOf(coercion);

        } else {
            errorMessage = "요청 본문을 처리하는 중 오류가 발생했습니다. JSON 메세지 규격을 확인해주세요.";
        }

        return failureResponse(ErrorType.INVALID_INPUT, errorMessage);
    }

    // 정수 필드가 int·long 범위를 넘으면 Jackson 은 필드 경로 없는 파서 예외를 던진다. 파서의 현재 위치로 경로를 만든다.
    private String rangeMessageOf(InputCoercionException e) {
        if (!(e.getProcessor() instanceof JsonParser parser)) {
            return null;
        }
        try {
            String path = pathOf(parser.getParsingContext());
            if (path.isEmpty()) {
                return null;
            }
            String targetType = e.getTargetType() != null ? e.getTargetType().getSimpleName() : "정수";
            return String.format("필드 '%s'의 값 '%s'이(가) 예상 타입(%s)의 범위를 넘었습니다.", path, parser.getText(), targetType);
        } catch (java.io.IOException ignored) {
            return null;
        }
    }

    // 배열 원소는 필드 이름이 없으므로 순서(인덱스)로 표기한다. 예: items[1].quantity
    private String pathOf(List<JsonMappingException.Reference> references) {
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

    private String pathOf(JsonStreamContext context) {
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

    private void appendField(StringBuilder path, String name) {
        if (!path.isEmpty()) {
            path.append('.');
        }
        path.append(name);
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadRequest(ServerWebInputException e) {
        String missingParams = extractMissingParameter(e.getReason() != null ? e.getReason() : "");
        if (!missingParams.isEmpty()) {
            String message = String.format("필수 요청 값 '%s'가 누락되었습니다.", missingParams);
            return failureResponse(ErrorType.INVALID_INPUT, message);
        } else {
            return failureResponse(ErrorType.INVALID_INPUT, null);
        }
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleNotFound(NoResourceFoundException e) {
        return failureResponse(ErrorType.NOT_FOUND, null);
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handle(Throwable e) {
        log.error("Exception : {}", e.getMessage(), e);
        return failureResponse(ErrorType.INTERNAL_ERROR, null);
    }

    private String fieldErrorMessage(FieldError error) {
        if ("NotNull".equals(error.getCode())) {
            return error.getField() + " 는 필수입니다.";
        }
        return error.getField() + " " + error.getDefaultMessage();
    }

    private String extractMissingParameter(String message) {
        Pattern pattern = Pattern.compile("'(.+?)'");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    private ResponseEntity<ApiResponse<?>> failureResponse(ErrorType errorType, String errorMessage) {
        return ResponseEntity.status(ErrorTypeHttpStatusMapper.toHttpStatus(errorType))
            .body(ApiResponse.fail(errorType.getCode(), errorMessage != null ? errorMessage : errorType.getMessage()));
    }
}
