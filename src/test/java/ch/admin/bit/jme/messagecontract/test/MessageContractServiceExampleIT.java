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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private RequestSpecification request;

    @BeforeAll
    static void startServices() throws Exception {
        cloneMessageTypeRegistryWithSystemGit();
        startService(null, "http://localhost:8083/message-contract-service",
                Map.of("spring.docker.compose.enabled", "false"));
    }

    private static void cloneMessageTypeRegistryWithSystemGit() throws IOException, InterruptedException {
        Path cacheDir = Path.of("target", "message-type-repository-cache", sha256(REPO_URL)).normalize();
        ProcessBuilder processBuilder;
        if (cacheDir.resolve("config").toFile().exists()) {
            processBuilder = new ProcessBuilder("git", "-c", "safe.bareRepository=all",
                    "-C", cacheDir.toString(), "fetch", "--prune", "--tags", "origin");
        } else {
            cacheDir.getParent().toFile().mkdirs();
            processBuilder = new ProcessBuilder("git", "-c", "safe.bareRepository=all",
                    "clone", "--mirror", REPO_URL, cacheDir.toString());
        }

        Process process = processBuilder.inheritIO().start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Failed to clone or refresh message type registry cache using system git. Exit code: " + exitCode);
        }
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
