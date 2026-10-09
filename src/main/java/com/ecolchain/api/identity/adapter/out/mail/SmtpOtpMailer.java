package com.ecolchain.api.identity.adapter.out.mail;

import com.ecolchain.api.identity.application.port.out.OtpMailer;
import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Envia o código OTP por e-mail (OCI Email Delivery). Nunca loga o código. */
@ApplicationScoped
public class SmtpOtpMailer implements OtpMailer {

    @Inject Mailer mailer;
    @Inject Template otp;

    @Override
    public void send(String to, String code, String lang) {
        boolean en = "en".equals(lang);
        String subject = en ? "Your ECOLchain sign-in code" : "Seu código de acesso ECOLchain";
        String html = otp.data("code", code, "lang", lang).render();
        try {
            mailer.send(Mail.withHtml(to, subject, html));
        } catch (RuntimeException e) {
            // falha de envio não pode vazar existência da conta: loga sem código
            Log.errorf("falha ao enviar OTP para %s", mask(to));
            throw e;
        }
        Log.infof("OTP enviado para %s", mask(to));
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***@" + email.substring(at + 1);
    }
}
