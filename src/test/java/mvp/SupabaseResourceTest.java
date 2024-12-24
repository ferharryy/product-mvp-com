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
    public void testSaveMessageEndpoint() {}

    @Test
    public void testSaveWorkItemEndpoint() {}

}

