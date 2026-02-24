FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

COPY target/backend-0.0.1-SNAPSHOT.jar /app/

EXPOSE 8080

CMD [ "java", "-jar", "/app/backend-0.0.1-SNAPSHOT.jar" ]
