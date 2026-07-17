FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn

RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline -B

COPY src ./src

RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN groupadd -r appuser && useradd -r -g appuser appuser

RUN mkdir -p /opt/tienda-online/uploads \
    && chown -R appuser:appuser /opt/tienda-online

COPY --from=builder /app/target/*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]