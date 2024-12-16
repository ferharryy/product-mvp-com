package mvp.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.core.Response;
import mvp.utils.OllamaUtils;
import mvp.utils.SupabaseUtils;
import mvp.utils.UtilsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                //return Response.status(Response.Status.BAD_REQUEST).entity("Invalid payload").build();
                return;
            }

            int workItemId = getJsonValue(payload, "resource.id", Integer.class);
            String[] keysTitle = {"resource", "fields", "System.Title"};
            String title =  getNestedJsonValue(payload, keysTitle, String.class);
            String[] keysComment = {"resource", "fields", "System.History"};
            String comment = getNestedJsonValue(payload, keysComment, String.class);

            if (workItemId == -1 || title == null || comment == null) {
                //return Response.status(Response.Status.BAD_REQUEST).entity("Missing fields in payload").build();
                return;
            }

            String cleanComment = UtilsService.removeHtmlTags(comment);
            JsonObject finalMessage = SupabaseUtils.hasFinalAssistantMessage(workItemId);
            int interaction = 0;
            int interactionOrder = 0;
            String messageToChat = "";
            //SupabaseUtils.handleInteraction(finalMessage, comment, interaction, interactionOrder);

            if (comment.toLowerCase().contains("aceito")) {
                int interactionAux = finalMessage != null ? finalMessage.getJsonNumber("interaction").intValue() : 0;
                JsonObject nextInteraction = SupabaseUtils.getNextInteraction(interactionAux);
                //return nextInteraction != null ? nextInteraction.getJsonNumber("interaction").intValue() : 0;
                interaction = nextInteraction != null ? nextInteraction.getJsonNumber("interaction").intValue() : 0;
            }
            else {
                interaction = finalMessage != null ? finalMessage.getJsonNumber("interaction").intValue() : 0;
                interactionOrder = finalMessage != null ? finalMessage.getJsonNumber("interaction_order").intValue() : 0;
            }

 /*           JsonObject nextInteractionOrder = SupabaseUtils.getNextInteractionOrder(interaction, interactionOrder);
            if (nextInteractionOrder == null){
                return;
            }
            else{
                interactionOrder = nextInteractionOrder.getJsonNumber("interaction_order").intValue();
            }

            if (!SupabaseUtils.saveUserMessage(workItemId, cleanComment, interaction, interactionOrder)){
                return;
            }

*/            JsonObject nextInteractionData = SupabaseUtils.getNextInteractionOrder(interaction, interactionOrder);

            if (nextInteractionData != null) {
                messageToChat = nextInteractionData.getJsonString("prompt").getString();
                interaction = nextInteractionData.getJsonNumber("interaction").intValue();
                interactionOrder = nextInteractionData.getJsonNumber("interaction_order").intValue();
            }else {
                return;
            }

            /*if (!SupabaseUtils.saveWorkItem(workItemId, title, cleanComment)) {
                //return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to save work item").build();
                return;
            }*/

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

            //return Response.ok("Process completed successfully").build();
        } catch (Exception e) {
            //return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Internal server error").build();
            logger.error(e.getMessage());
        }
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


    private <T> T getJsonValueDirect(JsonObject json, String key, Class<T> valueType) {
        try {
            // Verifica se a chave existe
            if (!json.containsKey(key)) {
                throw new IllegalArgumentException("Invalid key: " + key);
            }

            // Retorna o valor baseado no tipo esperado
            if (valueType == String.class) {
                return valueType.cast(json.getString(key, null));
            } else if (valueType == Integer.class) {
                return valueType.cast(json.getInt(key, -1));
            } else if (valueType == Double.class) {
                return valueType.cast(json.getJsonNumber(key).doubleValue());
            } else if (valueType == Boolean.class) {
                return valueType.cast(json.getBoolean(key, false));
            } else if (valueType == JsonObject.class) {
                return valueType.cast(json.getJsonObject(key));
            } else {
                throw new IllegalArgumentException("Unsupported value type: " + valueType.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Retorna null caso não encontre o valor
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
