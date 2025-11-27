package mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import mvp.utils.OllamaUtils;
import mvp.utils.SupabaseUtils;
import mvp.utils.UtilsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class CommentAcceptService {

    private static final Logger logger = LoggerFactory.getLogger(CommentAcceptService.class);

    @Inject
    OllamaUtils ollamaUtils;

    public void processComment(String workItemId, String project, String comment, String plataform, String url) {
        try {
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

            String key = project.split("-")[0];
            Long companyId = SupabaseUtils.getCompanyByURL(url);
            Long projectId = SupabaseUtils.getProjectByCompanyIdAndKey(companyId, key);

            if (!SupabaseUtils.saveUserMessage(workItemId, messageToChat, interaction, interactionOrder, companyId, projectId )){
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

                if (plataform.equals("0")){

                    UtilsService.addCommentToJira(workItemId,assistantResponse, url);
                } else if (plataform.equals("1")) {
                    UtilsService.addCommentToWorkItem(workItemId, assistantResponse);
                }
                return;
            }


            if (plataform.equals("0")) {
                String iterantionPath = workItemId.split("-")[0];

                CompletableFuture.supplyAsync(() ->
                        generateTaskPayloadsFromJsonToJira(assistantResponse, iterantionPath)
                ).thenAccept(taskPaloads -> {
                    taskPaloads.forEach(payloadTask -> UtilsService.addTaskToJira(payloadTask, iterantionPath, url));
            }).exceptionally(ex -> {
                logger.error("Erro ao processar JSON", ex);
                return null;
                });
            }else {
            String iterationPath = "Auditeste";
            String epicUrl = "https://dev.azure.com/InstantSoft/Auditeste/_apis/wit/workItems/" + workItemId;

            CompletableFuture.supplyAsync(() ->
                    generateTaskPayloadsFromJson(assistantResponse, iterationPath, epicUrl)
            ).thenAccept(taskPayloads -> {
                logger.info("JSON processado: " + taskPayloads);
                taskPayloads.forEach(payloadTask -> UtilsService.addTaskToWorkItem("Task", payloadTask));
            }).exceptionally(ex -> {
                logger.error("Erro ao processar JSON", ex);
                return null;
            });
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
                rootNode = rootNode;
            } else if (rootNode.has("atividades")) {
                // Formato com chave "atividades"
                rootNode = rootNode.get("atividades");
            } else if (rootNode.has("Atividades")) {
                // Formato com chave "atividades"
                rootNode = rootNode.get("Atividades");
            } else if (rootNode.has("activities")) {
                // Formato com chave "activities"
                rootNode = rootNode.get("activities");
            } else if (rootNode.has("Activities")) {
                // Formato com chave "activities"
                rootNode = rootNode.get("Activities");
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

    private List<String> generateTaskPayloadsFromJsonToJira(String ollamaJson, String iterationPath) {
        List<String> payloads = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        try {
            logger.info("entrou no generateTaskPayloadsFromJsonToJira");
            JsonNode rootNode = mapper.readTree(ollamaJson);

            // Normalizar o JSON para lidar com diferentes formatos
            JsonNode tasksNode;
            if (rootNode.isArray()) {
                // Formato simples: lista de objetos
                rootNode = rootNode;
            } else if (rootNode.has("atividades")) {
                // Formato com chave "atividades"
                rootNode = rootNode.get("atividades");
            } else if (rootNode.has("Atividades")) {
                // Formato com chave "atividades"
                rootNode = rootNode.get("Atividades");
            } else if (rootNode.has("activities")) {
                // Formato com chave "activities"
                rootNode = rootNode.get("activities");
            } else if (rootNode.has("Activities")) {
                // Formato com chave "activities"
                rootNode = rootNode.get("Activities");
            } else {
                logger.error("Formato de JSON não reconhecido");
                return payloads; // Retorna vazio se o formato for inesperado
            }

            for (JsonNode task : rootNode) {
                logger.info("valor task: " + task);
                String title = task.get("titulo").asText();
                String description = task.get("descricao").asText();

                String payload = "{\n" +
                        "  \"fields\": {\n" +
                        "    \"project\": {\n" +
                        "      \"key\": \"" + iterationPath + "\"\n" +
                        "    },\n" +
                        "    \"summary\": \"" + title + "\",\n" +
                        "    \"description\": {\n" +
                        "      \"type\": \"doc\",\n" +
                        "      \"version\": 1,\n" +
                        "      \"content\": [{\n" +
                        "        \"type\": \"paragraph\",\n" +
                        "        \"content\": [{\n" +
                        "          \"type\": \"text\",\n" +
                        "          \"text\": \"" + description +"\"\n" +
                        "        }]\n" +
                        "      }]\n" +
                        "    },\n" +
                        "    \"issuetype\": {\n" +
                        "      \"name\": \"Task\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "}";

                payloads.add(payload);
            }

        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        return payloads;
    }
}