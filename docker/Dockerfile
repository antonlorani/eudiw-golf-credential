FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon \
    && find build/libs -type f -name '*.jar' ! -name '*-plain.jar' \
       -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar

USER app
EXPOSE 8443
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
