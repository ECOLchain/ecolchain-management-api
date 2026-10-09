package com.ecolchain.api.common.i18n;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.inject.Inject;
import java.util.Locale;

/**
 * Resolves the response language for labels: {@code Accept-Language}
 * picks {@code en} when it explicitly prefers English; default pt-BR.
 */
@RequestScoped
public class RequestLocale {

    public static final String PT = "pt";
    public static final String EN = "en";

    @Inject
    HttpHeaders headers;

    public String lang() {
        var langs = headers.getAcceptableLanguages();
        if (langs == null || langs.isEmpty()) {
            return PT;
        }
        Locale best = langs.get(0);
        return best != null && best.getLanguage().startsWith(EN) ? EN : PT;
    }

    public boolean isEn() {
        return EN.equals(lang());
    }
}
