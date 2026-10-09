package com.ecolchain.api;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Path("/documents")
@IfBuildProfile("dev")
@Produces(MediaType.APPLICATION_JSON)
public class DevDocumentResource {

    @Inject
    DocumentStorageService storage;

    @GET
    @Path("/upload-url")
    public DocumentStorageService.PresignedUpload uploadUrl(@QueryParam("filename") String filename,
            @QueryParam("contentType") String contentType,
            @QueryParam("contentLength") long contentLength) {
        return storage.presignUpload(filename, contentType, contentLength);
    }

    @GET
    @Path("/{key:.+}/download-url")
    public DocumentStorageService.PresignedDownload downloadUrl(@RestPath String key) {
        return storage.presignDownload(key);
    }

    @ServerExceptionMapper
    public Response mapInvalid(InvalidDocumentException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
