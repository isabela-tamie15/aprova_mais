###Primeira fase - build
###Maven com Java 25 para compilar o projeto e gerar o JAR
FROM maven:3.9-eclipse-temurin-25 AS build

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

###Cria a pasta de logs dentro do container
RUN mkdir -p logs

###Mostra a porta HTTPS
EXPOSE 8443

###Roda a aplicação
ENTRYPOINT ["java", "-jar", "/app/app.jar"]