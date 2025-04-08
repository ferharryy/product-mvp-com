import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import mvp.service.CommentAcceptService;
import mvp.service.RejectionService;
import mvp.service.WorkItemService;
import mvp.utils.UtilsService;
import org.slf4j.LoggerFactory;

import java.util.logging.Logger;

import static mvp.utils.UtilsService.parsePayload;

@Path("/webhook")
public class WebhookResource {

    private static final Logger logger = Logger.getLogger(WebhookResource.class.getName());
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(WebhookResource.class);

    @Inject
    RejectionService rejectionService;

    @Inject
    CommentAcceptService commentAcceptService;

    @Inject
    WorkItemService workItemService;

    @POST
    @Path("/workitem")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleWebhook(String webhookPayload) {

        // Responde rapidamente ao Azure DevOps
        Response acceptedResponse = Response.accepted().build();

        // Processa a carga útil em segundo plano
        new Thread(() -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(webhookPayload);

                // Extrai informações do payload
                JsonNode fields = rootNode.get("resource").get("fields");
                String description = UtilsService.removeHtmlTags(fields.get("System.Description").asText());
                String title = fields.get("System.Title").asText();
                String type = fields.get("System.WorkItemType").asText();
                String workItemId = rootNode.get("resource").get("id").asText();

                workItemService.processWebhook(workItemId, title, description, "1", "");
            }catch (Exception e){
                e.printStackTrace();
            }
        }).start();

        return acceptedResponse;
    }

    @POST
    @Path("/comment")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleWebhookComment(String webhookPayload) {

        // Processa a carga útil em segundo plano
        new Thread(() -> {
            JsonObject payload = UtilsService.parsePayload(webhookPayload);

            String workItemId = getJsonValue(payload, "resource.id", String.class);
            //String[] keysTitle = {"resource", "fields", "System.Title"};
            //String title =  getNestedJsonValue(payload, keysTitle, String.class);
            String[] keysComment = {"resource", "fields", "System.History"};
            String comment = getNestedJsonValue(payload, keysComment, String.class);
            // Lógica para processar o webhook
            commentAcceptService.processComment(workItemId, comment, "1", "");
        }).start();

        return Response.accepted().build();
    }

    @POST
    @Path("/recuso")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleWebhookRecuso(String payload) {
        // Responde rapidamente ao Azure DevOps
        Response acceptedResponse = Response.accepted().build();

        // Processa a carga útil em segundo plano
        new Thread(() -> {
            JsonObject jsonObject = parsePayload(payload);

            String workItemId = jsonObject.getJsonObject("resource").getString("id");
            String comment = jsonObject.getJsonObject("resource").getJsonObject("fields").getString("System.History");

            // Lógica para processar o webhook
            rejectionService.handleRejection(workItemId, comment);
        }).start();

        return acceptedResponse;
    }

    private <T> T getNestedJsonValue(JsonObject json, String[] keys, Class<T> valueType) {
        try {
            JsonObject temp = json;
            // Percorre os níveis até o penúltimo
            for (int i = 0; i < keys.length - 1; i++) {
                temp = temp.getJsonObject(keys[i]);
                if (temp == null) {
                    throw new IllegalArgumentException("Invalid key: " + keys[i]);
                }
            }
            // Pega o valor final
            String finalKey = keys[keys.length - 1];
            if (!temp.containsKey(finalKey)) {
                throw new IllegalArgumentException("Invalid key: " + finalKey);
            }
            if (valueType == String.class) {
                return valueType.cast(temp.getString(finalKey, null));
            } else if (valueType == Integer.class) {
                return valueType.cast(temp.getInt(finalKey, -1));
            } else if (valueType == Boolean.class) {
                return valueType.cast(temp.getBoolean(finalKey, false));
            } else if (valueType == JsonObject.class) {
                return valueType.cast(temp.getJsonObject(finalKey));
            } else {
                throw new IllegalArgumentException("Unsupported value type: " + valueType.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private <T> T getJsonValue(JsonObject json, String path, Class<T> valueType) {
        try {
            // Processa o caminho diretamente, sem dividir no ponto
            String[] keys = path.split("\\.");
            JsonObject temp = json;

            for (int i = 0; i < keys.length; i++) {
                String currentKey = keys[i];

                // Para a última chave, retorna o valor diretamente
                if (i == keys.length - 1) {
                    if (!temp.containsKey(currentKey)) {
                        throw new IllegalArgumentException("Invalid path: " + currentKey);
                    }

                    if (valueType == String.class) {
                        return valueType.cast(temp.getString(currentKey, null));
                    } else if (valueType == Integer.class) {
                        return valueType.cast(temp.getInt(currentKey, -1));
                    } else if (valueType == Double.class) {
                        return valueType.cast(temp.getJsonNumber(currentKey).doubleValue());
                    } else if (valueType == Boolean.class) {
                        return valueType.cast(temp.getBoolean(currentKey, false));
                    } else if (valueType == JsonObject.class) {
                        return valueType.cast(temp.getJsonObject(currentKey));
                    } else {
                        throw new IllegalArgumentException("Unsupported value type: " + valueType.getName());
                    }
                }

                // Se não for a última chave, continue descendo na estrutura
                if (!temp.containsKey(currentKey) || temp.get(currentKey).getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalArgumentException("Invalid path: " + currentKey);
                }
                temp = temp.getJsonObject(currentKey);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Retorna null se o valor não for encontrado ou houver erro
    }
}
