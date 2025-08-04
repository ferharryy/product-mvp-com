package mvp.resources;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import mvp.service.SupabaseService;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Path("/logs") // Endpoint para os logs
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class LogResource {

    private static final Logger LOGGER = Logger.getLogger(LogResource.class.getName());

    @Inject
    SupabaseService supabaseService;

    /**
     * Busca os logs mais recentes da tabela 'logs' do Supabase.
     * Permite especificar um limite de logs a serem retornados.
     *
     * @param limit Opcional. O número máximo de logs a serem retornados. Padrão: 100.
     * @return Uma Response contendo uma lista de objetos de log.
     */
    @GET
    @RolesAllowed({"user", "admin"}) // Ajuste as roles conforme sua necessidade de segurança
    public Response getLogs(@QueryParam("limit") @DefaultValue("100") int limit) {
        try {
            // Chama o método do SupabaseService para buscar os logs
            List<JsonObject> rawLogs = supabaseService.getLogs(limit);

            // Converte List<JsonObject> para List<Map<String, Object>> para garantir compatibilidade
            // e para que o Jackson possa serializar corretamente para JSON no frontend.
            List<Map<String, Object>> logs = rawLogs.stream()
                    .map(this::convertJsonObjectToMap)
                    .collect(Collectors.toList());

            return Response.ok(logs).build();
        } catch (Exception e) {
            LOGGER.severe("Erro ao buscar logs: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro ao buscar logs: " + e.getMessage()))
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
            case STRING -> ((jakarta.json.JsonString) value).getString(); // Cast explícito para JsonString
            case NUMBER -> {
                jakarta.json.JsonNumber num = (jakarta.json.JsonNumber) value; // Cast explícito para JsonNumber
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
