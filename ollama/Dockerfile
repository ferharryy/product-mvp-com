FROM ubuntu:22.04

# Instalar dependências
RUN apt-get update && \
    apt-get install -y curl libcurl4-openssl-dev ca-certificates libssl-dev

# Baixar e executar o script de instalação do Ollama
RUN curl -fsSL https://ollama.com/install.sh | bash

# Executar o ollama
#RUN ollama start

# Baixar o modelo Llama 3 durante o build
#RUN ollama run codellama

# Definir o diretório de trabalho
WORKDIR /ollama

# Expor a porta padrão do Ollama
EXPOSE 11434

# Comando para iniciar o servidor Ollama
CMD ["ollama", "serve"]
