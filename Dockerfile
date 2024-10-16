# Estágio 1: Construir a aplicação usando Maven
FROM maven:3.9.9-eclipse-temurin-21 AS build

# Defina o diretório de trabalho para a fase de build
WORKDIR /app

# Copie o arquivo pom.xml e as dependências para cache
COPY pom.xml ./
COPY src ./src

# Execute o comando de build usando Maven
RUN mvn clean install -DskipTests

# Estágio 2: Criar a imagem final da aplicação
FROM eclipse-temurin:21-jdk

# Defina o diretório de trabalho para a aplicação
WORKDIR /app

# Copie os arquivos construídos do estágio de build
COPY --from=build /app/target/quarkus-app/ /app/target

# Exponha a porta que a aplicação Quarkus está utilizando (8080 por padrão)
EXPOSE 8080

# Comando para rodar a aplicação
CMD ["java", "-jar", "/app/target/quarkus-run.jar"]
