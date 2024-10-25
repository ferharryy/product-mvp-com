package mvp.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/chat")
public class OllamaChatResource {

    private static final Logger LOGGER = Logger.getLogger(OllamaChatResource.class.getName());

    @POST
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response chat(String requestBody) {
        //String url = "http://host.docker.internal:11434/api/chat";  // Atualize a URL se necessário
        String url = "http://localhost:11434/api/chat";

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(url);
            request.setHeader("Content-Type", "application/json");

            // Configura o payload JSON a partir do corpo da requisição (requestBody)
            StringEntity entity = new StringEntity(requestBody, StandardCharsets.UTF_8);
            request.setEntity(entity);

            try (CloseableHttpResponse response = client.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                LOGGER.info("Resposta recebida com status: " + statusCode);

                if (statusCode == 200) {
                    String jsonResponse = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    //LOGGER.info("Resposta do Ollama: " + jsonResponse);

                    // Use Jackson para processar o JSON
                    ObjectMapper mapper = new ObjectMapper();

                    // A resposta pode conter múltiplos objetos JSON, então é necessário dividir o jsonResponse
                    String[] jsonParts = jsonResponse.split("(?<=})\\s*(?=\\{)");

                    // Variável para armazenar o conteúdo concatenado
                    StringBuilder contentBuilder = new StringBuilder();

                    // Percorrer as mensagens fragmentadas e extrair o campo "content"
                    for (String jsonPart : jsonParts) {
                        JsonNode node = mapper.readTree(jsonPart);
                        JsonNode messageNode = node.get("message");
                        if (messageNode != null && messageNode.get("content") != null) {
                            contentBuilder.append(messageNode.get("content").asText());
                        }
                    }

                    // Retornar apenas o conteúdo concatenado
                    String fullContent = contentBuilder.toString();
                    LOGGER.info("Conteúdo final concatenado");
                    JsonObject chatPayloadObject = Json.createObjectBuilder()
                            .add("content", fullContent)
                            .build();
                    return Response.ok(chatPayloadObject).build();

                } else {
                    LOGGER.severe("Falha ao enviar para o Ollama. Status: " + statusCode);
                    return Response.status(statusCode).entity("Falha ao enviar para o Ollama").build();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao processar a requisição", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Ocorreu um erro").build();
        }
    }
}
