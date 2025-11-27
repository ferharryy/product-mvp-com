package mvp.exception;

import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import mvp.dto.ErrorResponse;

@Provider
public class WebhookExceptionMapper implements ExceptionMapper<WebhookException> {

    @Override
    public Response toResponse(WebhookException ex) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Payload inválido", ex.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
