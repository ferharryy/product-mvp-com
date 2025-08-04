package mvp.resources;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject; // Importe JsonObject para o retorno de getMessagesForTokenSummary
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import mvp.service.SupabaseService;
import org.jboss.logging.Logger;

import java.util.List; // Importe List
import java.util.Map;
import java.util.HashMap; // Adicionado para HashMap
import java.util.stream.Collectors; // Adicionado para Collectors

@Path("/messages")
@Produces(MediaType.APPLICATION_JSON)
public class MessageResource {

    private static final Logger LOG = Logger.getLogger(MessageResource.class);

    @Inject
    SupabaseService supabaseService;

    /**
     * Endpoint para buscar dados de mensagens para a sumarização de tokens.
     * Permite filtrar por empresa e projeto.
     *
     * @param companyId Opcional. ID da empresa para filtrar.
     * @param projectId Opcional. ID do projeto para filtrar.
     * @return Uma Response contendo uma lista de dados de mensagens.
     */
    @GET
    @Path("/tokens-summary")
    @RolesAllowed({"admin", "user"}) // Ajuste os papéis conforme necessário
    public Response getTokensSummary(
            @QueryParam("companyId") Long companyId,
            @QueryParam("projectId") Long projectId) {
        try {
            // Chama o método síncrono do SupabaseService
            List<JsonObject> rawData = supabaseService.getMessagesForTokenSummary(companyId, projectId);

            // Converte List<JsonObject> para List<Map<String, Object>> para garantir compatibilidade
            // e para que o Jackson possa serializar corretamente para JSON no frontend.
            List<Map<String, Object>> data = rawData.stream()
                    .map(this::convertJsonObjectToMap)
                    .collect(Collectors.toList());

            return Response.ok(data).build();
        } catch (Exception e) {
            LOG.error("Erro ao buscar resumo de tokens: " + e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro ao buscar resumo de tokens: " + e.getMessage()))
                    .build();
        }
    }

    // Método auxiliar para converter JsonObject em Map<String, Object>
    private Map<String, Object> convertJsonObjectToMap(JsonObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        jsonObject.forEach((key, value) -> {
            map.put(key, extractJsonValue(value));
        });
        return map;
    }

    // Método auxiliar para extrair o valor de JsonValue
    private Object extractJsonValue(JsonValue value) {
        return switch (value.getValueType()) {
            case STRING -> ((JsonString) value).getString();
            case NUMBER -> {
                JsonNumber num = (JsonNumber) value;
                yield num.isIntegral() ? num.longValue() : num.doubleValue();
            }
            case TRUE -> true;
            case FALSE -> false;
            case NULL -> null;
            case OBJECT -> convertJsonObjectToMap(value.asJsonObject()); // Recursivamente para objetos aninhados
            case ARRAY -> value.asJsonArray().stream()
                    .map(this::extractJsonValue) // Recursivamente para elementos do array
                    .collect(Collectors.toList());
            default -> null; // Para outros tipos não esperados
        };
    }
}
