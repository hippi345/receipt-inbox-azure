# One image definition for both jars. Pass MODULE=receipt-api or MODULE=receipt-worker.
FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG MODULE=receipt-api
WORKDIR /src
COPY . .
RUN mvn -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
ARG MODULE=receipt-api
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
