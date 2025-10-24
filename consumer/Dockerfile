FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy parent pom and module poms
COPY pom.xml .
COPY producer/pom.xml producer/
COPY consumer/pom.xml consumer/

# Download dependencies
RUN mvn dependency:go-offline -pl consumer -am

# Copy source code
COPY consumer/src consumer/src

# Build the application
RUN mvn clean package -pl consumer -am -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the jar from build stage
COPY --from=build /app/consumer/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]