package mvp.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class SupabaseService {

    private static final Logger LOGGER = Logger.getLogger(SupabaseService.class.getName());

    //@ConfigProperty(name = "supabase.url", defaultValue = "https://retvgrkwnakvbruzjcxn.supabase.co")
    String supabaseUrl = "https://retvgrkwnakvbruzjcxn.supabase.co";

    //@ConfigProperty(name = "supabase.api-key", defaultValue = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJldHZncmt3bmFrdmJydXpqY3huIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzAyNDc2MzEsImV4cCI6MjA0NTgyMzYzMX0._nFCHtv9ZPudKItIzRy6M98VhzqLoHjLJhH80G-ruHI")
    String apiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJldHZncmt3bmFrdmJydXpqY3huIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzAyNDc2MzEsImV4cCI6MjA0NTgyMzYzMX0._nFCHtv9ZPudKItIzRy6M98VhzqLoHjLJhH80G-ruHI";

    private final Client client = ClientBuilder.newClient();

    // Método para enviar requisição POST genérica
    private Response sendPostRequest(String url, String jsonBody) {
        return client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .post(Entity.json(jsonBody));
    }

    // Método para fazer GET e processar resposta em JSON
    private JsonArray sendGetRequest(String url) {
        try {
            Response response = client.target(url)
                    .request(MediaType.APPLICATION_JSON)
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .get();
            if (response.getStatus() == 200) {
                return response.readEntity(JsonArray.class);
            } else {
                LOGGER.severe("Erro ao fazer GET para " + url + ": " + response.getStatus());
            }
        } catch (Exception e) {
            LOGGER.severe("Erro ao fazer GET para " + url + ": " + e.getMessage());
        }
        return Json.createArrayBuilder().build(); // Retorna um array vazio em caso de erro
    }

    public Response saveMessage(String messageJson) {
        return sendPostRequest(supabaseUrl + "/rest/v1/messages", messageJson);
    }

    public Response saveWorkItem(String workItemJson) {
        return sendPostRequest(supabaseUrl + "/rest/v1/work_item", workItemJson);
    }

    public List<JsonObject> getMessagesByWorkItemId(int workItemId) {
        String url = supabaseUrl + "/rest/v1/messages?id_workitem=eq." + workItemId + "&order=id";
        JsonArray jsonArray = sendGetRequest(url);
        List<JsonObject> messages = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            messages.add(jsonArray.getJsonObject(i));
        }
        return messages;
    }

    public JsonObject hasFinalAssistantMessage(int workItemId) {
        String url = supabaseUrl + "/rest/v1/final_assistant_messages?id_workitem=eq." + workItemId + "&order=interaction.desc,interaction_order.desc&limit=1";
        JsonArray jsonArray = sendGetRequest(url);
        if (jsonArray.isEmpty()) {
            return null;  // Retorna null se não encontrar mensagens
        }
        JsonObject message = jsonArray.getJsonObject(0);
        return Json.createObjectBuilder()
                .add("interaction", message.getInt("interaction"))
                .add("interaction_order", message.getInt("interaction_order"))
                .add("is_final", message.getBoolean("is_final"))
                .build();
    }

    public JsonObject getNextInteractionOrder(int lastInteraction, int lastInteractionOrder) {
        String url = supabaseUrl + "/rest/v1/message_processing?interaction=eq." + lastInteraction + "&interaction_order=eq." + (lastInteractionOrder + 1);
        JsonArray jsonArray = sendGetRequest(url);
        if (jsonArray.isEmpty()) {
            return null;  // Retorna null se não encontrar a próxima interação
        }
        JsonObject message = jsonArray.getJsonObject(0);
        return Json.createObjectBuilder()
                .add("prompt", message.getString("prompt"))
                .add("interaction", message.getInt("interaction"))
                .add("interaction_order", message.getInt("interaction_order"))
                .build();
    }

    public JsonObject getNextInteraction(int lastInteraction) {
        String url = supabaseUrl + "/rest/v1/message_processing?interaction=eq." + (lastInteraction + 1);
        JsonArray jsonArray = sendGetRequest(url);
        if (jsonArray.isEmpty()) {
            return null;  // Retorna null se não encontrar a próxima interação
        }
        JsonObject message = jsonArray.getJsonObject(0);
        return Json.createObjectBuilder()
                .add("prompt", message.getString("prompt"))
                .add("interaction", message.getInt("interaction"))
                .add("interaction_order", message.getInt("interaction_order"))
                .add("is_final", message.getBoolean("is_final"))
                .build();
    }
}
