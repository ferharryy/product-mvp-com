package mvp.resources;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import mvp.exception.WebhookException;
import mvp.service.CommentAcceptService;
import mvp.service.RejectionService;
import mvp.service.WorkItemService;
import mvp.service.SupabaseService;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.Content;

import java.io.StringReader;
import java.net.URI;

@Path("/jira")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Jira Webhooks", description = "Recebe eventos do Jira e processa comentários e épicos")
public class JiraWebhookResource {

    private static final Logger LOG = Logger.getLogger(JiraWebhookResource.class);

    @Inject
    RejectionService rejectionService;

    @Inject
    CommentAcceptService commentAcceptService;

    @Inject
    WorkItemService workItemService;

    @Inject
    SupabaseService supabaseService;


    // =====================================================================
    //  POST /jira/comment
    // =====================================================================

    @POST
    @Path("/comment")
    @Operation(
            summary = "Recebe webhook de comentário do Jira",
            description = "Processa eventos de criação de comentários vindos do Jira."
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            name = "Exemplo de request",
                            value = "{\n" +
                                    "  \"webhookEvent\": \"comment_created\",\n" +
                                    "  \"comment\": {\n" +
                                    "    \"body\": \"aceito\",\n" +
                                    "    \"author\": {\n" +
                                    "      \"displayName\": \"João Silva\"\n" +
                                    "    }\n" +
                                    "  },\n" +
                                    "  \"issue\": {\n" +
                                    "    \"key\": \"TES-49\",\n" +
                                    "    \"self\": \"https://meu-jira.com/rest/api/3/issue/TES-49\"\n" +
                                    "  }\n" +
                                    "}"
                    )
            )
    )
    @APIResponse(
            responseCode = "202",
            description = "Webhook recebido",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            name = "Exemplo de resposta 202",
                            value = "{\n" +
                                    "  \"status\": \"accepted\",\n" +
                                    "  \"message\": \"Processamento iniciado com sucesso\"\n" +
                                    "}"
                    )
            )
    )
    @APIResponse(
            responseCode = "400",
            description = "Payload inválido",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            name = "Erro 400",
                            value = "{\n" +
                                    "  \"error\": \"Payload inválido\",\n" +
                                    "  \"detail\": \"Campo 'comment' é obrigatório\"\n" +
                                    "}"
                    )
            )
    )
    public Response handleComment(
            @Schema(description = "Payload bruto enviado pelo Jira")
            String payload
    ) {
        LOG.info("Recebendo Comentário do Jira: " + payload);

        // Salva o log do payload recebido
        supabaseService.saveLog("INFO", "Recebendo Comentário do Jira",
                Json.createObjectBuilder().add("payload_raw", payload).build()
        );

        // --------------- 🔥 VALIDAÇÃO ANTES DA THREAD ---------------
        JsonObject json;
        try {
            json = Json.createReader(new StringReader(payload)).readObject();
        } catch (Exception e) {
            throw new WebhookException("JSON malformado");
        }

        if (!json.containsKey("webhookEvent")) {
            throw new WebhookException("Campo 'webhookEvent' é obrigatório");
        }

        if (!"comment_created".equalsIgnoreCase(json.getString("webhookEvent"))) {
            throw new WebhookException("webhookEvent deve ser 'comment_created'");
        }

        if (!json.containsKey("comment")) {
            throw new WebhookException("Campo 'comment' é obrigatório");
        }

        if (!json.containsKey("issue")) {
            throw new WebhookException("Campo 'issue' é obrigatório");
        }

        // --------------- 🔄 PROCESSAMENTO ASSÍNCRONO ---------------
        new Thread(() -> {
            String key = "N/A";
            try {
                JsonObject comment = json.getJsonObject("comment");
                JsonObject issue = json.getJsonObject("issue");

                String body = comment.getString("body", "");
                key = issue.getString("key", "N/A");
                String url = issue.getString("self", "");

                URI uri = new URI(url);
                String baseUrl = uri.getScheme() + "://" + uri.getHost();

                if (body.toLowerCase().contains("aceito") || body.contains("comment IA")) {
                    commentAcceptService.processComment("", key, body, "0", baseUrl);
                    supabaseService.saveLog("INFO", "Comentário do Jira processado",
                            Json.createObjectBuilder().add("issue_key", key).build());
                } else {
                    supabaseService.saveLog("INFO", "Comentário ignorado",
                            Json.createObjectBuilder().add("issue_key", key).build());
                }
            } catch (Exception e) {
                LOG.error("Erro ao processar comentário para key " + key, e);
                supabaseService.saveLog("ERROR", "Erro ao processar comentário",
                        Json.createObjectBuilder()
                                .add("issue_key", key)
                                .add("error_message", e.getMessage())
                                .build());
            }
        }).start();

        // --------------- ✔ RETORNO SEM BODY (202) ---------------
        return Response.accepted().build();
    }


    // =====================================================================
    //  POST /jira/epic
    // =====================================================================

    @POST
    @Path("/epic")
    @Operation(
            summary = "Recebe webhook de épico do Jira",
            description = "Processa criação ou atualização de épicos enviados via webhook do Jira."
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            name = "Exemplo de request",
                            value = "{\n" +
                                    "  \"issue\": {\n" +
                                    "    \"key\": \"TES-10\",\n" +
                                    "    \"self\": \"https://meu-jira.com/rest/api/3/issue/TES-10\",\n" +
                                    "    \"fields\": {\n" +
                                    "      \"summary\": \"Criar funcionalidade X\",\n" +
                                    "      \"description\": \"Descrição detalhada do épico\"\n" +
                                    "    }\n" +
                                    "  }\n" +
                                    "}"
                    )
            )
    )
    @APIResponse(
            responseCode = "202",
            description = "Webhook de épico recebido",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            name = "Exemplo 202",
                            value = "{\n" +
                                    "  \"status\": \"accepted\",\n" +
                                    "  \"message\": \"Processamento do épico iniciado\"\n" +
                                    "}"
                    )
            )
    )
    @APIResponse(
            responseCode = "400",
            description = "Payload inválido",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            name = "Erro 400",
                            value = "{\n" +
                                    "  \"error\": \"Payload inválido\",\n" +
                                    "  \"detail\": \"Campo 'issue' é obrigatório\"\n" +
                                    "}"
                    )
            )
    )
    public Response handleEpic(
            @Schema(description = "Payload bruto enviado pelo Jira")
            String webhookPayload
    ) {
        LOG.info("Recebendo Épico do Jira: " + webhookPayload);
        supabaseService.saveLog("INFO", "Recebendo Épico do Jira",
                Json.createObjectBuilder().add("payload_raw", webhookPayload).build()
        );

        // ----------- 🔥 VALIDAÇÃO INICIAL DO PAYLOAD -----------
        JsonObject json;
        try {
            json = Json.createReader(new StringReader(webhookPayload)).readObject();
        } catch (Exception e) {
            throw new WebhookException("JSON malformado");
        }

        if (!json.containsKey("issue")) {
            throw new WebhookException("Campo 'issue' é obrigatório");
        }

        JsonObject issue = json.getJsonObject("issue");

        if (!issue.containsKey("fields")) {
            throw new WebhookException("Campo 'fields' é obrigatório");
        }

        if (!issue.containsKey("key")) {
            throw new WebhookException("Campo 'key' é obrigatório");
        }

        JsonObject fields = issue.getJsonObject("fields");

        if (!fields.containsKey("summary")) {
            throw new WebhookException("Campo 'summary' é obrigatório");
        }

        if (!fields.containsKey("description")) {
            throw new WebhookException("Campo 'description' é obrigatório");
        }

        // ----------- 🔄 PROCESSAMENTO ASSÍNCRONO -----------
        new Thread(() -> {
            String key = "N/A";

            try {
                key = issue.getString("key");
                String title = fields.getString("summary");
                String description = fields.getString("description");
                String url = issue.getString("self");

                URI uri = new URI(url);
                String baseUrl = uri.getScheme() + "://" + uri.getHost();

                workItemService.processWebhook("", key, title, description, "0", baseUrl);

                supabaseService.saveLog("INFO", "Épico do Jira processado com sucesso",
                        Json.createObjectBuilder()
                                .add("issue_key", key)
                                .add("title", title)
                                .build());
            } catch (Exception e) {
                LOG.error("Erro ao processar épico do Jira para key " + key, e);
                supabaseService.saveLog("ERROR", "Erro ao processar épico do Jira",
                        Json.createObjectBuilder()
                                .add("issue_key", key)
                                .add("error_message", e.getMessage())
                                .build());
            }
        }).start();

        // ----------- ✔ RETORNO 202 SEM BODY -----------
        return Response.accepted().build();
    }

}
