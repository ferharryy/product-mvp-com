package mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import mvp.utils.OllamaUtils;
import mvp.utils.SupabaseUtils;
import mvp.utils.UtilsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class CommentAcceptService {

    private static final Logger logger = LoggerFactory.getLogger(CommentAcceptService.class);

    @Inject
    OllamaUtils ollamaUtils;

    public void processComment(String webhookPayload) {
        try {
            JsonObject payload = UtilsService.parsePayload(webhookPayload);
            if (payload == null) {
                return;
            }

            int workItemId = getJsonValue(payload, "resource.id", Integer.class);
            String[] keysTitle = {"resource", "fields", "System.Title"};
            String title =  getNestedJsonValue(payload, keysTitle, String.class);
            String[] keysComment = {"resource", "fields", "System.History"};
            String comment = getNestedJsonValue(payload, keysComment, String.class);
            logger.info("Chegou comentario id: " + workItemId);

            if (workItemId == -1 || title == null || comment == null) {
                return;
            }

            JsonObject finalMessage = SupabaseUtils.hasFinalAssistantMessage(workItemId);
            int interaction = 0;
            int interactionOrder = 0;
            String messageToChat = "";
            Boolean isFinal = false;

            if (comment.toLowerCase().contains("aceito")) {
                int interactionAux = finalMessage != null ? finalMessage.getJsonNumber("interaction").intValue() : 0;
                JsonObject nextInteraction = SupabaseUtils.getNextInteraction(interactionAux);
                interaction = nextInteraction != null ? nextInteraction.getJsonNumber("interaction").intValue() : 0;
                isFinal = nextInteraction != null ? nextInteraction.getBoolean("is_final") : false;
            }
            else {
                interaction = finalMessage != null ? finalMessage.getJsonNumber("interaction").intValue() : 0;
                interactionOrder = finalMessage != null ? finalMessage.getJsonNumber("interaction_order").intValue() : 0;
            }

         JsonObject nextInteractionData = SupabaseUtils.getNextInteractionOrder(interaction, interactionOrder);

            if (nextInteractionData != null) {
                messageToChat = nextInteractionData.getJsonString("prompt").getString();
                interaction = nextInteractionData.getJsonNumber("interaction").intValue();
                interactionOrder = nextInteractionData.getJsonNumber("interaction_order").intValue();
            }else {
                return;
            }

            if (!SupabaseUtils.saveUserMessage(workItemId, messageToChat, interaction, interactionOrder)){
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
                return;
            }

            if (!SupabaseUtils.saveAssistantMessage(workItemId, assistantResponse, interaction, interactionOrder)) {
                return;
            }

            if (!isFinal) {
                if (!UtilsService.addCommentToWorkItem(workItemId, assistantResponse)) {
                    return;
                }
                return;
            }

            String iterationPath = "Auditeste";
            String epicUrl = "https://dev.azure.com/InstantSoft/Auditeste/_apis/wit/workItems/" + workItemId;

            List<String> taskPayloads = generateTaskPayloadsFromJson(assistantResponse, iterationPath, epicUrl);

            logger.info("json " + taskPayloads);
            for (String payloadTask : taskPayloads) {
                UtilsService.addTaskToWorkItem("Task", payloadTask);
            }

        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    public List<String> generateTaskPayloadsFromJson(String ollamaJson, String iterationPath, String epicUrl) {
        List<String> payloads = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        try {
            logger.info("entrou no generarteTaskPayloadsFromJson");
            JsonNode rootNode = mapper.readTree(ollamaJson);

            // Normalizar o JSON para lidar com diferentes formatos
            JsonNode tasksNode;
            if (rootNode.isArray()) {
                // Formato simples: lista de objetos
                tasksNode = rootNode;
            } else if (rootNode.has("atividades")) {
                // Formato com chave "atividades"
                tasksNode = rootNode.get("atividades");
            } else {
                logger.error("Formato de JSON não reconhecido");
                return payloads; // Retorna vazio se o formato for inesperado
            }

            for (JsonNode task : rootNode) {
                logger.info("valor task: " + task);
                String title = task.get("titulo").asText();
                String description = task.get("descricao").asText();

                String payload = "["
                        + "{"
                        + "\"op\": \"add\", \"path\": \"/fields/System.Title\", \"value\": \"" + title + "\""
                        + "},"
                        + "{"
                        + "\"op\": \"add\", \"path\": \"/fields/System.Description\", \"value\": \"" + description + "\""
                        + "},"
                        + "{"
                        + "\"op\": \"add\", \"path\": \"/fields/System.IterationPath\", \"value\": \"" + iterationPath + "\""
                        + "},"
                        + "{"
                        + "\"op\": \"add\", \"path\": \"/relations/-\", \"value\": {"
                        + "\"rel\": \"System.LinkTypes.Hierarchy-Reverse\","
                        + "\"url\": \"" + epicUrl + "\","
                        + "\"attributes\": {\"comment\": \"Linkado ao Epic correspondente\"}"
                        + "}}"
                        + "]";

                payloads.add(payload);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        return payloads;
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
