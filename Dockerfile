# Use a imagem do OpenJDK como base
FROM eclipse-temurin:21-jdk AS build

# Defina o diretório de trabalho
WORKDIR /app

# Copie o arquivo JAR do seu projeto (substitua 'target/product-mvp-com-1.0.0.jar' pelo caminho correto)
COPY target/quarkus-app/ /app/target

# Exponha a porta que sua aplicação Quarkus está usando (por padrão é 8080)
EXPOSE 8080

# Comando para executar a aplicação
CMD ["java", "-jar", "/app/target/quarkus-run.jar"]
