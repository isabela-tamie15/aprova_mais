###Primeira fase - build
###Maven com Java 25 para compilar o projeto e gerar o JAR
FROM maven:3.9-eclipse-temurin-26 AS build

WORKDIR /app

###Faz copia do pom.xml primeiro para aproveitar o cache do Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

###Faz copia do resto do projeto e gera o JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

###Segunda fase - execução
###Usa apenas o JRE para rodar o JAR, sem o Maven
FROM eclipse-temurin:25-jre

WORKDIR /app

### Faz copia apenas do JAR gerado no estágio anterior
COPY --from=build /app/target/aprovamais-0.0.1-SNAPSHOT.jar app.jar

###Usuário sem privilégio de root
RUN groupadd --system aprova && useradd --system --gid aprova --home-dir /app aprova \
    && mkdir -p logs uploads && chown -R aprova:aprova /app
USER aprova

###Porta da aplicação. No Render, vem da variável PORT
EXPOSE 8080

###Roda a aplicação, com a memória da JVM limitada à do contêiner
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]