package com.ecolchain.api.identity.adapter.in.web;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.List;
import java.util.Map;

/**
 * Caixa de saída do mailer mockado — ferramenta de dev/E2E (%dev/%test apenas,
 * habilitada por app.dev-tools.mailbox-enabled). Nunca loga código em produção.
 */
@Path("/dev-mailbox")
@Produces(MediaType.APPLICATION_JSON)
public class DevMailboxController {

    @Inject MockMailbox mailbox;

    @ConfigProperty(name = "app.dev-tools.mailbox-enabled", defaultValue = "false")
    boolean enabled;

    @GET
    public Response list(@QueryParam("to") String to) {
        if (!enabled) {
            return Response.status(404).build();
        }
        if (to == null || to.isBlank()) {
            return Response.ok(Map.of("total", mailbox.getTotalMessagesSent(),
                    "hint", "use ?to=<email>")).build();
        }
        List<Mail> mails = mailbox.getMessagesSentTo(to.toLowerCase());
        var out = mails.stream().map(m -> Map.of(
                "to", m.getTo() == null ? List.of() : m.getTo(),
                "subject", String.valueOf(m.getSubject()),
                "html", String.valueOf(m.getHtml()))).toList();
        return Response.ok(Map.of("mails", out)).build();
    }

    @GET
    @Path("/clear")
    public Response clear() {
        if (!enabled) {
            return Response.status(404).build();
        }
        mailbox.clear();
        return Response.ok(Map.of("cleared", true)).build();
    }
}
