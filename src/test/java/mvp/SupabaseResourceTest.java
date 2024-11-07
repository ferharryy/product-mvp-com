package mvp;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class SupabaseResourceTest {

    @Test
    public void testSaveMessageEndpoint() {
        String messageJson = "{\"messageContent\": \"Mensagem de teste\", \"timestamp\": \"2024-10-28T15:30:00Z\"}";

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(messageJson)
                .when()
                .post("/rest/v1/messages")
                .then()
                .statusCode(200); // Ou o código esperado caso dê erro
    }

    @Test
    public void testSaveWorkItemEndpoint() {
        String workItemJson = "{\"workItemId\": 456, \"description\": \"Descrição de teste do work item\"}";

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(workItemJson)
                .when()
                .post("/rest/v1/work-items")
                .then()
                .statusCode(200); // Ou o código esperado caso dê erro
    }
}

