FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
# Maven Central 偶发超时时保留已下载依赖，并允许 Wagon 自动重试。
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -q \
        -Dmaven.wagon.http.retryHandler.count=5 \
        -DskipTests \
        dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -q \
        -Dmaven.wagon.http.retryHandler.count=5 \
        -DskipTests \
        package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 opsmind
COPY --from=build /workspace/target/backend-springboot-0.0.1-SNAPSHOT.jar app.jar
USER opsmind
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
