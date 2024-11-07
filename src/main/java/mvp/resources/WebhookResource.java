import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
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
import mvp.service.SupabaseService;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("/webhook")
public class WebhookResource {

    private static final Logger logger = Logger.getLogger(WebhookResource.class.getName());
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(WebhookResource.class);

    @Inject
    SupabaseService supabaseService;

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

    @POST
    @Path("/comment")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleWebhookComment(String payload) {
        // Responde rapidamente ao Azure DevOps
        Response acceptedResponse = Response.accepted().build();

        // Processa a carga útil em segundo plano
        new Thread(() -> {
            // Lógica para processar o webhook
            processComment(payload);
        }).start();

        return acceptedResponse;
    }

    public Response processComment(String webhookPayload){
        try {
            // Converte a String JSON em JsonObject
            JsonReader jsonReader = Json.createReader(new StringReader(webhookPayload));
            JsonObject jsonObject = jsonReader.readObject();

            // Verifica se é um evento de comentário (isso depende da estrutura do payload do Azure DevOps)
            if (!jsonObject.containsKey("eventType") || !jsonObject.getString("eventType").equals("workitem.commented")) {
                return Response.status(Response.Status.BAD_REQUEST).entity("Event is not a comment").build();
            }

            // Verifique se o comentário é de um usuário específico
            String commenter = jsonObject.getJsonObject("resource").getJsonObject("fields").getString("System.ChangedBy");
            if (!commenter.equals("Fernando <marepositiva@hotmail.com>")) {
                return Response.status(Response.Status.OK).entity("Comment is not from the specific user").build();
            }

            // Obter o comentário enviado no evento
            String comment = jsonObject.getJsonObject("resource").getJsonObject("fields").getString("System.History");
            String cleanComment = removeHtmlTags(comment);

            // Log do comentário
            logger.info("Received comment: " + comment);

            int workItemId = jsonObject.getJsonObject("resource").getInt("id");
            String title = jsonObject.getJsonObject("resource").getJsonObject("fields").getString("System.Title");

            // Cria o JSON para salvar no Supabase
            JsonObject supabasePayload = Json.createObjectBuilder()
                    .add("id_workitem", workItemId)
                    .add("description", cleanComment)
                    .add("title", title)
                    .add("type", "comment")
                    .build();

            // Envia para o Supabase
            Response responseSupabase = supabaseService.saveWorkItem(supabasePayload.toString());
            if(responseSupabase.getStatus() != Response.Status.CREATED.getStatusCode()) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error in Supabase saveWorkItem response").build();
            }


            // Envia o comentário para o Ollama
            OllamaChatResource chatResource = new OllamaChatResource();

            String mensagemParaChat = "Agora sugira um workitem para este caso ";

            JsonObject messageJson = Json.createObjectBuilder()
                    .add("message", mensagemParaChat)
                    .add("created_at", java.time.Instant.now().toString())
                    .add("id_workitem", workItemId)
                    .add("sender", "user")  // Indica que a mensagem veio do Ollama
                    .build();

            responseSupabase = supabaseService.saveMessage(messageJson.toString());
            if(responseSupabase.getStatus() != Response.Status.CREATED.getStatusCode()){
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error in Supabase saveMessage user response").build();
            }
            // Criando o payload para o Ollama
            /*JsonArrayBuilder messagesArrayBuilder = Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                            .add("role", "user")
                            .add("content", "Agora sugira um workitem para este caso "));*/

            // Recupera todas as mensagens associadas ao ID do Work Item
            List<JsonObject> previousMessages = supabaseService.getMessagesByWorkItemId(workItemId);

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

            String chatPayload = chatPayloadObject.toString();

            // Chamando o Ollama para processar o comentário
            Response chatResponse = chatResource.chat(chatPayload);
            if (chatResponse.getStatus() != Response.Status.OK.getStatusCode()) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error in chat response").build();
            }

