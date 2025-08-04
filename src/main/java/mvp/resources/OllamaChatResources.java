package mvp.resources;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder; // Importar para JsonObjectBuilder
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import mvp.service.SupabaseService; // Importar SupabaseService
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/chat-v1")
public class OllamaChatResources {

    @Inject
    JsonWebToken jwt;

    @Inject
    SupabaseService supabaseService; // Injetar SupabaseService

    private static final Logger LOGGER = Logger.getLogger(OllamaChatResources.class.getName()); // Corrigido o nome da classe do logger

    // Memória por usuário
    private final Map<String, ChatMemory> userMemories = new ConcurrentHashMap<>();

    // Modelo de linguagem do Ollama
    private final ChatLanguageModel model = OllamaChatModel.builder()
            .baseUrl("http://127.0.0.1:11434")  // ajuste se necessário
            .modelName("codellama")               // ou "mistral", "phi", etc.
            .build();

    @POST
    @RolesAllowed("user")
    public Response chat(Map<String, String> json) {
        String userId = json.get("userId");
        String userMessage = json.get("message");

        // Log inicial da mensagem recebida
        JsonObjectBuilder initialLogContext = Json.createObjectBuilder()
                .add("userId", userId != null ? userId : "N/A")
                .add("userMessage", userMessage != null ? userMessage : "N/A");
        supabaseService.saveLog("INFO", "Mensagem de chat recebida", initialLogContext.build());

        try {
            if (userId == null || userMessage == null || userId.isEmpty() || userMessage.isEmpty()) {
                supabaseService.saveLog("WARN", "Requisição de chat inválida: userId ou message ausente", initialLogContext.build());
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Parâmetros 'userId' e 'message' são obrigatórios").build();
            }

            // Recupera ou cria memória do usuário
            ChatMemory memory = userMemories.computeIfAbsent(userId, id ->
                    MessageWindowChatMemory.withMaxMessages(10));

            // Adiciona a mensagem do usuário à memória
            memory.add(UserMessage.from(userMessage));

            // Gera resposta com base na memória
            List<ChatMessage> history = memory.messages();
            AiMessage aiResponse = model.generate(history).content();

            // Adiciona a resposta da IA à memória
            memory.add(aiResponse);

            // Retorna o conteúdo como JSON
            JsonObject responseJson = Json.createObjectBuilder()
                    .add("content", aiResponse.text())
                    .build();

            // Log da resposta da IA
            JsonObjectBuilder responseLogContext = Json.createObjectBuilder()
                    .add("userId", userId)
                    .add("userMessage", userMessage)
                    .add("aiResponse", aiResponse.text());
            supabaseService.saveLog("INFO", "Resposta da IA gerada", responseLogContext.build());

            return Response.ok(responseJson).build();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro no processamento do chat", e);
            // Log de erro
            JsonObjectBuilder errorLogContext = Json.createObjectBuilder()
                    .add("userId", userId != null ? userId : "N/A")
                    .add("userMessage", userMessage != null ? userMessage : "N/A")
                    .add("errorMessage", e.getMessage());
            if (e.getCause() != null) {
                errorLogContext.add("cause", e.getCause().getMessage());
            }
            supabaseService.saveLog("ERROR", "Erro no processamento do chat", errorLogContext.build());

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro interno no servidor").build();
        }
    }
}
