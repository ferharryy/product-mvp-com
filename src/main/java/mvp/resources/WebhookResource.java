import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import mvp.service.SupabaseService;
import mvp.service.WorkItemService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import org.jboss.logging.Logger;

import java.io.StringReader;
import java.net.URI;

@Path("/webhook")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WebhookResource {

    private static final Logger LOG = Logger.getLogger(WebhookResource.class);

    @Inject
    SupabaseService supabaseService;

    @Inject
    WorkItemService workItemService;

    // ---------------------------------------------------------------
    //  ENDPOINT EPIC DO JIRA
    // ---------------------------------------------------------------
    @POST
    @Path("/epic")
    @Operation(
            summary = "Recebe webhook de épico do Jira",
            description = "Processa criação ou atualização de épicos enviados via webhook do Jira."
    )
    @APIResponse(
            responseCode = "202",
            description = "Webhook de épico recebido e processamento iniciado"
    )
    @APIResponse(
            responseCode = "400",
            description = "Payload JSON inválido",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(example = "{ \"error\": \"Invalid JSON\" }")
            )
    )
    public Response handleEpic(
            @RequestBody(
                    required = true,
                    description = "Payload bruto enviado pelo Jira",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = {
                                    @ExampleObject(
                                            name = "Exemplo de payload enviado pelo Jira",
                                            value =
                                                    """
                                                    {
                                                      "issue": {
                                                        "key": "EPIC-123",
                                                        "self": "https://meu-jira.com/rest/api/2/issue/EPIC-123",
                                                        "fields": {
                                                          "summary": "Nome do épico",
                                                          "description": "Descrição completa do épico"
                                                        }
                                                      }
                                                    }
                                                    """
                                    )
                            }
                    )
            )
            String webhookPayload
    ) {

        LOG.info("Recebendo Épico do Jira: " + webhookPayload);

        supabaseService.saveLog(
                "INFO",
                "Recebendo Épico do Jira",
                Json.createObjectBuilder().add("payload_raw", webhookPayload).build()
        );

        // -------------------------------------------------------
        // 1) VALIDAÇÃO DE JSON MALFORMADO
        // -------------------------------------------------------
        JsonObject json;
        try {
            JsonReader reader = Json.createReader(new StringReader(webhookPayload));
            json = reader.readObject();
        } catch (Exception e) {
            LOG.warn("JSON inválido recebido no webhook de épico: " + e.getMessage());

            supabaseService.saveLog(
                    "ERROR",
                    "JSON inválido no webhook de épico",
                    Json.createObjectBuilder().add("error", e.getMessage()).build()
            );

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Json.createObjectBuilder()
                            .add("error", "Invalid JSON")
                            .add("details", e.getMessage())
                            .build())
                    .build();
        }

        // -------------------------------------------------------
        // 2) PROCESSAMENTO ASSÍNCRONO
        // -------------------------------------------------------
        new Thread(() -> processEpic(json)).start();

        return Response.accepted().build();
    }

    // ---------------------------------------------------------------
    //  MÉTODO INTERNO: PROCESSAMENTO DO EPIC
    // ---------------------------------------------------------------
    private void processEpic(JsonObject jsonPayload) {
        String key = "N/A";

        try {

            JsonObject issue = jsonPayload.getJsonObject("issue");
            JsonObject fields = issue.getJsonObject("fields");

            key = issue.getString("key");
            String title = fields.getString("summary", "Sem título");
            String description = fields.getString("description", "Sem descrição");
            String url = issue.getString("self");

            URI uri = new URI(url);
            String baseUrl = uri.getScheme() + "://" + uri.getHost();

            workItemService.processWebhook("", key, title, description, "0", baseUrl);

            supabaseService.saveLog(
                    "INFO",
                    "Épico processado com sucesso",
                    Json.createObjectBuilder()
                            .add("issue_key", key)
                            .add("title", title)
                            .build()
            );

        } catch (Exception e) {
            LOG.error("Erro ao processar épico do Jira (key=" + key + "): " + e.getMessage(), e);

            JsonObjectBuilder err = Json.createObjectBuilder()
                    .add("issue_key", key)
                    .add("error_message", e.getMessage());

            if (e.getCause() != null) {
                err.add("cause", e.getCause().getMessage());
            }

            supabaseService.saveLog(
                    "ERROR",
                    "Erro ao processar épico",
                    err.build()
            );
        }
    }
}
