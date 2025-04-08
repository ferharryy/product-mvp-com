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
import mvp.service.CommentAcceptService;
import mvp.service.RejectionService;
import mvp.service.WorkItemService;
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

    @POST
    @Path("/comment")
    public Response handleComment(String payload) {
        LOG.info("Recebendo Comentário do Jira: " + payload);

        new Thread(() -> {
            try {
                JsonReader jsonReader = Json.createReader(new StringReader(payload));
                JsonObject json = jsonReader.readObject();

                // Verifica se é um evento de comentário
                if ("comment_created".equals(json.getString("webhookEvent"))) {
                    JsonObject comment = json.getJsonObject("comment");
                    JsonObject issue = json.getJsonObject("issue");
                    String body = comment.getString("body");
                    String key = issue.getString("key"); //TES-49
                    String url = issue.getString("self");
                    URI uri = new URI(url);
                    String baseUrl = uri.getScheme() + "://" + uri.getHost();

                    // Filtra se o comentário contém "aceito" (case insensitive)
                    if (body.toLowerCase().contains("aceito") || body.contains("comment IA")) {
                        commentAcceptService.processComment(key, body, "0", baseUrl);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return Response.accepted().build();
    }

    @POST
    @Path("/epic")
    public Response handleEpic(String webhookPayload)  {
        LOG.info("Recebendo Epico do Jira: " + webhookPayload);

        new Thread(() -> {
            try {
                JsonReader jsonReader = Json.createReader(new StringReader(webhookPayload));
                JsonObject json = jsonReader.readObject();
                JsonObject issue = json.getJsonObject("issue");
                JsonObject fields = issue.getJsonObject("fields");
                String key = issue.getString("key");
                String title = fields.getString("summary");
                String description = fields.getString("description");
                String url = issue.getString("self");
                URI uri = new URI(url);
                String baseUrl = uri.getScheme() + "://" + uri.getHost();

                workItemService.processWebhook(key, title, description, "0", baseUrl);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return Response.accepted().build();
    }
}
