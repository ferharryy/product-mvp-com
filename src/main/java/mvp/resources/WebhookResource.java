import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonArrayBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import mvp.resources.OllamaChatResource;
import mvp.resources.WorkItemResource;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("/webhook")
public class WebhookResource {

    private static final Logger logger = Logger.getLogger(WebhookResource.class.getName());
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(WebhookResource.class);

    @POST
    @Path("/workitem")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleWebhook(String webhookPayload) {

        // Responde rapidamente ao Azure DevOps
        Response acceptedResponse = Response.accepted().build();

        // Processa a carga útil em segundo plano
        new Thread(() -> {
            // Lógica para processar o webhook
            processWebhook(webhookPayload);
        }).start();

        return acceptedResponse;
    }

    public Response processWebhook(String webhookPayload){
        try {
            // Converte a String JSON em JsonObject
            JsonReader jsonReader = Json.createReader(new StringReader(webhookPayload));
            JsonObject jsonObject = jsonReader.readObject();

            // Validação para verificar se os campos necessários estão presentes
            if (!jsonObject.containsKey("resource") ||
                    !jsonObject.getJsonObject("resource").containsKey("fields") ||
                    !jsonObject.getJsonObject("resource").getJsonObject("fields").containsKey("System.Description")) {
                return Response.status(Response.Status.BAD_REQUEST).entity("Invalid payload structure").build();
            }

            // Extrair a descrição do work item
            String workItemDescription = jsonObject
                    .getJsonObject("resource")
                    .getJsonObject("fields")
                    .getString("System.Description");
            workItemDescription = removeHtmlTags(workItemDescription);

            // Log da descrição do Work Item
            logger.info("Work item description: " + workItemDescription);

            // Instância de OllamaChatResource
            OllamaChatResource chatResource = new OllamaChatResource();

            // Criando o payload para enviar ao OllamaChatResource
            JsonArrayBuilder messagesArrayBuilder = Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                            .add("role", "user")
                            .add("content", "como analista de qualidade crie os cenários de testes para a seguinte estoria usando cucumber " + workItemDescription)  // Adiciona a descrição do work item
                    );

            JsonObject chatPayloadObject = Json.createObjectBuilder()
                    .add("model", "codellama")  // Define o modelo como "codellama"
                    .add("messages", messagesArrayBuilder)
                    .build();

            // Convertendo para string o payload
            String chatPayload = chatPayloadObject.toString();

            // Chamando o método chat e obtendo a resposta
            Response chatResponse = chatResource.chat(chatPayload);
            if (chatResponse.getStatus() != Response.Status.OK.getStatusCode()) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error in chat response").build();
            }

            // Obtendo a resposta do Ollama
            String chatResponseBody = chatResponse.getEntity().toString();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode chatJsonNode = mapper.readTree(chatResponseBody);
            String chatContent = chatJsonNode.get("content").asText();
            JsonObject jsonComment = Json.createObjectBuilder().add("text", chatContent).build();
            String commentPayload = jsonComment.toString();

            // Log da resposta do chat
            logger.info("Chat response: " + chatContent);

            // Instância de WorkItemResource para adicionar comentário
            WorkItemResource workItemResource = new WorkItemResource();

            // Chamando o método addComment para adicionar a resposta do chat como comentário
            Response commentResponse = workItemResource.addComment(
                    jsonObject.getJsonObject("resource").getInt("id"),
                    commentPayload
            );

            // Verifica se o comentário foi adicionado com sucesso
            if (commentResponse.getStatus() == Response.Status.OK.getStatusCode()) {
                return Response.ok("Comment added successfully").build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to add comment").build();
            }

        } catch (Exception e) {
            // Log do erro
            logger.log(Level.SEVERE, "Error processing webhook", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error processing webhook").build();
        }
    }

    // Método para remover tags HTML
    private String removeHtmlTags(String html) {
        // Usando expressão regular para remover tags HTML
        Pattern pattern = Pattern.compile("<.*?>");
        Matcher matcher = pattern.matcher(html);
        return matcher.replaceAll("").trim();
    }
}
