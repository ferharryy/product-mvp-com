package mvp.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.core.Response;
import mvp.resources.WorkItemResource;
import mvp.utils.OllamaUtils;
import mvp.utils.SupabaseUtils;
import mvp.utils.UtilsService;

import java.io.StringReader;
import java.util.List;

import static mvp.utils.UtilsService.parsePayload;

@ApplicationScoped
public class RejectionService {

    @Inject
    OllamaUtils ollamaUtils;

    @Inject
    SupabaseUtils supabaseUtils;

    public void handleRejection(String workItemId, String comment) {
        try {

            // Limpeza do comentário
            String cleanComment = UtilsService.removeHtmlTags(comment);
            cleanComment = comment.trim().equalsIgnoreCase("recuso")
                    ? "Recuso. Faça uma nova sugestão."
                    : cleanComment;

            JsonObject finalMessage = SupabaseUtils.hasFinalAssistantMessage(workItemId);
            int interaction = 0;
            int interactionOrder = 0;
            interaction = finalMessage != null ? finalMessage.getJsonNumber("interaction").intValue() : 0;
            interactionOrder = finalMessage != null ? finalMessage.getJsonNumber("interaction_order").intValue() : 0;

            // Salva a mensagem no Supabase
            boolean saveUserMessage = supabaseUtils.saveUserMessage(workItemId, cleanComment, interaction, interactionOrder);

            if (!saveUserMessage) {
                return;
            }

            List<JsonObject> previousMessages = SupabaseUtils.getMessagesByWorkItemId(workItemId);

            // Monta o array de mensagens para o Ollama
            JsonArrayBuilder messagesArrayBuilder = Json.createArrayBuilder();
            for (JsonObject message : previousMessages) {
                messagesArrayBuilder.add(Json.createObjectBuilder()
                        .add("role", message.getString("sender"))
                        .add("content", message.getString("message")));
            }

            JsonObject chatPayloadObject = Json.createObjectBuilder()
                    .add("model", "codellama")  // Define o modelo como "codellama"
                    .add("messages", messagesArrayBuilder)
                    .build();

            String assistantResponse = ollamaUtils.getChatResponse(chatPayloadObject);
            if (assistantResponse == null) {
                //return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to get chat response").build();
                return;
            }

            if (!SupabaseUtils.saveAssistantMessage(workItemId, assistantResponse, interaction, interactionOrder)) {
                //return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to save assistant message").build();
                return;
            }

            if (!UtilsService.addCommentToWorkItem(workItemId, assistantResponse)) {
                //return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to add comment to work item").build();
                return;
            }

        } catch (Exception e) {
            return;
        }
    }
}
