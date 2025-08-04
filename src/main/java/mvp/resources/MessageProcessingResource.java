package mvp.resources;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import mvp.service.SupabaseService;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Path("/message-processing") // Endpoint para fluxos de processamento de mensagens
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MessageProcessingResource {

    private static final Logger LOGGER = Logger.getLogger(MessageProcessingResource.class.getName());

    @Inject
    SupabaseService supabaseService;

    // CREATE
    @POST
    @RolesAllowed("user") // Ajuste as roles conforme sua necessidade de segurança
    public Response createMessageProcessing(JsonObject flowData) {
        // Validação dos campos obrigatórios conforme a tabela message_processing e o frontend
        String prompt = flowData.getString("prompt", "").trim();
        Long interaction = null;
        if (flowData.containsKey("interaction") && flowData.get("interaction").getValueType() == JsonValue.ValueType.NUMBER) {
            interaction = flowData.getJsonNumber("interaction").longValue();
        }
        Long interactionOrder = null;
        if (flowData.containsKey("interaction_order") && flowData.get("interaction_order").getValueType() == JsonValue.ValueType.NUMBER) {
            interactionOrder = flowData.getJsonNumber("interaction_order").longValue();
        }
        boolean isFinal = flowData.getBoolean("is_final", false);

        // NOVOS CAMPOS: id_company e id_project (nomes corretos do DB)
        Long idCompany = null;
        if (flowData.containsKey("id_company") && flowData.get("id_company").getValueType() == JsonValue.ValueType.NUMBER) {
            idCompany = flowData.getJsonNumber("id_company").longValue();
        }
        Long idProject = null;
        if (flowData.containsKey("id_project") && flowData.get("id_project").getValueType() == JsonValue.ValueType.NUMBER) {
            idProject = flowData.getJsonNumber("id_project").longValue();
        }

        if (prompt.isEmpty() || interaction == null || interactionOrder == null || idCompany == null || idProject == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "Campos obrigatórios (prompt, interaction, interaction_order, id_company, id_project) são necessários."))
                    .build();
        }

        // Constrói o payload para o Supabase, usando os nomes de coluna corretos
        JsonObjectBuilder flowToSupabaseBuilder = Json.createObjectBuilder()
                .add("prompt", prompt)
                .add("interaction", interaction)
                .add("interaction_order", interactionOrder)
                .add("is_final", isFinal);

        // Adiciona id_company e id_project se não forem nulos
        if (idCompany != null) {
            flowToSupabaseBuilder.add("id_company", idCompany);
        }
        if (idProject != null) {
            flowToSupabaseBuilder.add("id_project", idProject);
        }

        try {
            Response supabaseResponse = supabaseService.createMessageProcessing(flowToSupabaseBuilder.build().toString());

            if (supabaseResponse.getStatus() == 201) {
                return Response.status(Response.Status.CREATED)
                        .entity(Map.of("message", "Fluxo criado com sucesso."))
                        .build();
            }

            String error = supabaseResponse.readEntity(String.class);
            LOGGER.severe("Erro Supabase ao criar fluxo: " + error);
            return Response.status(supabaseResponse.getStatus())
                    .entity(Map.of("message", "Erro ao criar fluxo: " + error))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao criar fluxo: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro interno ao criar fluxo."))
                    .build();
        }
    }

    // READ ALL
    @GET
    @RolesAllowed("user")
    public Response getAllMessageProcessing() {
        try {
            List<JsonObject> flows = supabaseService.getAllMessageProcessing();

            // Mapeia para List<Map<String, Object>> e aplica filterMessageProcessingData
            List<Map<String, Object>> filteredFlows = flows.stream()
                    .map(this::filterMessageProcessingData) // Aplica o filtro/conversão para Map
                    .collect(Collectors.toList());

            return Response.ok(filteredFlows).build(); // Retorna a lista de Map<String, Object>

        } catch (Exception e) {
            LOGGER.severe("Erro ao listar fluxos: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro ao listar fluxos."))
                    .build();
        }
    }

    // READ BY ID
    @GET
    @RolesAllowed("user")
    @Path("/{id}")
    public Response getMessageProcessingById(@PathParam("id") Long id) {
        try {
            JsonObject flow = supabaseService.getMessageProcessingById(id);
            if (flow != null) {
                return Response.ok(filterMessageProcessingData(flow)).build(); // Aplica o filtro/conversão para Map
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("message", "Fluxo não encontrado."))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao buscar fluxo por ID: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro ao buscar fluxo."))
                    .build();
        }
    }

    // UPDATE
    @PATCH
    @Path("/{id}")
    @RolesAllowed("user")
    public Response updateMessageProcessing(@PathParam("id") Long id, JsonObject flowData) {
        try {
            JsonObject existingFlow = supabaseService.getMessageProcessingById(id);
            if (existingFlow == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("message", "Fluxo não encontrado para atualização."))
                        .build();
            }

            // Constrói o payload de atualização, excluindo o ID e usando os nomes de coluna corretos
            JsonObjectBuilder updateBuilder = Json.createObjectBuilder();
            flowData.forEach((key, value) -> {
                if (!"id".equals(key)) { // Não permite atualização do ID
                    updateBuilder.add(key, value);
                }
            });

            JsonObject updatePayload = updateBuilder.build();
            if (updatePayload.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("message", "Nenhum dado para atualizar."))
                        .build();
            }

            Response response = supabaseService.updateMessageProcessing(id, updatePayload.toString());

            if (response.getStatus() == 204) {
                return Response.noContent().build();
            }

            String error = response.readEntity(String.class);
            LOGGER.severe("Erro Supabase ao atualizar fluxo: " + error);
            return Response.status(response.getStatus())
                    .entity(Map.of("message", "Erro ao atualizar fluxo: " + error))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao atualizar fluxo: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro interno ao atualizar fluxo."))
                    .build();
        }
    }

    // DELETE
    @DELETE
    @Path("/{id}")
    @RolesAllowed("user")
    public Response deleteMessageProcessing(@PathParam("id") Long id) {
        try {
            Response response = supabaseService.deleteMessageProcessing(id);

            if (response.getStatus() == 204) {
                return Response.noContent().build();
            } else if (response.getStatus() == 404) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("message", "Fluxo não encontrado para exclusão."))
                        .build();
            }

            String error = response.readEntity(String.class);
            LOGGER.severe("Erro Supabase ao deletar fluxo: " + error);
            return Response.status(response.getStatus())
                    .entity(Map.of("message", "Erro ao deletar fluxo: " + error))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao deletar fluxo: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro interno ao deletar fluxo."))
                    .build();
        }
    }

    // 🔧 Converte JsonObject em Map<String, Object> plano, seguindo o padrão do UserResource
    private Map<String, Object> filterMessageProcessingData(JsonObject flow) {
        Map<String, Object> flatFlow = new HashMap<>();
        flow.forEach((key, value) -> {
            // Não há campos específicos para remover como 'password_hash' aqui
            flatFlow.put(key, extractJsonValue(value));
        });
        return flatFlow;
    }

    // 🔧 Extrai o valor primitivo de um JsonValue, seguindo o padrão do UserResource
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
            case OBJECT -> value.asJsonObject(); // Retorna o JsonObject para aninhamento
            case ARRAY -> value.asJsonArray(); // Retorna o JsonArray
        };
    }
}
