# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build
COPY settings-docker.xml /build/settings-docker.xml
COPY . /build

# 要构建的模块：common / auth / resume / job-match / application / gateway
ARG MODULE
RUN mvn -B -ntp -s settings-docker.xml -pl "${MODULE}" -am -DskipTests package

# ---------- run ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ARG MODULE
ARG JAR_NAME
COPY --from=build "/build/${MODULE}/target/${JAR_NAME}" /app/app.jar

ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
