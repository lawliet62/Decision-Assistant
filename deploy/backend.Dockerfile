ARG BASE_IMAGE=eclipse-temurin:21-jre-jammy
FROM ${BASE_IMAGE}
WORKDIR /app
COPY backend/build/libs/decision-assistant-api-*-SNAPSHOT.jar /app/application.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
