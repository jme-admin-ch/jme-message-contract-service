# Message Contract Service
Message Contract Service for the JME

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
```shell script
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
[Message Contract Service API](http://localhost:8083/message-contract-service/swagger-ui/index.html?urls.primaryName=MessageContract-Service-API)

The integration test follows the jEAP example structure and starts the service with the shared
`BootServiceSpringIntegrationTestBase`.
It starts the Docker Compose database through Spring Boot's Docker Compose support, so Docker must be available when
running the integration test:
```shell script
./mvnw verify
```

## Note

This repository is part of the open source distribution of jEAP. See [github.com/jeap-admin-ch/jeap](https://github.com/jme-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
