package com.loopers.interfaces.api;

import com.loopers.domain.example.ExampleModel;
import com.loopers.infrastructure.example.ExampleJpaRepository;
import com.loopers.interfaces.api.example.ExampleV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 회귀 관찰 테스트: 새 기능을 TDD로 만드는 게 아니라, 이미 있는 Example API가
 * 네 가지 입력에 대해 지금 실제로 어떤 계약(HTTP status / meta.result / errorCode / data 유무)을
 * 지키는지 관찰해서 고정한다. 결과는 docs/week1/order-discount-contract.md 1장 표에도 옮겨 적을 것.
 *
 * TODO: 아래 4개 메소드에 각각 arrange/act/assert를 직접 채우세요.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContractClassificationTest {

    private static final String EXAMPLE_ENDPOINT = "/api/v1/examples";
    private static final String UNMAPPED_ENDPOINT = "/api/v1/no-such-resource"; // TODO: 필요하면 값을 바꾸세요

    private final TestRestTemplate testRestTemplate;
    private final ExampleJpaRepository exampleJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ContractClassificationTest(
        TestRestTemplate testRestTemplate,
        ExampleJpaRepository exampleJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.exampleJpaRepository = exampleJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("네 가지 입력에 대한 계약 분류")
    @Nested
    class Classify {

        @DisplayName("존재하는 숫자 ID를 주면, ???")
        @Test
        void returnsX_whenExistingNumericIdIsGiven() {
            // TODO arrange: ExampleModel을 저장해서 존재하는 ID를 만드세요 (exampleJpaRepository.save(...))
            // TODO act: testRestTemplate.exchange(...)로 EXAMPLE_ENDPOINT + "/" + id 요청
            // TODO assert: HTTP status / meta.result / errorCode / data 유무를 확인하세요
        }

        @DisplayName("숫자가 아닌 ID를 주면, ???")
        @Test
        void returnsX_whenNonNumericIdIsGiven() {
            // TODO: EXAMPLE_ENDPOINT + "/abc" 로 요청해서 관찰하세요
        }

        @DisplayName("존재하지 않는 숫자 ID를 주면, ???")
        @Test
        void returnsX_whenNonExistingNumericIdIsGiven() {
            // TODO: 존재할 수 없는 숫자 ID(예: 음수)로 요청해서 관찰하세요
        }

        @DisplayName("미매핑 URL로 요청하면, ???")
        @Test
        void returnsX_whenUnmappedUrlIsRequested() {
            // TODO: UNMAPPED_ENDPOINT로 요청해서 관찰하세요
        }
    }
}
