package mvp.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;
import mvp.service.SupabaseService;

import java.util.List;

@ApplicationScoped
public class SupabaseUtils {

    private static final SupabaseService supabaseService = new SupabaseService();

    // Método para salvar WorkItem
    public static boolean saveWorkItem(String workItemId, String title, String description) {
        JsonObject payload = Json.createObjectBuilder()
                .add("id_workitem", workItemId)
                .add("description", description)
                .add("title", title)
                .add("type", "comment")
                .build();

        Response response = supabaseService.saveWorkItem(payload.toString());
        return response.getStatus() == Response.Status.CREATED.getStatusCode();
    }

    // Método para salvar Mensagem
    public static boolean saveMessage(JsonObject payload) {
        Response response = supabaseService.saveMessage(payload.toString());
        return response.getStatus() == Response.Status.CREATED.getStatusCode();
    }

    // Método para obter a próxima interação
    public static JsonObject getNextInteraction(int interaction) {
        return supabaseService.getNextInteraction(interaction);
    }

    // Método para obter a interação com base na ordem
    public static JsonObject getNextInteractionOrder(int interaction, int order) {
        return supabaseService.getNextInteractionOrder(interaction, order);
    }

    // Método para verificar se já existe uma mensagem final do assistente
    public static JsonObject hasFinalAssistantMessage(String workItemId) {
        return supabaseService.hasFinalAssistantMessage(workItemId);
    }

    public static List<JsonObject> getMessagesByWorkItemId(String workItemId){
        return supabaseService.getMessagesByWorkItemId(workItemId);
    }

    public static boolean saveUserMessage(String workItemId, String message, int interaction, int interactionOrder) {
        JsonObject payload = Json.createObjectBuilder()
                .add("message", message)
                .add("created_at", java.time.Instant.now().toString())
                .add("id_workitem", workItemId)
                .add("sender", "user")
                .add("interaction_message_processing", interaction)
                .add("interaction_order", interactionOrder)
                .build();

        Response response = supabaseService.saveMessage(payload.toString());
        return response.getStatus() == Response.Status.CREATED.getStatusCode();
    }

    public static boolean saveAssistantMessage(String workItemId, String message, int interaction, int interactionOrder) {
        JsonObject payload = Json.createObjectBuilder()
                .add("message", message)
                .add("created_at", java.time.Instant.now().toString())
                .add("id_workitem", workItemId)
                .add("sender", "assistant")
                .add("interaction_message_processing", interaction)
                .add("interaction_order", interactionOrder)
                .build();

        Response response = supabaseService.saveMessage(payload.toString());
        return response.getStatus() == Response.Status.CREATED.getStatusCode();
    }

    public static void handleInteraction(JsonObject finalMessage, String comment, int interaction, int interactionOrder) {
        if (comment.toLowerCase().contains("aceito")) {
            int interactionAux = finalMessage != null ? finalMessage.getJsonNumber("interaction").intValue() : 0;
            JsonObject nextInteraction = supabaseService.getNextInteraction(interactionAux);
            //return nextInteraction != null ? nextInteraction.getJsonNumber("interaction").intValue() : 0;
            interaction = nextInteraction != null ? nextInteraction.getJsonNumber("interaction").intValue() : 0;
        }
        interaction = finalMessage != null ? finalMessage.getJsonNumber("interaction").intValue() : 0;
        interactionOrder = finalMessage != null ? finalMessage.getJsonNumber("interaction_order").intValue() : 0;
        //return finalMessage != null ? finalMessage.getJsonNumber("interaction").intValue() : 0;
    }

    public static String prepareChatMessage(int interaction) {
        JsonObject nextInteraction = supabaseService.getNextInteractionOrder(interaction, 0);
        return nextInteraction != null ? nextInteraction.getString("prompt") : null;
    }
}
