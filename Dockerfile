# ---- Build Stage ----
FROM gradle:4.7-jdk8 AS build
WORKDIR /app

# Switch to root user for permission setup
USER root

# Copy the entire source code into the Docker container
COPY . .

# Grant write permissions to Gradle
RUN chown -R gradle:gradle /app
RUN chmod -R 777 /app

# Switch back to gradle user
USER gradle

# Build the fat JAR (skip tests and disable daemon)
RUN gradle clean build -x test --no-daemon --stacktrace

# ---- Runtime Stage ----
FROM openjdk:8-jdk
WORKDIR /app

# Copy the fat jar from the build stage
COPY --from=build /app/build/libs/*.jar app.jar
# Copy folder "assets" vào thư mục cùng cấp với app.jar
COPY --from=build /app/assets /app/assets
COPY index.html /app/index.html

# Expose the default port (can be overridden later)
EXPOSE 9090

# Log information when starting the application
ENTRYPOINT ["java", "-jar", "app.jar"]
