import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import mvp.service.CommentAcceptService;
import mvp.service.RejectionService;
import mvp.service.WorkItemService;
import org.slf4j.LoggerFactory;

import java.util.logging.Logger;

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
            // Lógica para processar o webhook
            workItemService.processWebhook(webhookPayload);
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
            commentAcceptService.processComment(payload);
        }).start();

        return acceptedResponse;
    }

    @POST
    @Path("/recuso")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleWebhookRecuso(String payload) {
        // Responde rapidamente ao Azure DevOps
        Response acceptedResponse = Response.accepted().build();

        // Processa a carga útil em segundo plano
        new Thread(() -> {
            // Lógica para processar o webhook
            rejectionService.handleRejection(payload);
        }).start();

        return acceptedResponse;
    }
}
