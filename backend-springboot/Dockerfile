FROM maven:3.9.11-eclipse-temurin-21 AS build
ARG MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}
ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"
WORKDIR /workspace
COPY maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src src
# JAR 保存在镜像层中；仓库缓存只负责加速源码变更后的重新构建。
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn --settings /root/.m2/settings.xml \
        --batch-mode --no-transfer-progress \
        -Dmaven.wagon.http.retryHandler.count=5 \
        -Dmaven.test.skip=true \
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
