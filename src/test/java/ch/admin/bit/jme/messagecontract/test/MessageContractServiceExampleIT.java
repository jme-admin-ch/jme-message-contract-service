package ch.admin.bit.jme.messagecontract.test;

import ch.admin.bit.jeap.jme.test.BootServiceSpringIntegrationTestBase;
import ch.admin.bit.jeap.messagecontract.web.api.dto.CreateMessageContractsDto;
import ch.admin.bit.jeap.messagecontract.web.api.dto.MessageContractRole;
import ch.admin.bit.jeap.messagecontract.web.api.dto.NewMessageContractDto;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ch.admin.bit.jeap.messagecontract.web.api.dto.CompatibilityMode.BACKWARD;
import static ch.admin.bit.jeap.messagecontract.web.api.dto.MessageContractRole.CONSUMER;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.preemptive;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

@Slf4j
@TestMethodOrder(OrderAnnotation.class)
class MessageContractServiceExampleIT extends BootServiceSpringIntegrationTestBase {

    private static final String MAIN = "main";
    private static final String NO_COMMIT_HASH = null;
    private static final String REPO_URL = "https://github.com/jme-admin-ch/jme-message-type-registry.git";
    private static final String BASE_URL = "http://localhost:8083/message-contract-service/api";
    private static final String MESSAGE_TYPE = "JmeCreateDeclarationCommand";
    private static final String TOPIC_INCOMPATIBLE = "topic-incompatible";
    private static final String TOPIC = "topic";
    private static final String V1 = "1.0.0";
    private static final String V2 = "2.0.0";
    private static final String ENCRYPTION_KEY_ID = "messaging-key-id";
    private static final int CLONE_ATTEMPTS = 3;
    private static final Duration CLONE_RETRY_DELAY = Duration.ofSeconds(5);

    private RequestSpecification request;

    @BeforeAll
    static void startServices() throws Exception {
        cloneMessageTypeRegistryWithSystemGit();
        startService(null, "http://localhost:8083/message-contract-service",
                Map.of("spring.docker.compose.enabled", "false"));
    }

