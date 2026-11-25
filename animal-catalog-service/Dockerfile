FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/animal-catalog-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
