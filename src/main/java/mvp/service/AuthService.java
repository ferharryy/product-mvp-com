package mvp.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;
import org.mindrot.jbcrypt.BCrypt;
import java.util.logging.Logger;

@ApplicationScoped
public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    @Inject
    SupabaseService supabaseService; // Para interagir com o Supabase

    // Nível de "cost" para o BCrypt. Um valor maior aumenta a segurança, mas também o tempo de processamento.
    // 10-12 é um bom ponto de partida.
    private static final int BCRYPT_SALT_ROUNDS = 12;

    /**
     * Gera uma hash BCrypt para a senha fornecida.
     * @param password A senha em texto puro.
     * @return A hash da senha.
     */
    public String hashPassword(String password) {
        // Gerar um salt (sal) aleatório e então gerar a hash com o sal e o custo.
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_SALT_ROUNDS));
    }

    /**
     * Verifica se uma senha em texto puro corresponde a uma hash BCrypt.
     * @param plainPassword A senha em texto puro fornecida pelo usuário.
     * @param hashedPassword A hash da senha armazenada no banco de dados.
     * @return true se as senhas correspondem, false caso contrário.
     */
    public boolean checkPassword(String plainPassword, String hashedPassword) {
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }

    /**
     * Método para registrar um novo usuário.
     * Criptografa a senha antes de enviar para o Supabase.
     * @param username O nome de usuário (ou email) do novo usuário.
     * @param plainPassword A senha em texto puro do novo usuário.
     * @return Response do Supabase ou erro.
     */
    public Response registerUser(String username, String plainPassword) {
        try {
            // 1. Criptografar a senha
            String hashedPassword = hashPassword(plainPassword);

            // 2. Criar o objeto JSON para enviar ao Supabase
            JsonObject userJson = Json.createObjectBuilder()
                    .add("username", username)
                    .add("password_hash", hashedPassword)
                    .build();

            // 3. Chamar o SupabaseService para criar o usuário
            // Assumindo que você terá um método createUser no SupabaseService que aceita JsonObject
            // ou um String que você pode converter para JSON string.
            // Para este exemplo, usaremos o método existente que aceita String.
            return supabaseService.createUser(userJson.toString());

        } catch (Exception e) {
            LOGGER.severe("Erro ao registrar usuário: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao registrar usuário").build();
        }
    }

    /**
     * Método para autenticar um usuário.
     * @param username O nome de usuário (ou email) fornecido.
     * @param plainPassword A senha em texto puro fornecida.
     * @return JsonObject com dados do usuário se a autenticação for bem-sucedida, null caso contrário.
     */
    public JsonObject authenticateUser(String username, String plainPassword) {
        try {
            // 1. Buscar o usuário pelo username no Supabase
            JsonObject user = supabaseService.getUserByUsername(username); // Novo método necessário!

            if (user == null) {
                LOGGER.info("Tentativa de login falhou: Usuário não encontrado - " + username);
                return null; // Usuário não encontrado
            }

            String storedPasswordHash = user.getString("password_hash");

            // 2. Verificar a senha
            if (checkPassword(plainPassword, storedPasswordHash)) {
                LOGGER.info("Login bem-sucedido para o usuário: " + username);
                // Retorne um subconjunto seguro dos dados do usuário, sem a hash da senha
                return Json.createObjectBuilder()
                        .add("id", user.getJsonNumber("id").longValue())
                        .add("username", user.getString("username"))
                        // Adicione outros campos que você queira retornar ao frontend (mas NUNCA a senha_hash)
                        .build();
            } else {
                LOGGER.info("Tentativa de login falhou: Senha incorreta para o usuário - " + username);
                return null; // Senha incorreta
            }
        } catch (Exception e) {
            LOGGER.severe("Erro durante a autenticação: " + e.getMessage());
            return null;
        }
    }
}