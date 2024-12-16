package mvp.utils;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.core.Response;
import mvp.resources.WorkItemResource;
import mvp.service.CommentAcceptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UtilsService {

    private static final Logger logger = LoggerFactory.getLogger(UtilsService.class);

    // Método para remover tags HTML
    public static String removeHtmlTags(String html) {
        // Usando expressão regular para remover tags HTML
        Pattern pattern = Pattern.compile("<.*?>");
        Matcher matcher = pattern.matcher(html);
        return matcher.replaceAll("").trim();
    }

    public static JsonObject parsePayload(String payload) {
        try (JsonReader reader = Json.createReader(new StringReader(payload))) {
            return reader.readObject();
        } catch (Exception e) {
            logger.warn("Failed to parse payload: " + e.getMessage());
            return null;
        }
    }

    public static boolean addCommentToWorkItem(int workItemId, String comment) {
        JsonObject payload = Json.createObjectBuilder()
                .add("text", comment)
                .build();

        Response response = new WorkItemResource().addComment(workItemId, payload.toString());
        return response.getStatus() == Response.Status.OK.getStatusCode();
    }
}
