package com.loopers.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 생성된 API 문서(OpenAPI)가 실제 요청 형식과 맞는지 검사한다. 파라미터 문서화 애노테이션을 잘못 쓰면
 * (예: {@code @Schema(name = "상품 ID")}) 경로 변수가 문서에서 사라지거나 이름이 바뀌어 Swagger UI 에서 호출할 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    private static final String USER_ID_HEADER = "X-USER-ID";
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}]+)}");
    private static final Pattern REQUEST_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record Operation(String method, String path, JsonNode node) {
        String label() {
            return method.toUpperCase() + " " + path;
        }

        Set<String> parameterNames(String in) {
            Set<String> names = new TreeSet<>();
            for (JsonNode parameter : node.path("parameters")) {
                if (in == null || in.equals(parameter.path("in").asText())) {
                    names.add(parameter.path("name").asText());
                }
            }
            return names;
        }
    }

    private List<Operation> operations;

    @BeforeEach
    void loadDocs() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        operations = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> paths = objectMapper.readTree(body).path("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> path = paths.next();
            // 다른 테스트가 만든 임시 컨트롤러(예: /test/header)가 아니라 실제 API 만 검사한다.
            if (!path.getKey().startsWith("/api/") && !path.getKey().startsWith("/api-admin/")) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> methods = path.getValue().fields();
            while (methods.hasNext()) {
                Map.Entry<String, JsonNode> method = methods.next();
                operations.add(new Operation(method.getKey(), path.getKey(), method.getValue()));
            }
        }
    }

    private Set<String> pathVariablesOf(String path) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = PATH_VARIABLE.matcher(path);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    @DisplayName("경로 변수는 이름 그대로 path 파라미터로 문서화된다.")
    @Test
    void documentsEveryPathVariable() {
        assertThat(operations).isNotEmpty();
        for (Operation operation : operations) {
            assertThat(operation.parameterNames("path"))
                .as(operation.label())
                .isEqualTo(pathVariablesOf(operation.path()));
        }
    }

    @DisplayName("모든 파라미터의 이름은 실제 요청에 쓰는 영문 이름이다.")
    @Test
    void usesRequestNamesForEveryParameter() {
        for (Operation operation : operations) {
            assertThat(operation.parameterNames(null))
                .as(operation.label())
                .allMatch(name -> REQUEST_NAME.matcher(name).matches());
        }
    }

    @DisplayName("식별이 필요한 경로만 X-USER-ID 헤더를 문서화한다.")
    @Test
    void documentsUserIdHeader_onlyOnIdentifiedPaths() {
        for (Operation operation : operations) {
            // 경로 변수에 임의의 값을 넣어 실제 요청처럼 식별 경로 여부를 판단한다.
            String uri = PATH_VARIABLE.matcher(operation.path()).replaceAll("1");
            MockHttpServletRequest request = new MockHttpServletRequest(operation.method().toUpperCase(), uri);
            request.setServletPath(uri);
            boolean identified = SecurityConfig.IDENTIFIED_PATHS.matches(request);

            assertThat(operation.parameterNames("header").contains(USER_ID_HEADER))
                .as(operation.label())
                .isEqualTo(identified);
        }
    }
}
