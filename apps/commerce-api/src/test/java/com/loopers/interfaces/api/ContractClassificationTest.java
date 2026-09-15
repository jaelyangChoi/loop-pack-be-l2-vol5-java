package com.loopers.interfaces.api;

import com.loopers.domain.example.ExampleModel;
import com.loopers.infrastructure.example.ExampleJpaRepository;
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
 * 회귀 관찰 테스트.
 *
 * <p>새 기능을 TDD 로 만드는 것이 아니라, 이미 있는 Example API 가 네 가지 입력에 대해
 * 지금 실제로 어떤 계약(HTTP status / meta.result / meta.errorCode / data 유무 / meta.message)을
 * 지키는지 관찰해서 고정한다. 여기서 확인한 값은 그대로
 * {@code docs/week1/order-discount-contract.md} 1장 표로 옮겨 적어 주문 쿠폰 할인 API 설계의 기준선으로 쓴다.
 *
 * <p>관찰 포인트: "존재하지 않는 리소스"와 "미매핑 URL"이 status 와 errorCode 로는 구분되지 않고
 * ({@code 404 / "Not Found"}) message 로만 갈린다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContractClassificationTest {

    private static final String EXAMPLE_ENDPOINT = "/api/v1/examples/";
    private static final String UNMAPPED_ENDPOINT = "/api/v1/no-such-resource";

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

    private ResponseEntity<ApiResponse<Object>> get(String url) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null), responseType);
    }

    @DisplayName("기존 Example API 계약 분류 (네 가지 입력)")
    @Nested
    class Classify {

        @DisplayName("존재하는 숫자 ID 를 주면, 200 OK · result=SUCCESS · errorCode 없음 · data 있음")
        @Test
        void returnsSuccessWithData_whenExistingNumericIdIsGiven() {
            // arrange
            Long id = exampleJpaRepository.save(new ExampleModel("예시 제목", "예시 설명")).getId();

            // act
            ResponseEntity<ApiResponse<Object>> response = get(EXAMPLE_ENDPOINT + id);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().meta().errorCode()).isNull(),
                () -> assertThat(response.getBody().meta().message()).isNull(),
                () -> assertThat(response.getBody().data()).isNotNull()
            );
        }

        @DisplayName("숫자가 아닌 ID 를 주면, 400 Bad Request · result=FAIL · errorCode='Bad Request' · data 없음")
        @Test
        void returnsBadRequest_whenNonNumericIdIsGiven() {
            // act
            ResponseEntity<ApiResponse<Object>> response = get(EXAMPLE_ENDPOINT + "abc");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("Bad Request"),
                () -> assertThat(response.getBody().meta().message()).contains("abc"),
                () -> assertThat(response.getBody().data()).isNull()
            );
        }

        @DisplayName("존재하지 않는 숫자 ID 를 주면, 404 Not Found · result=FAIL · errorCode='Not Found' · 도메인 메시지")
        @Test
        void returnsNotFoundWithDomainMessage_whenNonExistingNumericIdIsGiven() {
            // act
            ResponseEntity<ApiResponse<Object>> response = get(EXAMPLE_ENDPOINT + "-1");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("Not Found"),
                () -> assertThat(response.getBody().meta().message()).contains("예시를 찾을 수 없습니다"),
                () -> assertThat(response.getBody().data()).isNull()
            );
        }

        @DisplayName("미매핑 URL 로 요청하면, 404 Not Found · result=FAIL · errorCode='Not Found' · 제네릭 메시지 "
            + "(존재하지 않는 숫자 ID 와 status·errorCode 가 동일하고 message 로만 갈린다)")
        @Test
        void returnsNotFoundWithGenericMessage_whenUnmappedUrlIsRequested() {
            // act
            ResponseEntity<ApiResponse<Object>> response = get(UNMAPPED_ENDPOINT);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("Not Found"),
                () -> assertThat(response.getBody().meta().message()).isEqualTo("존재하지 않는 요청입니다."),
                () -> assertThat(response.getBody().data()).isNull()
            );
        }
    }
}
