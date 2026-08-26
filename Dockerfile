# =========================================================
# BUILD STAGE
# =========================================================
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy pom.xml trước để tận dụng Docker cache
COPY pom.xml .

# Download Maven dependencies
RUN mvn -B dependency:go-offline

# Copy source code
COPY src ./src

# Build Spring Boot application
RUN mvn -B clean package -DskipTests


# =========================================================
# RUNTIME STAGE
# =========================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Tối ưu JVM khi chạy trên Railway
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# Copy JAR được tạo từ pom.xml
COPY --from=build /workspace/target/digishop-*.jar /app/app.jar

# Port mặc định của Spring Boot
EXPOSE 8080

# Start application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]