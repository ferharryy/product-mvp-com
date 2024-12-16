package mvp.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;
import mvp.resources.OllamaChatResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class OllamaUtils {

    private static final Logger logger = LoggerFactory.getLogger(OllamaUtils.class);

    public String getChatResponse(JsonObject message) {
        /*JsonObject chatPayload = Json.createObjectBuilder()
                .add("model", "codellama")
                .add("messages", Json.createArrayBuilder().add(Json.createObjectBuilder()
                        .add("role", "user")
                        .add("content", message)))
                .build();*/

        Response response = new OllamaChatResource().chat(message.toString());
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
            return null;
        }

        try {
            JsonNode responseBody = new ObjectMapper().readTree(response.getEntity().toString());
            return responseBody.get("content").asText();
        } catch (Exception e) {
            logger.warn("Failed to parse chat response: " + e.getMessage());
            return null;
        }
    }
}
