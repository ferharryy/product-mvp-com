package mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;
import mvp.resources.WorkItemResource;
import mvp.utils.OllamaUtils;
import mvp.utils.SupabaseUtils;
import mvp.utils.UtilsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class WorkItemService {

    private static final Logger logger = LoggerFactory.getLogger(WorkItemService.class);

    @Inject
    OllamaUtils ollamaUtils;

    @Inject
    WorkItemResource workItemResource;

    @Inject
    SupabaseUtils supabaseUtils;  // Usando a nova classe utilitária

    public Response processWebhook(String workItemId, String title, String description, String plataform, String url) {
        try {
            // Parse do JSON recebido


            logger.info("Processing Work Item: ID={}, Title={}", workItemId, title);

            boolean supabaseResponse = supabaseUtils.saveWorkItem(workItemId, title, description);

            if (!supabaseResponse) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to save Work Item").build();
            }

            // Busca próxima interação no Supabase
            JsonObject nextInteraction = supabaseUtils.getNextInteractionOrder(1, 0);
            String message = nextInteraction != null
                    ? nextInteraction.getString("prompt") + " " + description
                    : description;

            if (!SupabaseUtils.saveUserMessage(workItemId, message, 1, 1)){
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to save user message").build();
            }
            JsonArrayBuilder messagesArrayBuilder = Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                            .add("role", "user")
                            .add("content", message)
                    );
            JsonObject chatPayloadObject = Json.createObjectBuilder()
                    .add("model", "codellama")  // Define o modelo como "codellama"
                    .add("messages", messagesArrayBuilder)
                    .build();

            // Convertendo para string o payload
            String chatPayload = chatPayloadObject.toString();
            // Envia mensagem ao Ollama
            String assistantResponse = ollamaUtils.getChatResponse(chatPayloadObject);
            if (assistantResponse == null) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to send message to Ollama").build();
            }

            if (!SupabaseUtils.saveAssistantMessage(workItemId, assistantResponse, 1, 1)) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to save assistant message").build();
            }

            if (plataform.equals("0") && !UtilsService.addCommentToJira(workItemId, assistantResponse, url)){
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to add comment to work item").build();
            }else if (!UtilsService.addCommentToWorkItem(workItemId, assistantResponse)) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to add comment to work item").build();
            }

            return Response.ok("Process completed successfully").build();

        } catch (Exception e) {
            logger.error("Error processing webhook", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error processing webhook").build();
        }
    }
}
