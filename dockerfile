FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package \
    && cp target/*.jar /app/hookport.jar

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app/hookport.jar hookport.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "hookport.jar"]