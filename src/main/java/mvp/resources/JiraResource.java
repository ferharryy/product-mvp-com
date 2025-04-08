package mvp.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Path("/jira")
public class JiraResource {

    //private static final String JIRA_API_URL = "https://instantsofttechsolution.atlassian.net/rest/api/3/issue";
    //private static final String USER_EMAIL = "marepositiva@hotmail.com";
    //private static final String API_TOKEN = "ATATT3xFfGF0zBL5yTMGGfdrx9QC05GzorFV5aNiuUMTbgIGginjsD8S_nDEljcuoenlh7PF3l1v1ffeRI4mJFWmCX3gU0BjUEZDVhcWbNsje8aM3pXPzYdghAWPZiY_MK5gWKMbXFUaVeQwDsb6UKuuJFJBYJySK7WaNUQw0MLiB85CnXTCu68=6CB78884";


    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createIssue(String payload, String url, String user_email, String pat_token) {

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost( url + "/rest/api/3/issue/");
            String auth = user_email + ":" + pat_token;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + encodedAuth;
            request.setHeader("Authorization", authHeader);
            request.setHeader("Content-Type", "application/json");

            // Configura o payload JSON
            StringEntity entity = new StringEntity(payload, StandardCharsets.UTF_8);
            request.setEntity(entity);

            try (CloseableHttpResponse response = client.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 200) {
                    String json = EntityUtils.toString(response.getEntity());

                    // Use Jackson to parse JSON
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readTree(json);

                    return Response.ok(jsonNode.toString()).build();
                } else {
                    return Response.status(statusCode).entity("Failed to add comment").build();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error occurred").build();
        }
    }


    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addComment(String requestBody, String url, String pat_token, String user_email) {
        JsonReader jsonReader = Json.createReader(new StringReader(requestBody));
        JsonObject jsonObject = jsonReader.readObject();

        String issueKey = jsonObject.getString("key");
        String commentText = "comment IA " + jsonObject.getString("comment").replaceAll("\n", " ");

        // Cria o JSON no formato de documento Atlassian para o comentário
        String jiraCommentPayload = "{\n" +
                "  \"body\": {\n" +
                "    \"type\": \"doc\",\n" +
                "    \"version\": 1,\n" +
                "    \"content\": [\n" +
                "      {\n" +
                "        \"type\": \"paragraph\",\n" +
                "        \"content\": [\n" +
                "          {\n" +
                "            \"type\": \"text\",\n" +
                "            \"text\": \"" + commentText + "\"\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";

        // Constrói a URL para adicionar comentário à issue
        String jiraUrl = url + "/rest/api/3/issue/" + issueKey + "/comment";

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(jiraUrl);

            // Gera o cabeçalho de autenticação corretamente
            String auth = user_email + ":" + pat_token;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + encodedAuth;

            request.setHeader("Authorization", authHeader);
            request.setHeader("Content-Type", "application/json");

            // Configura o payload JSON
            StringEntity entity = new StringEntity(jiraCommentPayload, StandardCharsets.UTF_8);
            request.setEntity(entity);

            try (CloseableHttpResponse response = client.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (statusCode == 201) { // 201 Created indica sucesso
                    return Response.status(Response.Status.CREATED).entity(responseBody).build();
                } else {
                    return Response.status(statusCode).entity("Failed to add comment: " + responseBody).build();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error occurred: " + e.getMessage()).build();
        }
    }

}
