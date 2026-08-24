# Message Contract Service

This example project shows how to assemble a runnable
[Message Contract Service](https://github.com/jeap-admin-ch/jeap-message-contract-service) instance using the
`jeap-message-contract-service-instance` starter, backed by a PostgreSQL database.

## What this example demonstrates

The Message Contract Service tracks, per application and topic, which message types producers publish and
consumers expect (a "contract"), checks compatibility between producer and consumer contracts against message
schemas resolved from a [message type registry](https://github.com/jme-admin-ch/jme-message-type-registry)
git repository, and records which contract version is deployed to which environment so incompatible deployments
can be detected before they happen. The integration test (`MessageContractServiceExampleIT`) exercises the full
workflow end to end:

1. Registering a producer's message contracts (`PUT /contracts/{app}/{version}`, role `PRODUCER`).
2. Registering a consumer's message contract for the same message type/topic (role `CONSUMER`).
3. Registering a consumer contract for an *incompatible* topic, to later demonstrate a compatibility failure.
4. Registering a deployment of the producer to an environment (`PUT /deployments/{app}/{version}/{env}`).
5. Checking deployment compatibility for the compatible consumer (`GET /deployments/compatibility/...` → `200 OK`).
6. Checking deployment compatibility for the incompatible consumer (→ `412 Precondition Failed`).
7. Listing all recorded deployments (`GET /deployments`).

## Installing / Getting started
Run the application with the `local` profile:
```shell script
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile starts the PostgreSQL database from `docker/docker-compose.yml` automatically through Spring Boot
Docker Compose. Docker must be running before starting the application.

You can also start the database manually, for example when you want to keep it running across application restarts:
```shell script
cd docker && docker compose up
```

Then start the application with the same Maven command shown above and access swagger at
[Message Contract Service API](http://localhost:8083/message-contract-service/swagger-ui/index.html?urls.primaryName=MessageContract-Service-API)

The integration test follows the jEAP example structure and starts the service with the shared
`BootServiceSpringIntegrationTestBase`.
It starts the Docker Compose database through Spring Boot's Docker Compose support, so Docker must be available when
running the integration test:
```shell script
./mvnw verify
```

## Note

This repository is part of the open source distribution of JME. See [github.com/jme-admin-ch/jme](https://github.com/jme-admin-ch/jme)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
