FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar /app/
EXPOSE 8080
CMD ["java","-jar","/app/backend-0.0.1-SNAPSHOT.jar"]
