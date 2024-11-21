package mvp.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@ApplicationScoped
public class SupabaseService {

    @ConfigProperty(name = "supabase.url")
    String supabaseUrl;

    @ConfigProperty(name = "supabase.api-key")
    String apiKey;

    private final Client client = ClientBuilder.newClient();

    public Response saveMessage(String messageJson) {
        String url = supabaseUrl + "/rest/v1/messages";

        Response response = client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .post(Entity.json(messageJson));

        return response;
    }

    public Response saveWorkItem(String workItemJson) {
        String url = supabaseUrl + "/rest/v1/work_item";

        Response response = client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .post(Entity.json(workItemJson));

        return response;
    }

    public List<JsonObject> getMessagesByWorkItemId(int workItemId) {
        List<JsonObject> messages = new ArrayList<>();
        try {
            URL url = new URL(supabaseUrl + "/rest/v1/messages?id_workitem=eq." + workItemId + "&order=id");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", apiKey);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Scanner scanner = new Scanner(conn.getInputStream());
                StringBuilder response = new StringBuilder();
                while (scanner.hasNext()) {
                    response.append(scanner.nextLine());
                }
                scanner.close();

                JsonReader jsonReader = Json.createReader(new StringReader(response.toString()));
                JsonArray jsonArray = jsonReader.readArray();

                for (int i = 0; i < jsonArray.size(); i++) {
                    messages.add(jsonArray.getJsonObject(i));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return messages;
    }

    public JsonObject hasFinalAssistantMessage(int workItemId) {
        try {
            // Consulta para buscar a última interação e o estado "is_final" usando a view
            URL url = new URL(supabaseUrl + "/rest/v1/final_assistant_messages?id_workitem=eq." + workItemId + "&order=interaction.desc,interaction_order.desc&limit=1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", apiKey);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Scanner scanner = new Scanner(conn.getInputStream());
                StringBuilder response = new StringBuilder();
                while (scanner.hasNext()) {
                    response.append(scanner.nextLine());
                }
                scanner.close();

                JsonReader jsonReader = Json.createReader(new StringReader(response.toString()));
                JsonArray jsonArray = jsonReader.readArray();

                // Se não encontrar mensagens, retorna null
                if (jsonArray.isEmpty()) {
                    return null;
                }

                // Pega o último registro
                JsonObject message = jsonArray.getJsonObject(0);

                // Retorna um JsonObject com as informações de interação e status final
                return Json.createObjectBuilder()
                        .add("interaction", message.getJsonNumber("interaction").intValue())
                        .add("interaction_order", message.getJsonNumber("interaction_order").intValue())
                        .add("is_final", message.getBoolean("is_final"))
                        .build();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;  // Retorna null se não encontrar a última mensagem do assistente
    }

    public JsonObject getNextInteractionOrder(int lastInteraction, int lastInteractionOrder) {
        try {
            int nextInteractionOrder = lastInteractionOrder + 1;

            // Consulta para buscar a próxima interação
            URL url = new URL(supabaseUrl + "/rest/v1/message_processing?&interaction=eq." + lastInteraction + "&interaction_order=eq." + nextInteractionOrder);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", apiKey);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Scanner scanner = new Scanner(conn.getInputStream());
                StringBuilder response = new StringBuilder();
                while (scanner.hasNext()) {
                    response.append(scanner.nextLine());
                }
                scanner.close();

                JsonReader jsonReader = Json.createReader(new StringReader(response.toString()));
                JsonArray jsonArray = jsonReader.readArray();

                // Se não encontrar a próxima interação, retorna null
                if (jsonArray.isEmpty()) {
                    return null;
                }

                // Retorna a próxima interação encontrada
                JsonObject message = jsonArray.getJsonObject(0);

                // Retorna um JsonObject com as informações de interação e status final
                return Json.createObjectBuilder()
                        .add("prompt", message.getString("prompt"))
                        .add("interaction", message.getJsonNumber("interaction").intValue())
                        .add("interaction_order", message.getJsonNumber("interaction_order").intValue())
                        .build();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;  // Retorna null se não encontrar a próxima interação
    }

    public JsonObject getNextInteraction(int lastInteraction) {
        try {
            int nextInteraction = lastInteraction + 1;

            // Consulta para buscar a próxima interação
            URL url = new URL(supabaseUrl + "/rest/v1/message_processing?&interaction=eq." + nextInteraction);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", apiKey);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Scanner scanner = new Scanner(conn.getInputStream());
                StringBuilder response = new StringBuilder();
                while (scanner.hasNext()) {
                    response.append(scanner.nextLine());
                }
                scanner.close();

                JsonReader jsonReader = Json.createReader(new StringReader(response.toString()));
                JsonArray jsonArray = jsonReader.readArray();

                // Se não encontrar a próxima interação, retorna null
                if (jsonArray.isEmpty()) {
                    return null;
                }

                // Retorna a próxima interação encontrada
                JsonObject message = jsonArray.getJsonObject(0);

                // Retorna um JsonObject com as informações de interação e status final
                return Json.createObjectBuilder()
                        .add("prompt", message.getString("prompt"))
                        .add("interaction", message.getJsonNumber("interaction").intValue())
                        .add("interaction_order", message.getJsonNumber("interaction_order").intValue())
                        .build();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;  // Retorna null se não encontrar a próxima interação
    }


}
