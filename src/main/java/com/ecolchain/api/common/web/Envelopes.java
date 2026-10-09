package com.ecolchain.api.common.web;

import com.ecolchain.api.contract.model.Envelope;
import com.ecolchain.api.contract.model.ErrorResponse;
import com.ecolchain.api.contract.model.Link;
import com.ecolchain.api.contract.model.Problem;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Envelope helpers: every response is {@code {data, links, erros}}.
 * Controllers fill {@code data} on the generated *Response model;
 * these helpers cover the empty/error cases and link shortcuts.
 */
public final class Envelopes {

    private Envelopes() {
    }

    public static Link link(String rel, String href, String metodo) {
        Link l = new Link();
        l.setRel(rel);
        l.setHref(href);
        l.setMetodo(metodo);
        return l;
    }

    public static Response error(int status, Problem problem) {
        ErrorResponse body = new ErrorResponse();
        body.setLinks(List.of());
        body.setErros(List.of(problem));
        return Response.status(status).entity(body).build();
    }
}
