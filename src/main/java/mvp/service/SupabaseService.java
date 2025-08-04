package mvp.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonObjectBuilder; // Importar para JsonObjectBuilder
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.time.OffsetDateTime; // Importar para timestamp
import java.time.format.DateTimeFormatter; // Importar para formatar timestamp
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class SupabaseService {

    private static final Logger LOGGER = Logger.getLogger(SupabaseService.class.getName());
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(SupabaseService.class);

    @ConfigProperty(name = "supabase.url")
    String supabaseUrl;

    @ConfigProperty(name = "supabase.service-role-key")
    String supabaseServiceRoleKey;

    private final Client client = ClientBuilder.newClient();

    // --- Métodos Auxiliares para Requisições HTTP ---

    /**
     * Envia uma requisição HTTP genérica (GET, POST, PATCH, DELETE) com a chave de role de serviço.
     */
    private Response sendRequest(String method, String url, String jsonBody) {
        try {
            return client.target(url)
                    .request(MediaType.APPLICATION_JSON)
                    .header("apikey", supabaseServiceRoleKey)
                    .header("Authorization", "Bearer " + supabaseServiceRoleKey)
                    .method(method, jsonBody != null ? Entity.json(jsonBody) : null);
        } catch (Exception e) {
            LOGGER.severe("Erro ao enviar " + method + " para " + url + ": " + e.getMessage());
            throw new WebApplicationException("Erro de comunicação com Supabase.", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Envia uma requisição GET com a chave de role de serviço e retorna um JsonArray.
     * Lida com respostas que podem ser um único objeto, encapsulando-o em um array.
     */
    private JsonArray sendGetRequest(String url) {
        try {
            Response response = sendRequest("GET", url, null);

            if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                String body = response.readEntity(String.class);
                if (body == null || body.isBlank()) return Json.createArrayBuilder().build();

                JsonReader reader = Json.createReader(new StringReader(body));
                JsonStructure json = reader.read();

                if (json.getValueType() == JsonValue.ValueType.ARRAY) {
                    return (JsonArray) json;
                } else if (json.getValueType() == JsonValue.ValueType.OBJECT) {
                    return Json.createArrayBuilder().add((JsonObject) json).build();
                } else {
                    LOGGER.severe("Tipo de resposta inesperado do Supabase para GET: " + json.getValueType() + " para " + url);
                    throw new WebApplicationException("Tipo de resposta inesperado do Supabase.", Response.Status.INTERNAL_SERVER_ERROR);
                }
            }

            String errorDetails = response.readEntity(String.class);
            LOGGER.severe("Erro Supabase GET: Status " + response.getStatus() + " - " + errorDetails + " para URL: " + url);
            throw new WebApplicationException("Erro Supabase: " + errorDetails, response.getStatus());

        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.severe("Exceção ao fazer GET para " + url + ": " + e.getMessage());
            throw new WebApplicationException("Erro interno ao se comunicar com Supabase.", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    // --- Métodos auxiliares para processar respostas ---

    /**
     * Converte um JsonArray em uma List<JsonObject>.
     */
    private List<JsonObject> toList(JsonArray array) {
        List<JsonObject> list = new ArrayList<>();
        for (JsonValue value : array) {
            if (value.getValueType() == JsonValue.ValueType.OBJECT) {
                list.add(value.asJsonObject());
            }
        }
        return list;
    }

    /**
     * Retorna o primeiro JsonObject de um JsonArray, ou null se o array estiver vazio.
     */
    private JsonObject getFirst(JsonArray array) {
        return array.isEmpty() ? null : array.getJsonObject(0);
    }

    // --- Métodos Públicos ---

    // Company
    public Response createCompany(String json) {
        return sendRequest("POST", supabaseUrl + "/rest/v1/companies", json);
    }


    public List<JsonObject> getAllCompanies() {
        return toList(sendGetRequest(supabaseUrl + "/rest/v1/companies"));
    }

    public JsonObject getCompanyById(Long id) {
        return getFirst(sendGetRequest(supabaseUrl + "/rest/v1/companies?id=eq." + id));
    }

    public Response updateCompany(Long id, String json) {
        return sendRequest("PATCH", supabaseUrl + "/rest/v1/companies?id=eq." + id, json);
    }

    public Response deleteCompany(Long id) {
        return sendRequest("DELETE", supabaseUrl + "/rest/v1/companies?id=eq." + id, null);
    }

    public List<JsonObject> getCompanyOptions() {
        String query = supabaseUrl + "/rest/v1/companies?select=id,name&order=name";
        return toList(sendGetRequest(query));
    }

    // User
    public Response createUser(String json) {
        return sendRequest("POST", supabaseUrl + "/rest/v1/users", json);
    }

    public List<JsonObject> getAllUsersAsList() {
        return toList(sendGetRequest(supabaseUrl + "/rest/v1/users"));
    }

    public JsonObject getUserById(Long id) {
        return getFirst(sendGetRequest(supabaseUrl + "/rest/v1/users?id=eq." + id));
    }

    public JsonObject getUserByUsername(String username) {
        return getFirst(sendGetRequest(supabaseUrl + "/rest/v1/users?username=eq." + username));
    }

    public Response updateUser(Long id, String json) {
        return sendRequest("PATCH", supabaseUrl + "/rest/v1/users?id=eq." + id, json);
    }

    public Response deleteUser(Long id) {
        return sendRequest("DELETE", supabaseUrl + "/rest/v1/users?id=eq." + id, null);
    }

    // Work Items
    public Response saveWorkItem(String json) {
        return sendRequest("POST", supabaseUrl + "/rest/v1/work_item", json);
    }

    public Response saveMessage(String json) {
        return sendRequest("POST",supabaseUrl + "/rest/v1/messages", json);
    }

    public List<JsonObject> getMessagesByWorkItemId(String id) {
        return toList(sendGetRequest(supabaseUrl + "/rest/v1/messages?id_workitem=eq." + id + "&order=id"));
    }

    public JsonObject getPatAndUrlFromUser(String key, String baseUrl) {
        return getFirst(sendGetRequest(supabaseUrl + "/rest/v1/project_pat_view?project_key=eq." + key + "&url=eq." + baseUrl));
    }

    public JsonObject hasFinalAssistantMessage(String workItemId) {
        JsonObject msg = getFirst(sendGetRequest(supabaseUrl + "/rest/v1/final_assistant_messages?id_workitem=eq." + workItemId + "&order=interaction.desc,interaction_order.desc&limit=1"));
        if (msg == null) return null;

        return Json.createObjectBuilder()
                .add("interaction", msg.getInt("interaction"))
                .add("interaction_order", msg.getInt("interaction_order"))
                .add("is_final", msg.getBoolean("is_final"))
                .build();
    }

    public JsonObject getNextInteractionOrder(int interaction, int order) {
        JsonObject msg = getFirst(sendGetRequest(supabaseUrl + "/rest/v1/message_processing?interaction=eq." + interaction + "&interaction_order=eq." + (order + 1)));
        if (msg == null) return null;

        return Json.createObjectBuilder()
                .add("prompt", msg.getString("prompt"))
                .add("interaction", msg.getInt("interaction"))
                .add("interaction_order", msg.getInt("interaction_order"))
                .build();
    }

    public JsonObject getNextInteraction(int interaction) {
        JsonObject msg = getFirst(sendGetRequest(supabaseUrl + "/rest/v1/message_processing?interaction=eq." + (interaction + 1)));
        if (msg == null) return null;

        return Json.createObjectBuilder()
                .add("prompt", msg.getString("prompt"))
                .add("interaction", msg.getInt("interaction"))
                .add("interaction_order", msg.getInt("interaction_order"))
                .add("is_final", msg.getBoolean("is_final"))
                .build();
    }

    // --- Métodos CRUD para Projetos ---
    public Response createProject(String json) {
        return sendRequest("POST", supabaseUrl + "/rest/v1/project", json);
    }

    public List<JsonObject> getAllProjects() {
        return toList(sendGetRequest(supabaseUrl + "/rest/v1/project"));
    }

    public JsonObject getProjectById(Long id) {
        return getFirst(sendGetRequest(supabaseUrl + "/rest/v1/project?id=eq." + id));
    }

    public Response updateProject(Long id, String json) {
        return sendRequest("PATCH", supabaseUrl + "/rest/v1/project?id=eq." + id, json);
    }

    public Response deleteProject(Long id) {
        return sendRequest("DELETE", supabaseUrl + "/rest/v1/project?id=eq." + id, null);
    }

    public List<JsonObject> getProjectOptions() {
        String query = supabaseUrl + "/rest/v1/project?select=id,name,company_id&order=name";
        return toList(sendGetRequest(query));
    }

    // --- Métodos CRUD para Message Processing (Fluxo) ---
    public Response createMessageProcessing(String json) {
        return sendRequest("POST", supabaseUrl + "/rest/v1/message_processing", json);
    }

    public List<JsonObject> getAllMessageProcessing() {
        return toList(sendGetRequest(supabaseUrl + "/rest/v1/message_processing"));
    }

    public JsonObject getMessageProcessingById(Long id) {
        return getFirst(sendGetRequest(supabaseUrl + "/rest/v1/message_processing?id=eq." + id));
    }

    public Response updateMessageProcessing(Long id, String json) {
        return sendRequest("PATCH", supabaseUrl + "/rest/v1/message_processing?id=eq." + id, json);
    }

    public Response deleteMessageProcessing(Long id) {
        return sendRequest("DELETE", supabaseUrl + "/rest/v1/message_processing?id=eq." + id, null);
    }

    // --- MÉTODO PARA RESUMO DE TOKENS (Revertido para Bloqueante) ---
    /**
     * Busca dados de mensagens para sumarização de tokens.
     * Inclui company_id, project_id, created_at e message.
     *
     * @param companyId Opcional. Filtra por ID da empresa.
     * @param projectId Opcional. Filtra por ID do projeto.
     * @return Uma lista de JsonObjects contendo os dados das mensagens.
     */
    public List<JsonObject> getMessagesForTokenSummary(Long companyId, Long projectId) {
        StringBuilder urlBuilder = new StringBuilder(supabaseUrl + "/rest/v1/messages?select=company_id,project_id,created_at,message");

        if (companyId != null) {
            urlBuilder.append("&company_id=eq.").append(companyId);
        }
        if (projectId != null) {
            urlBuilder.append("&project_id=eq.").append(projectId);
        }
        urlBuilder.append("&order=created_at.asc"); // Ordena por data para facilitar o processamento no frontend

        return toList(sendGetRequest(urlBuilder.toString()));
    }

    /**
     * Salva um registro de log na tabela 'logs' do Supabase.
     * @param level O nível do log (ex: INFO, WARN, ERROR).
     * @param message A mensagem do log.
     * @param context Um JsonObject opcional para dados de contexto adicionais.
     */
    public void saveLog(String level, String message, JsonObject context) {
        JsonObjectBuilder logEntryBuilder = Json.createObjectBuilder()
                .add("level", level)
                .add("message", message);

        // Adiciona o timestamp atual
        logEntryBuilder.add("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        if (context != null) {
            logEntryBuilder.add("context", context);
        }

        try {
            Response response = sendRequest("POST", supabaseUrl + "/rest/v1/logs", logEntryBuilder.build().toString());
            if (response.getStatus() != 201) {
                log.error("Falha ao salvar log no Supabase. Status: " + response.getStatus() + ", Erro: " + response.readEntity(String.class));
            }
        } catch (Exception e) {
            log.error("Exceção ao tentar salvar log no Supabase: " + e.getMessage(), e);
        }
    }

    /**
     * Busca os logs da tabela 'logs' do Supabase.
     * @param limit O número máximo de logs a serem retornados.
     * @return Uma lista de JsonObjects representando os logs.
     */
    public List<JsonObject> getLogs(int limit) {
        String query = supabaseUrl + "/rest/v1/logs?order=timestamp.desc&limit=" + limit;
        return toList(sendGetRequest(query));
    }
}
