# Multi-stage Docker build for MeshChat Server
FROM openjdk:17-jdk-slim as builder

# Install build dependencies
RUN apt-get update && apt-get install -y \
    curl \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts ./
COPY settings.gradle.kts ./

# Make gradlew executable
RUN chmod +x ./gradlew

# Download dependencies (for better caching)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src ./src

# Build the application
RUN ./gradlew shadowJar --no-daemon

# Production stage
FROM openjdk:17-jre-slim

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    curl \
    dumb-init \
    && rm -rf /var/lib/apt/lists/*

# Create application user
RUN groupadd -r meshchat && useradd -r -g meshchat meshchat

# Set working directory
WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/build/libs/meshchat-server.jar ./meshchat-server.jar

# Copy configuration files
COPY src/main/resources/application.conf ./application.conf
COPY src/main/resources/logback.xml ./logback.xml

# Create data directory for database
RUN mkdir -p /app/data && chown -R meshchat:meshchat /app

# Switch to non-root user
USER meshchat

# Expose ports
EXPOSE 8080 9090

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:9090/health || exit 1

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Run the application
CMD ["java", \
     "-server", \
     "-XX:+UseG1GC", \
     "-XX:MaxGCPauseMillis=200", \
     "-XX:+UseStringDeduplication", \
     "-Xms512m", \
     "-Xmx2g", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-Dlogback.configurationFile=logback.xml", \
     "-jar", "meshchat-server.jar"]