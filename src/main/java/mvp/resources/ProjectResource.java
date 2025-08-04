package mvp.resources;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.*; // Certifique-se de que jakarta.json.* está importado
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import mvp.service.SupabaseService;

import java.util.List;
import java.util.Map;
import java.util.HashMap; // Adicionado para HashMap
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Path("/projects") // Endpoint para projetos
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ProjectResource {

    private static final Logger LOGGER = Logger.getLogger(ProjectResource.class.getName());

    @Inject
    SupabaseService supabaseService;

    // CREATE
    @POST
    @RolesAllowed("user") // Ajuste as roles conforme sua necessidade de segurança
    public Response createProject(JsonObject projectData) {
        // Validação básica dos campos obrigatórios
        String name = projectData.getString("name", "").trim();
        String key = projectData.getString("key", "").trim(); // 'key' é NOT NULL na tabela
        Long userId = null;
        if (projectData.containsKey("user_id") && projectData.get("user_id").getValueType() == JsonValue.ValueType.NUMBER) {
            userId = projectData.getJsonNumber("user_id").longValue();
        }
        Long companyId = null;
        if (projectData.containsKey("company_id") && projectData.get("company_id").getValueType() == JsonValue.ValueType.NUMBER) {
            companyId = projectData.getJsonNumber("company_id").longValue();
        }

        if (name.isEmpty() || key.isEmpty() || userId == null || companyId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "Campos obrigatórios (name, key, user_id, company_id) são necessários."))
                    .build();
        }

        try {
            Response supabaseResponse = supabaseService.createProject(projectData.toString());

            // A resposta do Supabase para POST geralmente é 201 Created
            if (supabaseResponse.getStatus() == 201) { // Verifica se o status é 201
                // Retorna apenas uma mensagem de sucesso, seguindo o padrão do UserResource
                return Response.status(Response.Status.CREATED)
                        .entity(Map.of("message", "Projeto criado com sucesso."))
                        .build();
            }

            String error = supabaseResponse.readEntity(String.class);
            LOGGER.severe("Erro Supabase ao criar projeto: " + error);
            return Response.status(supabaseResponse.getStatus())
                    .entity(Map.of("message", "Erro ao criar projeto: " + error))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao criar projeto: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro interno ao criar projeto."))
                    .build();
        }
    }

    // READ ALL
    @GET
    @RolesAllowed("user")
    public Response getAllProjects() {
        try {
            // supabaseService.getAllProjects() retorna List<JsonObject>
            // Mapeia para List<Map<String, Object>> e aplica filterProjectData
            List<Map<String, Object>> projects = supabaseService.getAllProjects().stream()
                    .map(this::filterProjectData) // Aplica o filtro/conversão para Map
                    .collect(Collectors.toList());

            return Response.ok(projects).build(); // Retorna a lista de Map<String, Object>

        } catch (Exception e) {
            LOGGER.severe("Erro ao listar projetos: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro ao listar projetos."))
                    .build();
        }
    }

    // READ BY ID
    @GET
    @RolesAllowed("user")
    @Path("/{id}")
    public Response getProjectById(@PathParam("id") Long id) {
        try {
            JsonObject project = supabaseService.getProjectById(id);
            if (project != null) {
                return Response.ok(filterProjectData(project)).build(); // Aplica o filtro/conversão para Map
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("message", "Projeto não encontrado."))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao buscar projeto por ID: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro ao buscar projeto."))
                    .build();
        }
    }

    // UPDATE
    @PATCH
    @Path("/{id}")
    @RolesAllowed("user")
    public Response updateProject(@PathParam("id") Long id, JsonObject projectJson) {
        try {
            JsonObject existingProject = supabaseService.getProjectById(id);
            if (existingProject == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("message", "Projeto não encontrado para atualização."))
                        .build();
            }

            JsonObjectBuilder builder = Json.createObjectBuilder();
            projectJson.forEach((key, value) -> {
                if (!"id".equals(key)) { // Não permite atualização do ID
                    builder.add(key, value);
                }
            });

            JsonObject updatePayload = builder.build();
            if (updatePayload.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("message", "Nenhum dado para atualizar."))
                        .build();
            }

            Response response = supabaseService.updateProject(id, updatePayload.toString());

            if (response.getStatus() == 204) {
                return Response.noContent().build();
            }

            String error = response.readEntity(String.class);
            LOGGER.severe("Erro Supabase ao atualizar projeto: " + error);
            return Response.status(response.getStatus())
                    .entity(Map.of("message", "Erro ao atualizar projeto: " + error))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao atualizar projeto: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro interno ao atualizar projeto."))
                    .build();
        }
    }

    // DELETE
    @DELETE
    @Path("/{id}")
    @RolesAllowed("user")
    public Response deleteProject(@PathParam("id") Long id) {
        try {
            Response response = supabaseService.deleteProject(id);
            if (response.getStatus() == 204) {
                return Response.noContent().build();
            } else if (response.getStatus() == 404) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("message", "Projeto não encontrado para exclusão."))
                        .build();
            }

            String error = response.readEntity(String.class);
            LOGGER.severe("Erro Supabase ao deletar projeto: " + error);
            return Response.status(response.getStatus())
                    .entity(Map.of("message", "Erro ao deletar projeto: " + error))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao deletar projeto: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro interno ao deletar projeto."))
                    .build();
        }
    }

    // 🔧 Converte JsonObject em Map<String, Object> plano
    private Map<String, Object> filterProjectData(JsonObject project) {
        Map<String, Object> flatProject = new HashMap<>();
        project.forEach((key, value) -> {
            // Não há campos específicos para remover como password_hash aqui
            flatProject.put(key, extractJsonValue(value));
        });
        return flatProject;
    }

    // 🔧 Extrai o valor primitivo de um JsonValue
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
            case OBJECT -> value.asJsonObject(); // Retorna o JsonObject, se for para achatamento recursivo, seria diferente
            case ARRAY -> value.asJsonArray(); // Retorna o JsonArray
        };
    }
}
