package mvp.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.CloseableHttpClient;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response.Status;

@Path("/jira")
public class JiraIntegrationResource {

    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createJiraIssue(String webhookPayload, String url, String token_pat, String email) {
        try{
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(webhookPayload);
        String projectKey = rootNode.get("projectKey").asText();
        String summary = rootNode.get("summary").asText();
        String description = rootNode.get("description").asText();

            JsonObject issue = Json.createObjectBuilder()
                    .add("fields", Json.createObjectBuilder()
                            .add("project", Json.createObjectBuilder()
                                    .add("key", projectKey)
                            )
                            .add("summary", summary)
                            .add("description", Json.createObjectBuilder()
                                    .add("type", "doc")
                                    .add("version", 1)
                                    .add("content", Json.createArrayBuilder()
                                            .add(Json.createObjectBuilder()
                                                    .add("type", "paragraph")
                                                    .add("content", Json.createArrayBuilder()
                                                            .add(Json.createObjectBuilder()
                                                                    .add("type", "text")
                                                                    .add("text", description)
                                                            )
                                                    )
                                            )
                                    )
                            )
                            .add("issuetype", Json.createObjectBuilder()
                                    .add("name", "Task")
                            )
                    )
                    .build();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Client client = ClientBuilder.newClient();
            Response response = client.target(url)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Basic " + getAuthHeader(email, token_pat))
                    .post(Entity.json(issue));

            if (response.getStatus() == Status.CREATED.getStatusCode()) {
                return Response.status(Status.CREATED).entity("Atividade criada com sucesso!").build();
            } else {
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Erro ao criar atividade: " + response.getStatus()).build();
            }
        }
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Erro ao se comunicar com o Jira: " + e.getMessage()).build();
        }
    }

    private String getAuthHeader(String email, String token_pat) {
        String auth = email + ":" + token_pat;
        return java.util.Base64.getEncoder().encodeToString(auth.getBytes());
    }

    // Classe para mapear os dados do corpo da requisição
    public static class IssueRequest {
        private String projectKey;
        private String summary;
        private String description;

        // Getters e setters
        public String getProjectKey() {
            return projectKey;
        }

        public void setProjectKey(String projectKey) {
            this.projectKey = projectKey;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
