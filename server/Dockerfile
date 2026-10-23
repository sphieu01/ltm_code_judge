# Stage 1: Build fat JAR bằng Maven
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Runtime Image chứa Java 21 và các trình biên dịch (C, C++, Python)
FROM eclipse-temurin:21-jdk-alpine
LABEL maintainer="LTM Auto Grader Team"

# Cài đặt gcc, g++, python3, bash để hỗ trợ sandbox chấm bài trực tiếp an toàn
RUN apk update && \
    apk add --no-cache \
    build-base \
    gcc \
    g++ \
    python3 \
    bash

WORKDIR /app
COPY --from=builder /build/target/code-grading-server-1.0.0.jar app.jar

# Tạo thư mục tạm cho judge
RUN mkdir -p /app/temp/judge

EXPOSE 9999

ENV SERVER_PORT=9999
ENV CONNECTION_POOL_SIZE=20
ENV JUDGE_WORKER_COUNT=4

CMD ["java", "-jar", "app.jar"]
