package com.loopers.testcontainers;

import com.redis.testcontainers.RedisContainer;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@Configuration
public class RedisTestContainersConfig {
    private static final RedisContainer redisContainer = new RedisContainer(DockerImageName.parse("redis:latest"));

    // 생성자에서 넣으면 RedisConfig 가 이 빈보다 먼저 만들어질 때 컨테이너 주소를 보지 못하므로,
    // MySqlTestContainersConfig 처럼 클래스 초기화 시점에 넣는다.
    static {
        redisContainer.start();

        System.setProperty("datasource.redis.database", "0");
        System.setProperty("datasource.redis.master.host", redisContainer.getHost());
        System.setProperty("datasource.redis.master.port", String.valueOf(redisContainer.getFirstMappedPort()));
        System.setProperty("datasource.redis.replicas[0].host", redisContainer.getHost());
        System.setProperty("datasource.redis.replicas[0].port", String.valueOf(redisContainer.getFirstMappedPort()));
    }

    /**
     * 장애 실험이 이 컨테이너를 멈추고(pause) 다시 살릴(unpause) 때 쓴다. stop/start 는 매핑 포트가 바뀌어 같은 JVM 의
     * 다른 테스트가 접속하지 못하므로 쓰지 않는다.
     */
    public static GenericContainer<?> container() {
        return redisContainer;
    }
}
