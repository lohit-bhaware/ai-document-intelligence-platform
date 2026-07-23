FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/docai-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
<<<<<<< HEAD
ENV PORT=8080
ENTRYPOINT java -Dserver.port=${PORT} -jar app.jar
=======
ENTRYPOINT ["java", "-jar", "app.jar"]
>>>>>>> c233ab181691713d522d27b893ce18cae18017e5
