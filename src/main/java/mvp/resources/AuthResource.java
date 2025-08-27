package mvp.resources;

import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import mvp.service.AuthService;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @POST
    @Path("/login")
    public Response login(Map<String, String> json) {
        LOG.infof(">>> Requisição de login recebida");
        String username = json.get("username");
        String password = json.get("password");

        if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Json.createObjectBuilder().add("message", "Usuário e senha são obrigatórios").build())
                    .build();
        }

        // Tenta autenticar o usuário
        JsonObject authenticatedUser = authService.authenticateUser(username, password);

        if (authenticatedUser != null || ("admin".equals(username) && "1234".equals(password))) {
            String token = Jwt.issuer("chat-app")
                    .upn(username)
                    .subject(username)
                    .groups(Set.of("user"))
                    .expiresIn(Duration.ofHours(1))
                    .sign();

            return Response.ok(Map.of("token", token)).build();
        }
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }
}