            // Processa a resposta do Ollama
            String chatResponseBody = chatResponse.getEntity().toString();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode chatJsonNode = mapper.readTree(chatResponseBody);
            String chatContent = chatJsonNode.get("content").asText();

            // Adiciona a resposta como um novo comentário ao work item
            WorkItemResource workItemResource = new WorkItemResource();
            JsonObject jsonComment = Json.createObjectBuilder().add("text", chatContent).build();
            String commentPayload = jsonComment.toString();

            messageJson = Json.createObjectBuilder()
                    .add("message", chatContent)
                    .add("created_at", java.time.Instant.now().toString())
                    .add("id_workitem", workItemId)
                    .add("sender", "assistant")  // Indica que a mensagem veio do Ollama
                    .build();

            // Salva a resposta no Supabase
            responseSupabase = supabaseService.saveMessage(messageJson.toString());

            if(responseSupabase.getStatus() != Response.Status.CREATED.getStatusCode()){
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error in Supabase saveMessage assistent response").build();
            }

            Response commentResponse = workItemResource.addComment(workItemId, commentPayload);

            // Verifica se o comentário foi adicionado com sucesso
            if (commentResponse.getStatus() == Response.Status.OK.getStatusCode()) {
                return Response.ok("Response comment added successfully").build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to add response comment").build();
            }

        } catch (Exception e) {
            // Log do erro
            logger.log(Level.SEVERE, "Error processing comment webhook", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error processing comment webhook").build();
        }
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

            // Extrai os campos do work item
            String title = jsonObject.getJsonObject("resource").getJsonObject("fields").getString("System.Title");
            String type = jsonObject.getJsonObject("resource").getJsonObject("fields").getString("System.WorkItemType");
            int workItemId = jsonObject.getJsonObject("resource").getInt("id");

            // Cria o JSON para salvar no Supabase
            JsonObject supabasePayload = Json.createObjectBuilder()
                    .add("id_workitem", workItemId)
                    .add("description", workItemDescription)
                    .add("title", title)
                    .add("type", type)
                    .build();

            // Envia para o Supabase
            Response responseSupabase = supabaseService.saveWorkItem(supabasePayload.toString());
            if(responseSupabase.getStatus() != Response.Status.CREATED.getStatusCode()){
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error in Supabase saveWorkItem response").build();
            }


            // Instância de OllamaChatResource
            OllamaChatResource chatResource = new OllamaChatResource();
            String mensagemParaChat = "como product owner aponte falhas e problemas de análise do seguinte workitem " + workItemDescription;

            // Criando o payload para enviar ao OllamaChatResource
            JsonArrayBuilder messagesArrayBuilder = Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                            .add("role", "user")
                            .add("content", mensagemParaChat)
                    );

            JsonObject messageJson = Json.createObjectBuilder()
                    .add("message", mensagemParaChat)
                    .add("created_at", java.time.Instant.now().toString())
                    .add("id_workitem", workItemId)
                    .add("sender", "user")  // Indica que a mensagem veio do Ollama
                    .build();

            responseSupabase = supabaseService.saveMessage(messageJson.toString());

            if(responseSupabase.getStatus() != Response.Status.CREATED.getStatusCode()){
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error in Supabase saveMessage user response").build();
            }

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
            String commentPayload = removeHtmlTags(jsonComment.toString());

            // Log da resposta do chat
            logger.info("Chat response: " + chatContent);

            messageJson = Json.createObjectBuilder()
                    .add("message", chatContent)
                    .add("created_at", java.time.Instant.now().toString())
                    .add("id_workitem", workItemId)
                    .add("sender", "assistant")  // Indica que a mensagem veio do Ollama
                    .build();

            // Salva a resposta no Supabase
            responseSupabase = supabaseService.saveMessage(messageJson.toString());

            if(responseSupabase.getStatus() != Response.Status.CREATED.getStatusCode()){
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error in Supabase saveMessage assistent response").build();
            }

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
