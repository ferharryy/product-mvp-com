package mvp.resources;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.json.*;
import jakarta.ws.rs.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("/workitems")
public class WorkItemResource {

    private static final String ORGANIZATION = "InstantSoft";
    private static final String PROJECT = "Auditeste";
    private static final String PAT = System.getenv("AZURE_DEVOPS_PAT");

    private static final String BASE_URL = "https://dev.azure.com/" + ORGANIZATION + "/" + PROJECT + "/_apis/wit/workitems/";
    String comment = "*** comment Test *** ";

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWorkItem(@PathParam("id") int id) {
        String url = BASE_URL + id + "?api-version=6.0";

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((":".concat(PAT)).getBytes()));

            try (CloseableHttpResponse response = client.execute(request)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    String json = EntityUtils.toString(response.getEntity());

                    // Use Jackson to parse JSON
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readTree(json);

                    // Extraindo a descrição e removendo as tags HTML
                    String description = jsonNode.path("fields").path("System.Description").asText();
                    String cleanDescription = removeHtmlTags(description);

                    // Atualizando o JSON com a descrição limpa
                    ((ObjectNode) jsonNode.get("fields")).put("System.Description", cleanDescription);

                    return Response.ok(jsonNode.toString()).build();
                } else {
                    return Response.status(response.getStatusLine().getStatusCode()).entity("Failed to fetch work item").build();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error occurred").build();
        }
    }

    // Método para remover tags HTML
    private String removeHtmlTags(String html) {
        // Usando expressão regular para remover tags HTML
        Pattern pattern = Pattern.compile("<.*?>");
        Matcher matcher = pattern.matcher(html);
        return matcher.replaceAll("").trim();
    }

    @POST
    @Path("/{id}/comments")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addComment(@PathParam("id") int id, String comment) {
        String url = BASE_URL + id + "?api-version=6.0";
        JsonReader jsonReader = Json.createReader(new StringReader(comment));
        JsonObject jsonObject = jsonReader.readObject();

        String comentario = jsonObject.getString("text");

        /*JsonArrayBuilder jsonPayloadObject = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                .add("op", "add")
                .add("path", "/fields/System.History")
                .add("value", comentario)
                );*/
        String jsonPayload = "["
                + "{ \"op\": \"add\", \"path\": \"/fields/System.History\", \"value\": \"" + comentario + " \"}"
                + "]";
        //String jsonPayload = jsonPayloadObject.toString();

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPatch request = new HttpPatch(url);
            String authHeader = "Basic " + Base64.getEncoder().encodeToString((":".concat(PAT)).getBytes());
            request.setHeader("Authorization", authHeader);
            request.setHeader("Content-Type", "application/json-patch+json");

            // Configura o payload JSON
            StringEntity entity = new StringEntity(jsonPayload, StandardCharsets.UTF_8);
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

    @PUT
    @Path("/{id}/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateWorkItem(@PathParam("id") int id, String jsonPayload) {
        String url = BASE_URL + id + "?api-version=7.1";

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPatch request = new HttpPatch(url);
            String authHeader = "Basic " + Base64.getEncoder().encodeToString((":".concat(PAT)).getBytes());
            request.setHeader("Authorization", authHeader);
            request.setHeader("Content-Type", "application/json-patch+json");

            // Configura o payload JSON
            StringEntity entity = new StringEntity(jsonPayload);
            request.setEntity(entity);

            try (CloseableHttpResponse response = client.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());

                    // Use Jackson to parse JSON
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readTree(responseBody);

                    return Response.ok(jsonNode.toString()).build();
                } else {
                    return Response.status(statusCode).entity("Failed to update work item").build();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error occurred").build();
        }
    }

    @POST
    @Path("/{type}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createWorkItem(@PathParam("type") String type, String jsonPayload) {
        String url = BASE_URL + "$" + type + "?api-version=6.0";

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(url);
            String authHeader = "Basic " + Base64.getEncoder().encodeToString((":".concat(PAT)).getBytes());
            request.setHeader("Authorization", authHeader);
            request.setHeader("Content-Type", "application/json");

            // Configura o payload JSON
            StringEntity entity = new StringEntity(jsonPayload);
            request.setEntity(entity);

            try (CloseableHttpResponse response = client.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 200 || statusCode == 201) {
                    String responseBody = EntityUtils.toString(response.getEntity());

                    // Use Jackson to parse JSON
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readTree(responseBody);

                    return Response.ok(jsonNode.toString()).build();
                } else {
                    return Response.status(statusCode).entity("Failed to create work item").build();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error occurred").build();
        }
    }

}