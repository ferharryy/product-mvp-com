package mvp.resources;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import mvp.service.SupabaseService;

@Path("/rest/v1")
public class SupabaseResource {

    @Inject
    SupabaseService supabaseService;

    @POST
    @Path("/messages")
    public Response saveMessage(String messageJson) {
        return supabaseService.saveMessage(messageJson);
    }

    @POST
    @Path("/work-items")
    public Response saveWorkItem(String workItemJson) {
        return supabaseService.saveWorkItem(workItemJson);
    }
}
