FROM bit-base-images-docker-hosted.nexus.bit.admin.ch/bit/eclipse-temurin:25-jre-ubi9-minimal

USER 0
EXPOSE 8080

ENV JAR_FILE=jme-message-contract-service.jar

WORKDIR /app

RUN curl https://repo.bit.admin.ch/repository/raw-jeap-hosted/rhos/entrypoint/entrypoint-1.0.0.sh > entrypoint.sh

# Update Java truststore
RUN set -uxe && \
    chmod g=u ./entrypoint.sh &&\
    chmod +x ./entrypoint.sh &&\
    chmod g=u $JAVA_HOME/lib/security/cacerts

COPY target/${JAR_FILE} /app/
USER 1001

ENTRYPOINT "./entrypoint.sh" "${JAR_FILE}"