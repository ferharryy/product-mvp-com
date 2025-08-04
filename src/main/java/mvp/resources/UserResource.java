package mvp.resources;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import mvp.service.AuthService;
import mvp.service.SupabaseService;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class UserResource {

    private static final Logger LOGGER = Logger.getLogger(UserResource.class.getName());

    @Inject SupabaseService supabaseService;
    @Inject AuthService authService;

    @POST
    @RolesAllowed("user")
    public Response createUser(JsonObject userData) {
        String username = userData.getString("username", "").trim();
        String password = userData.getString("password", "").trim();
        String name     = userData.getString("name", "").trim();
        String email    = userData.getString("email", "").trim();
        String role     = userData.getString("role", "USER").trim();

        Long companyId = userData.containsKey("company_id") && userData.get("company_id").getValueType() == JsonValue.ValueType.NUMBER
                ? userData.getJsonNumber("company_id").longValue()
                : null;

        boolean isResponsible = userData.getBoolean("is_responsible", false);

        if (username.isEmpty() || password.isEmpty() || name.isEmpty() || email.isEmpty() || role.isEmpty() || companyId == null || companyId <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "Campos obrigatórios: username, password, name, email, role, company_id"))
                    .build();
        }

        try {
            if (supabaseService.getUserByUsername(username) != null) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("message", "Usuário com este nome já existe."))
                        .build();
            }

            String hashedPassword = authService.hashPassword(password);

            JsonObject userToSupabase = Json.createObjectBuilder()
                    .add("username", username)
                    .add("password_hash", hashedPassword)
                    .add("name", name)
                    .add("email", email)
                    .add("role", role)
                    .add("company_id", companyId)
                    .add("is_responsible", isResponsible)
                    .build();

            Response supabaseResponse = supabaseService.createUser(userToSupabase.toString());

            if (supabaseResponse.getStatus() == 201) {
                return Response.status(Response.Status.CREATED)
                        .entity(Map.of("message", "Usuário criado com sucesso."))
                        .build();
            }

            String errorDetails = supabaseResponse.readEntity(String.class);
            LOGGER.severe("Erro Supabase ao criar usuário: " + errorDetails);
            return Response.status(supabaseResponse.getStatus())
                    .entity(Map.of("message", "Erro ao criar usuário: " + errorDetails))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao criar usuário: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro interno ao criar usuário."))
                    .build();
        }
    }


    @GET
    @RolesAllowed("user")
    public Response getAllUsers() {
        try {
            List<Map<String, Object>> users = supabaseService.getAllUsersAsList().stream()
                    .map(this::filterUserData)
                    .collect(Collectors.toList());

            return Response.ok(users).build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao listar usuários: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro ao listar usuários."))
                    .build();
        }
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("user")
    public Response getUserById(@PathParam("id") Long id) {
        try {
            JsonObject user = supabaseService.getUserById(id);
            if (user != null) {
                return Response.ok(filterUserData(user)).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("message", "Usuário não encontrado."))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao buscar usuário por ID: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro ao buscar usuário."))
                    .build();
        }
    }

    @PATCH
    @Path("/{id}")
    @RolesAllowed("user")
    public Response updateUser(@PathParam("id") Long id, JsonObject userData) {
        try {
            JsonObject existingUser = supabaseService.getUserById(id);
            if (existingUser == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("message", "Usuário não encontrado."))
                        .build();
            }

            JsonObjectBuilder updateBuilder = Json.createObjectBuilder();
            userData.forEach((key, value) -> {
                if ("password".equals(key)) {
                    String newPassword = ((JsonString) value).getString();
                    updateBuilder.add("password_hash", authService.hashPassword(newPassword));
                } else if (!"id".equals(key) && !"password_hash".equals(key)) {
                    updateBuilder.add(key, value);
                }
            });

            JsonObject updatePayload = updateBuilder.build();

            if (updatePayload.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("message", "Nenhum dado para atualizar."))
                        .build();
            }

            Response supabaseResponse = supabaseService.updateUser(id, updatePayload.toString());

            if (supabaseResponse.getStatus() == 204) {
                return Response.noContent().build();
            }

            String errorDetails = supabaseResponse.readEntity(String.class);
            LOGGER.severe("Erro Supabase ao atualizar usuário: " + errorDetails);
            return Response.status(supabaseResponse.getStatus())
                    .entity(Map.of("message", "Erro ao atualizar: " + errorDetails))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao atualizar usuário: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro interno ao atualizar usuário."))
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("user")
    public Response deleteUser(@PathParam("id") Long id) {
        try {
            Response supabaseResponse = supabaseService.deleteUser(id);

            if (supabaseResponse.getStatus() == 204) {
                return Response.noContent().build();
            } else if (supabaseResponse.getStatus() == 404) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("message", "Usuário não encontrado."))
                        .build();
            }

            String errorDetails = supabaseResponse.readEntity(String.class);
            LOGGER.severe("Erro Supabase ao deletar usuário: " + errorDetails);
            return Response.status(supabaseResponse.getStatus())
                    .entity(Map.of("message", "Erro ao deletar usuário: " + errorDetails))
                    .build();

        } catch (Exception e) {
            LOGGER.severe("Erro ao deletar usuário: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", "Erro interno ao deletar usuário."))
                    .build();
        }
    }

    // 🔧 Converte JsonObject aninhado em Map<String, Object> plano
    private Map<String, Object> filterUserData(JsonObject user) {
        Map<String, Object> flatUser = new HashMap<>();
        user.forEach((key, value) -> {
            if (!"password_hash".equals(key)) {
                flatUser.put(key, extractJsonValue(value));
            }
        });
        return flatUser;
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
            case OBJECT -> value.asJsonObject(); // ou converter recursivamente, se quiser
            case ARRAY -> value.asJsonArray();
        };
    }
}
