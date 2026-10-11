package com.loopers.config.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
class JacksonConfig {

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // Classpath 내의 모든 Jackson 모듈 자동 등록
            builder.findModulesViaServiceLoader(true);

            // Serialization Features
            builder.serializationInclusion(JsonInclude.Include.NON_NULL);
            builder.featuresToEnable(
                JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT,
                JsonGenerator.Feature.IGNORE_UNKNOWN,
                JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN
            );
            builder.featuresToDisable(
                SerializationFeature.FAIL_ON_EMPTY_BEANS
            );

            // Deserialization Features
            builder.featuresToEnable(
                DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT,
                DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
                DeserializationFeature.READ_ENUMS_USING_TO_STRING,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
            );
            builder.featuresToDisable(
                // 정수 필드에 소수(1.5)를 보내면 소수점을 버리고 받아들이는 기본 동작을 끈다. 금액·수량이 입력과 다르게 저장되지 않도록 거절한다.
                DeserializationFeature.ACCEPT_FLOAT_AS_INT,
                DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL,
                DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
            );
        };
    }
}
