package com.ecolchain.api.common.web;

import io.quarkus.logging.Log;
import com.ecolchain.api.contract.model.ErrorResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Central exception mapping — the "ControllerAdvice" of this app.
 * Every error leaves as {@code {data: null, links: [], erros: [Problem]}}.
 */
@ApplicationScoped
public class GlobalExceptionMappers {

    @Inject
    ContainerRequestContext request;

    private String instance() {
        try {
            String path = request.getUriInfo().getPath();
            return path.startsWith("/") ? path : "/" + path;
        } catch (RuntimeException e) {
            return "";
        }
    }

    @ServerExceptionMapper
    public Response business(BusinessException e) {
        return Envelopes.error(e.status(), ProblemFactory.of(e, instance()));
    }

    @ServerExceptionMapper
    public Response validation(ConstraintViolationException e) {
        var errors = e.getConstraintViolations().stream()
                .map(v -> ProblemFactory.of(ErrorCodes.VALIDATION, 400,
                        ProblemFactory.titleFor(ErrorCodes.VALIDATION),
                        v.getMessage(), fieldOf(v), instance()))
                .toList();
        ErrorResponse body = new ErrorResponse();
        body.setLinks(java.util.List.of());
        body.setErros(errors.isEmpty()
                ? java.util.List.of(ProblemFactory.of(ErrorCodes.VALIDATION, 400,
                        ProblemFactory.titleFor(ErrorCodes.VALIDATION), "requisição inválida", null, instance()))
                : errors);
        return Response.status(400).entity(body).build();
    }

    private String fieldOf(ConstraintViolation<?> v) {
        String path = v.getPropertyPath() == null ? "" : v.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    @ServerExceptionMapper
    public Response notAuthorized(NotAuthorizedException e) {
        return Envelopes.error(401, ProblemFactory.of(ErrorCodes.AUTH_REQUIRED, 401,
                ProblemFactory.titleFor(ErrorCodes.AUTH_REQUIRED),
                "autenticação necessária", null, instance()));
    }

    @ServerExceptionMapper
    public Response forbidden(ForbiddenException e) {
        return Envelopes.error(403, ProblemFactory.of(ErrorCodes.FORBIDDEN, 403,
                ProblemFactory.titleFor(ErrorCodes.FORBIDDEN),
                "perfil sem acesso a este recurso", null, instance()));
    }

    @ServerExceptionMapper
    public Response notFound(NotFoundException e) {
        return Envelopes.error(404, ProblemFactory.of(ErrorCodes.NOT_FOUND, 404,
                ProblemFactory.titleFor(ErrorCodes.NOT_FOUND),
                "recurso não encontrado", null, instance()));
    }

    @ServerExceptionMapper
    public Response webApp(WebApplicationException e) {
        int status = e.getResponse() != null ? e.getResponse().getStatus() : 500;
        String code = status == 409 ? ErrorCodes.CONFLICT
                : status == 429 ? ErrorCodes.TOO_MANY_REQUESTS
                        : status >= 500 ? ErrorCodes.INTERNAL : ErrorCodes.VALIDATION;
        if (status >= 500) {
            Log.error("web application error", e);
        }
        return Envelopes.error(status, ProblemFactory.of(code, status,
                ProblemFactory.titleFor(code),
                e.getMessage() == null ? "erro na requisição" : e.getMessage(), null, instance()));
    }

    @ServerExceptionMapper
    public Response fallback(Throwable e) {
        Log.error("unhandled error", e);
        return Envelopes.error(500, ProblemFactory.of(ErrorCodes.INTERNAL, 500,
                "Erro interno", "erro inesperado; tente novamente", null, instance()));
    }
}
