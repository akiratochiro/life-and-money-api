# Etapa 1: compilar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# O pom e o wrapper vêm antes do código: enquanto as dependências não mudam, esta camada
# fica em cache e o build não baixa nada de novo.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests -B

# Etapa 2: rodar
# Só o JRE e o jar: a imagem final não carrega o JDK nem o repositório do Maven.
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8080

# MaxRAMPercentage faz a JVM dimensionar o heap pelo limite do container, e não pela memória
# da máquina: sem isso, numa instância de 512 MB ela reservaria heap demais e seria morta.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "app.jar"]
