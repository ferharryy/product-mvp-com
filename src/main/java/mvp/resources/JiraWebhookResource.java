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
import jakarta.json.JsonObjectBuilder; // Importar para JsonObjectBuilder
import mvp.service.CommentAcceptService;
import mvp.service.RejectionService;
import mvp.service.WorkItemService;
import mvp.service.SupabaseService; // Importar SupabaseService
import org.jboss.logging.Logger;

import java.io.StringReader;
import java.net.URI;

@Path("/jira")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class JiraWebhookResource {

    private static final Logger LOG = Logger.getLogger(JiraWebhookResource.class);

    @Inject
    RejectionService rejectionService;

    @Inject
    CommentAcceptService commentAcceptService;

    @Inject
    WorkItemService workItemService;

    @Inject
    SupabaseService supabaseService; // Injetar SupabaseService

    @POST
    @Path("/comment")
    public Response handleComment(String payload) {
        LOG.info("Recebendo Comentário do Jira: " + payload);
        supabaseService.saveLog("INFO", "Recebendo Comentário do Jira", Json.createObjectBuilder().add("payload_raw", payload).build());

        new Thread(() -> {
            String key = "N/A"; // Valor padrão para logs em caso de erro antes de extrair a chave
            try {
                JsonReader jsonReader = Json.createReader(new StringReader(payload));
                JsonObject json = jsonReader.readObject();

                // Verifica se é um evento de comentário
                if ("comment_created".equals(json.getString("webhookEvent"))) {
                    JsonObject comment = json.getJsonObject("comment");
                    JsonObject issue = json.getJsonObject("issue");
                    String body = comment.getString("body");
                    key = issue.getString("key"); //TES-49
                    String url = issue.getString("self");
                    URI uri = new URI(url);
                    String baseUrl = uri.getScheme() + "://" + uri.getHost();

                    // Filtra se o comentário contém "aceito" (case insensitive)
                    if (body.toLowerCase().contains("aceito") || body.contains("comment IA")) {
                        commentAcceptService.processComment(key, body, "0", baseUrl);
                        supabaseService.saveLog("INFO", "Comentário do Jira processado com sucesso",
                                Json.createObjectBuilder()
                                        .add("issue_key", key)
                                        .add("comment_body", body)
                                        .add("event", "comment_accepted")
                                        .build());
                    } else {
                        supabaseService.saveLog("INFO", "Comentário do Jira recebido, mas não elegível para processamento",
                                Json.createObjectBuilder()
                                        .add("issue_key", key)
                                        .add("comment_body", body)
                                        .add("event", "comment_ignored")
                                        .build());
                    }
                } else {
                    supabaseService.saveLog("WARN", "Evento de webhook do Jira não é 'comment_created'",
                            Json.createObjectBuilder()
                                    .add("webhook_event", json.getString("webhookEvent", "N/A"))
                                    .add("payload_summary", payload.substring(0, Math.min(payload.length(), 200)) + "...") // Limita o payload para não sobrecarregar
                                    .build());
                }
            } catch (Exception e) {
                LOG.error("Erro ao processar comentário do Jira para key: " + key + " - " + e.getMessage(), e);
                JsonObjectBuilder errorContextBuilder = Json.createObjectBuilder()
                        .add("issue_key", key)
                        .add("error_message", e.getMessage());
                if (e.getCause() != null) {
                    errorContextBuilder.add("cause", e.getCause().getMessage());
                }
                supabaseService.saveLog("ERROR", "Erro ao processar comentário do Jira", errorContextBuilder.build());
            }
        }).start();

        return Response.accepted().build();
    }

    @POST
    @Path("/epic")
    public Response handleEpic(String webhookPayload)  {
        LOG.info("Recebendo Epico do Jira: " + webhookPayload);
        supabaseService.saveLog("INFO", "Recebendo Épico do Jira", Json.createObjectBuilder().add("payload_raw", webhookPayload).build());

        new Thread(() -> {
            String key = "N/A"; // Valor padrão para logs em caso de erro antes de extrair a chave
            try {
                JsonReader jsonReader = Json.createReader(new StringReader(webhookPayload));
                JsonObject json = jsonReader.readObject();
                JsonObject issue = json.getJsonObject("issue");
                JsonObject fields = issue.getJsonObject("fields");
                key = issue.getString("key");
                String title = fields.getString("summary");
                String description = fields.getString("description");
                String url = issue.getString("self");
                URI uri = new URI(url);
                String baseUrl = uri.getScheme() + "://" + uri.getHost();

                workItemService.processWebhook(key, title, description, "0", baseUrl);
                supabaseService.saveLog("INFO", "Épico do Jira processado com sucesso",
                        Json.createObjectBuilder()
                                .add("issue_key", key)
                                .add("title", title)
                                .build());
            } catch (Exception e) {
                LOG.error("Erro ao processar épico do Jira para key: " + key + " - " + e.getMessage(), e);
                JsonObjectBuilder errorContextBuilder = Json.createObjectBuilder()
                        .add("issue_key", key)
                        .add("error_message", e.getMessage());
                if (e.getCause() != null) {
                    errorContextBuilder.add("cause", e.getCause().getMessage());
                }
                supabaseService.saveLog("ERROR", "Erro ao processar épico do Jira", errorContextBuilder.build());
            }
        }).start();

        return Response.accepted().build();
    }
}
