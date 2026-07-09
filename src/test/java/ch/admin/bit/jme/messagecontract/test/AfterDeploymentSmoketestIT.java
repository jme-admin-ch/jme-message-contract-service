package ch.admin.bit.jme.messagecontract.test;

import ch.admin.bit.jeap.messagecontract.web.api.dto.CreateMessageContractsDto;
import ch.admin.bit.jeap.messagecontract.web.api.dto.MessageContractRole;
import ch.admin.bit.jeap.messagecontract.web.api.dto.NewMessageContractDto;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.List;

import static ch.admin.bit.jeap.messagecontract.web.api.dto.CompatibilityMode.BACKWARD;
import static ch.admin.bit.jeap.messagecontract.web.api.dto.MessageContractRole.CONSUMER;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.preemptive;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

@Slf4j
@TestMethodOrder(OrderAnnotation.class)
@EnabledIfSystemProperty(named = AfterDeploymentSmoketestIT.DEPLOY_STAGE_PROPERTY_NAME, matches = "d")
class AfterDeploymentSmoketestIT {

    static final String DEPLOY_STAGE_PROPERTY_NAME = "deployStage";
    private static final String MASTER = "master";
    private static final String NO_COMMIT_HASH = null;
    private static final String REPO_URL = "https://bitbucket.bit.admin.ch/scm/bit_jme/jme-message-type-registry.git";
    private static final String MESSAGE_TYPE = "JmeCreateDeclarationCommand";
    private static final String TOPIC_INCOMPATIBLE = "topic-incompatible";
    private static final String TOPIC = "topic";
    private static final String V1 = "1.0.0";
    private static final String V2 = "2.0.0";
    private static final String ENCRYPTION_KEY_ID = "messaging-key-id";

    private RequestSpecification request;

    @Order(1)
    @Test
    void putProducerContracts() {
        NewMessageContractDto producerContractV1Dto = new NewMessageContractDto(
                MESSAGE_TYPE, V1,
                TOPIC, MessageContractRole.PRODUCER, REPO_URL, NO_COMMIT_HASH, MASTER, BACKWARD, null);
        NewMessageContractDto producerContractV2Dto = new NewMessageContractDto(
                MESSAGE_TYPE, V2,
                TOPIC_INCOMPATIBLE, MessageContractRole.PRODUCER, REPO_URL, NO_COMMIT_HASH, MASTER, BACKWARD, ENCRYPTION_KEY_ID);
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
                TOPIC, CONSUMER, REPO_URL, NO_COMMIT_HASH, MASTER, BACKWARD, null);
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
                TOPIC_INCOMPATIBLE, CONSUMER, REPO_URL, NO_COMMIT_HASH, MASTER, BACKWARD, null);
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
        String deployStage = System.getProperty(DEPLOY_STAGE_PROPERTY_NAME, "d");
        String baseUri = "https://bit-jme-%s.apps.p-szb-ros-shrd-npr-01.cloud.admin.ch/".formatted(deployStage);
        RestAssured.useRelaxedHTTPSValidation();
        RestAssured.config.getLogConfig().blacklistHeader(HttpHeaders.AUTHORIZATION, HttpHeaders.SET_COOKIE);
        RestAssured.filters(new ResponseLoggingFilter());

        RequestSpecBuilder builder = new RequestSpecBuilder();
        builder.setBaseUri(baseUri);
        builder.setBasePath("/message-contract-service/api");
        builder.setAuth(preemptive().basic("write", "secret"));
        request = builder.build();
    }
}
