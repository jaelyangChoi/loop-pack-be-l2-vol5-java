package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandApiE2ETest {

    private static final String BRANDS_URL = "/api/v1/brands/";
    private static final String USER_ID_HEADER = "X-USER-ID";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders headers(String userId) {
        HttpHeaders headers = new HttpHeaders();
        if (userId != null) {
            headers.set(USER_ID_HEADER, userId);
        }
        return headers;
    }

    // 응답 필드 구성(시각 필드 제외)을 확인하려고 data 를 Map 으로 받는다.
    private ResponseEntity<ApiResponse<Map<String, Object>>> getBrand(String userId, String brandId) {
        return testRestTemplate.exchange(
            BRANDS_URL + brandId, HttpMethod.GET, new HttpEntity<>(headers(userId)),
            new ParameterizedTypeReference<>() {}
        );
    }

    private void assertFail(
        ResponseEntity<ApiResponse<Map<String, Object>>> response, HttpStatus status, String errorCode
    ) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().meta().errorCode()).isEqualTo(errorCode);
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetDetail {
        @DisplayName("브랜드의 id 와 이름만 반환한다. 생성·수정 시각은 담지 않는다.")
        @Test
        void returnsIdAndNameOnly() {
            // arrange
            User user = userRepository.save(User.create());
            Brand brand = brandRepository.save(Brand.create("루퍼스"));

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response =
                getBrand(String.valueOf(user.getId()), String.valueOf(brand.getId()));

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data())
                .containsOnlyKeys("id", "name")
                .containsEntry("name", "루퍼스");
            assertThat(((Number) response.getBody().data().get("id")).longValue()).isEqualTo(brand.getId());
        }

        @DisplayName("없는 브랜드이면 404 NOT_FOUND 로 응답한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            User user = userRepository.save(User.create());

            ResponseEntity<ApiResponse<Map<String, Object>>> response =
                getBrand(String.valueOf(user.getId()), "999");

            assertFail(response, HttpStatus.NOT_FOUND, "NOT_FOUND");
            assertThat(response.getBody().meta().message()).isEqualTo("존재하지 않는 브랜드입니다.");
        }

        @DisplayName("삭제된 브랜드이면 404 NOT_FOUND 로 응답한다.")
        @Test
        void returnsNotFound_whenBrandIsDeleted() {
            User user = userRepository.save(User.create());
            Brand deleted = Brand.create("삭제됨");
            deleted.delete();
            Brand saved = brandRepository.save(deleted);

            ResponseEntity<ApiResponse<Map<String, Object>>> response =
                getBrand(String.valueOf(user.getId()), String.valueOf(saved.getId()));

            assertFail(response, HttpStatus.NOT_FOUND, "NOT_FOUND");
        }

        @DisplayName("brandId 가 숫자가 아니면 400 INVALID_INPUT 으로 응답한다.")
        @Test
        void returnsBadRequest_whenBrandIdIsNotNumber() {
            User user = userRepository.save(User.create());

            ResponseEntity<ApiResponse<Map<String, Object>>> response =
                getBrand(String.valueOf(user.getId()), "abc");

            assertFail(response, HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        }

        @DisplayName("X-USER-ID 헤더가 없으면 400 INVALID_INPUT 으로 응답한다.")
        @Test
        void returnsBadRequest_whenUserIdHeaderIsMissing() {
            Brand brand = brandRepository.save(Brand.create("루퍼스"));

            ResponseEntity<ApiResponse<Map<String, Object>>> response =
                getBrand(null, String.valueOf(brand.getId()));

            assertFail(response, HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        }

        @DisplayName("존재하지 않는 사용자이면 404 NOT_FOUND 로 응답한다.")
        @Test
        void returnsNotFound_whenUserDoesNotExist() {
            Brand brand = brandRepository.save(Brand.create("루퍼스"));

            ResponseEntity<ApiResponse<Map<String, Object>>> response =
                getBrand("999", String.valueOf(brand.getId()));

            assertFail(response, HttpStatus.NOT_FOUND, "NOT_FOUND");
            assertThat(response.getBody().meta().message()).isEqualTo("존재하지 않는 사용자입니다.");
        }
    }
}
