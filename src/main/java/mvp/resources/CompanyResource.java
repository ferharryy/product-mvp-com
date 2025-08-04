package mvp.resources;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import mvp.service.SupabaseService;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Path("/companies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class CompanyResource {

    private static final Logger LOGGER = Logger.getLogger(CompanyResource.class.getName());

    @Inject
    SupabaseService supabaseService;

    // CREATE
    @POST
    @RolesAllowed("user")
    public Response createCompany(JsonObject companyData) {
        String name = companyData.getString("name", "").trim();

        if (name.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "Campo obrigatório: name"))
                    .build();
        }

        try {
            Response supabaseResponse = supabaseService.createCompany(companyData.toString());

            if (supabaseResponse.getStatus() == 201) {
                return Response.status(Response.Status.CREATED)
                        .entity(Map.of("message", "Empresa criada com sucesso"))
                        .build();
            }

            String error = supabaseResponse.readEntity(String.class);
            LOGGER.severe("Erro Supabase ao criar empresa: " + error);
            return Response.status(supabaseResponse.getStatus())
                    .entity(Map.of("message", "Erro ao criar empresa: " + error))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao criar empresa: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro interno ao criar empresa."))
                    .build();
        }
    }

    @GET
    @Path("/company-options")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCompanyOptions() {
        try {
            List<Map<String, Object>> companies = supabaseService.getCompanyOptions().stream()
                    .map(this::flattenJson)
                    .collect(Collectors.toList());

            return Response.ok(companies).build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao listar empresas: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro ao listar empresas."))
                    .build();
        }
    }

    // READ ALL
    @GET
    @RolesAllowed("user")
    public Response getAllCompanies() {
        try {
            List<Map<String, Object>> companies = supabaseService.getAllCompanies().stream()
                    .map(this::flattenJson)
                    .collect(Collectors.toList());

            return Response.ok(companies).build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao listar empresas: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro ao listar empresas."))
                    .build();
        }
    }

    // READ BY ID
    @GET
    @RolesAllowed("user")
    @Path("/{id}")
    public Response getCompanyById(@PathParam("id") Long id) {
        try {
            JsonObject company = supabaseService.getCompanyById(id);
            if (company != null) {
                return Response.ok(flattenJson(company)).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("message", "Empresa não encontrada."))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao buscar empresa por ID: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro ao buscar empresa."))
                    .build();
        }
    }

    // UPDATE
    @PATCH
    @Path("/{id}")
    @RolesAllowed("user")
    public Response updateCompany(@PathParam("id") Long id, JsonObject companyJson) {
        try {
            JsonObject existingCompany = supabaseService.getCompanyById(id);
            if (existingCompany == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("message", "Empresa não encontrada."))
                        .build();
            }

            JsonObjectBuilder builder = Json.createObjectBuilder();
            companyJson.forEach((key, value) -> {
                if (!"id".equals(key)) {
                    builder.add(key, value);
                }
            });

            JsonObject updatePayload = builder.build();
            if (updatePayload.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("message", "Nenhum dado para atualizar."))
                        .build();
            }

            Response response = supabaseService.updateCompany(id, updatePayload.toString());

            if (response.getStatus() == 204) {
                return Response.noContent().build();
            }

            String error = response.readEntity(String.class);
            LOGGER.severe("Erro Supabase ao atualizar empresa: " + error);
            return Response.status(response.getStatus())
                    .entity(Map.of("message", "Erro ao atualizar empresa: " + error))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao atualizar empresa: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro interno ao atualizar empresa."))
                    .build();
        }
    }

    // DELETE
    @DELETE
    @Path("/{id}")
    @RolesAllowed("user")
    public Response deleteCompany(@PathParam("id") Long id) {
        try {
            Response response = supabaseService.deleteCompany(id);
            if (response.getStatus() == 204) {
                return Response.noContent().build();
            } else if (response.getStatus() == 404) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("message", "Empresa não encontrada."))
                        .build();
            }

            String error = response.readEntity(String.class);
            LOGGER.severe("Erro Supabase ao deletar empresa: " + error);
            return Response.status(response.getStatus())
                    .entity(Map.of("message", "Erro ao deletar empresa: " + error))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao deletar empresa: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro interno ao deletar empresa."))
                    .build();
        }
    }

    // 🔧 Helper para converter JsonObject em Map plano
    private Map<String, Object> flattenJson(JsonObject json) {
        Map<String, Object> map = new HashMap<>();
        json.forEach((key, value) -> map.put(key, extractJsonValue(value)));
        return map;
    }

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
            case OBJECT -> value.asJsonObject(); // ou converter recursivamente
            case ARRAY -> value.asJsonArray();
        };
    }
}
