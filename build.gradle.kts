import org.gradle.api.Project.DEFAULT_VERSION
import org.springframework.boot.gradle.tasks.bundling.BootJar

/** --- configuration functions --- */
fun getGitHash(): String {
    return runCatching {
        providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
    }.getOrElse { "init" }
}

/** --- project configurations --- */
plugins {
    java
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

allprojects {
    val projectGroup: String by project
    group = projectGroup
    version = if (version == DEFAULT_VERSION) getGitHash() else version

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    // Docker Engine 29+ 는 최소 클라이언트 API 버전을 1.44로 올렸는데, Spring Boot 3.4.4가 관리하는
    // testcontainers 1.20.x 는 이보다 낮은 버전을 보내 400을 받는다(https://github.com/testcontainers/testcontainers-java/issues/11212).
    // 이 문제는 testcontainers 2.0.2 에서 기본 API 버전을 1.44로 올리며 해결됨.
    extra["testcontainers.version"] = "2.0.2"

    dependencyManagement {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:${project.properties["springCloudDependenciesVersion"]}")
        }
    }

    dependencies {
        // Web
        runtimeOnly("org.springframework.boot:spring-boot-starter-validation")
        // Spring
        implementation("org.springframework.boot:spring-boot-starter")
        // Serialize
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
        // Lombok
        implementation("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")
        // Test
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        // testcontainers:mysql 이 jdbc 사용함
        testRuntimeOnly("com.mysql:mysql-connector-j")
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("com.ninja-squad:springmockk:${project.properties["springMockkVersion"]}")
        testImplementation("org.mockito:mockito-core:${project.properties["mockitoVersion"]}")
        testImplementation("org.instancio:instancio-junit:${project.properties["instancioJUnitVersion"]}")
        // Testcontainers
        testImplementation("org.springframework.boot:spring-boot-testcontainers")
        testImplementation("org.testcontainers:testcontainers")
        testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    }

    tasks.withType(Jar::class) { enabled = true }
    tasks.withType(BootJar::class) { enabled = false }

    configure(allprojects.filter { it.parent?.name.equals("apps") }) {
        tasks.withType(Jar::class) { enabled = false }
        tasks.withType(BootJar::class) { enabled = true }
    }

    tasks.test {
        maxParallelForks = 1
        useJUnitPlatform()
        systemProperty("user.timezone", "Asia/Seoul")
        systemProperty("spring.profiles.active", "test")
        jvmArgs("-Xshare:off")
    }

    tasks.withType<JacocoReport> {
        mustRunAfter("test")
        executionData(fileTree(layout.buildDirectory.asFile).include("jacoco/*.exec"))
        reports {
            xml.required = true
            csv.required = false
            html.required = false
        }
        afterEvaluate {
            classDirectories.setFrom(
                files(
                    classDirectories.files.map {
                        fileTree(it)
                    },
                ),
            )
        }
    }
}

// module-container 는 task 를 실행하지 않도록 한다.
project("apps") { tasks.configureEach { enabled = false } }
project("modules") { tasks.configureEach { enabled = false } }
project("supports") { tasks.configureEach { enabled = false } }