    /**
     * Populates the reference repository cache the service reads the message type schemas from. github.com
     * intermittently answers the anonymous clone from the CI agents with a 401, which makes git ask for a username
     * and fail, so retry a few times before giving up.
     */
    private static void cloneMessageTypeRegistryWithSystemGit() throws IOException, InterruptedException {
        Path cacheDir = Path.of("target", "message-type-repository-cache", sha256(REPO_URL)).normalize();
        StringBuilder failures = new StringBuilder();
        for (int attempt = 1; attempt <= CLONE_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                Thread.sleep(CLONE_RETRY_DELAY.toMillis());
            }
            for (List<String> proxyArgs : gitProxyArguments()) {
                String failure = cloneOrFetch(cacheDir, proxyArgs);
                if (failure == null) {
                    return;
                }
                failures.append(failure);
            }
        }
        throw new IllegalStateException("Failed to clone or refresh the message type registry cache using system git:\n" + failures);
    }

    /**
     * Git configurations to try, in order: first the ambient git configuration, which is what a developer machine
     * and the CI agents normally use, then the corporate proxy as an alternative route. Maven hands that proxy to
     * the test JVM as system properties, which git does not pick up by itself.
     */
    private static List<List<String>> gitProxyArguments() {
        List<List<String>> arguments = new ArrayList<>();
        arguments.add(List.of());
        String proxyHost = System.getProperty("https.proxyHost");
        if (proxyHost != null && !proxyHost.isBlank()) {
            String proxyPort = System.getProperty("https.proxyPort", "8080");
            arguments.add(List.of("-c", "http.proxy=http://" + proxyHost + ":" + proxyPort));
        }
        return arguments;
    }

    /**
     * @return {@code null} if git succeeded, the git command and its output otherwise
     */
    private static String cloneOrFetch(Path cacheDir, List<String> proxyArgs) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git", "-c", "safe.bareRepository=all"));
        command.addAll(proxyArgs);
        if (cacheDir.resolve("config").toFile().exists()) {
            command.addAll(List.of("-C", cacheDir.toString(), "fetch", "--prune", "--tags", "origin"));
        } else {
            // A previous attempt may have left a partial clone behind, which would make git refuse to clone again
            FileSystemUtils.deleteRecursively(cacheDir);
            cacheDir.getParent().toFile().mkdirs();
            command.addAll(List.of("clone", "--mirror", REPO_URL, cacheDir.toString()));
        }

        log.info("Running {}", String.join(" ", command));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        log.info("{}", output);
        if (exitCode == 0) {
            return null;
        }
        return String.join(" ", command) + " exited with code " + exitCode + ":\n" + output + "\n";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    @Order(1)
    @Test
    void putProducerContracts() {
        NewMessageContractDto producerContractV1Dto = new NewMessageContractDto(
                MESSAGE_TYPE, V1,
                TOPIC, MessageContractRole.PRODUCER, REPO_URL, NO_COMMIT_HASH, MAIN, BACKWARD, null);
        NewMessageContractDto producerContractV2Dto = new NewMessageContractDto(
                MESSAGE_TYPE, V2, TOPIC_INCOMPATIBLE, MessageContractRole.PRODUCER,
                REPO_URL, NO_COMMIT_HASH, MAIN, BACKWARD, ENCRYPTION_KEY_ID);
        CreateMessageContractsDto dto = new CreateMessageContractsDto(List.of(producerContractV1Dto, producerContractV2Dto));

        given().spec(request).contentType(ContentType.JSON)
                .body(dto)
                .when().put("/contracts/producer-app/1.0")
                .then().statusCode(HttpStatus.CREATED.value());
    }

    @Order(2)
    @Test
    void putConsumerContract() {
        NewMessageContractDto consumerContractDto = new NewMessageContractDto(
                MESSAGE_TYPE, V1,
                TOPIC, CONSUMER, REPO_URL, NO_COMMIT_HASH, MAIN, BACKWARD, null);
        CreateMessageContractsDto dto = new CreateMessageContractsDto(List.of(consumerContractDto));

        given().spec(request).contentType(ContentType.JSON)
                .body(dto)
                .when().put("/contracts/consumer-app/2.0")
                .then().statusCode(HttpStatus.CREATED.value());
    }

    @Order(3)
    @Test
    void putIncompatibleConsumerContract() {
        // Producer produces v2 on this topic, consumer tries to consume incompatible v1 on the same topic
        NewMessageContractDto consumerContractDto = new NewMessageContractDto(
                MESSAGE_TYPE, V1,
                TOPIC_INCOMPATIBLE, CONSUMER, REPO_URL, NO_COMMIT_HASH, MAIN, BACKWARD, null);
        CreateMessageContractsDto dto = new CreateMessageContractsDto(List.of(consumerContractDto));

        given().spec(request).contentType(ContentType.JSON)
                .body(dto)
                .when().put("/contracts/incompatible-consumer-app/2.0")
                .then().statusCode(HttpStatus.CREATED.value());
    }

    @Order(4)
    @Test
    void registerDeploymentOfProducer() {
        given().spec(request)
                .when().put("/deployments/producer-app/1.0/dev")
                .then().statusCode(HttpStatus.CREATED.value());
    }

    @Order(5)
    @Test
    void checkCompatibilityOfConsumer_whenCompatible_shouldReturnOk() {
        given().spec(request)
                .when().get("/deployments/compatibility/consumer-app/2.0/dev")
                .then().statusCode(HttpStatus.OK.value())
                .body("interactions", hasSize(greaterThan(0)));
    }

    @Order(6)
    @Test
    void checkCompatibilityOfConsumer_whenIncompatible_shouldReturnPreconditionFailed() {
        given().spec(request)
                .when().get("/deployments/compatibility/incompatible-consumer-app/2.0/dev")
                .then().statusCode(HttpStatus.PRECONDITION_FAILED.value());
    }

    @Order(7)
    @Test
    void getDeployments() {
        given().spec(request)
                .when().get("/deployments")
                .then().statusCode(HttpStatus.OK.value());
    }

    @BeforeEach
    void setUp() {
        RestAssured.config.getLogConfig().blacklistHeader(HttpHeaders.AUTHORIZATION, HttpHeaders.SET_COOKIE);
        RestAssured.filters(new ResponseLoggingFilter());

        RequestSpecBuilder builder = new RequestSpecBuilder();
        builder.setBaseUri(BASE_URL);
        builder.setAuth(preemptive().basic("write", "secret"));
        request = builder.build();
    }

}
