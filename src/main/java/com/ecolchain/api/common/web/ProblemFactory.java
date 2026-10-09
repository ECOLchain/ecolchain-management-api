package com.ecolchain.api.common.web;

import com.ecolchain.api.contract.model.Problem;

/**
 * Builds {@link Problem} items (RFC 9457 + extensions {@code code}/{@code campo}).
 * Centralized so the {@code type} URN convention never drifts.
 */
public final class ProblemFactory {

    private static final String URN_PREFIX = "urn:ecolchain:problem:";

    private ProblemFactory() {
    }

    public static Problem of(String code, int status, String title, String detail, String campo,
            String instance) {
        Problem p = new Problem();
        p.setType(URN_PREFIX + slug(code));
        p.setTitle(title);
        p.setStatus(status);
        p.setDetail(detail);
        p.setInstance(instance == null ? "" : instance);
        p.setCode(code);
        p.setCampo(campo);
        return p;
    }

    public static Problem of(BusinessException e, String instance) {
        return of(e.code(), e.status(), titleFor(e.code()), e.getMessage(), e.campo(), instance);
    }

    private static String slug(String code) {
        return code.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    /** Short title per code; detail carries the specifics. */
    public static String titleFor(String code) {
        return switch (code) {
            case ErrorCodes.VALIDATION -> "Dados inválidos";
            case ErrorCodes.AUTH_REQUIRED -> "Autenticação necessária";
            case ErrorCodes.FORBIDDEN -> "Acesso negado";
            case ErrorCodes.NOT_FOUND -> "Recurso não encontrado";
            case ErrorCodes.CONFLICT -> "Conflito de estado";
            case ErrorCodes.TOO_MANY_REQUESTS -> "Muitas requisições";
            case ErrorCodes.OTP_INVALID -> "Código inválido";
            case ErrorCodes.OTP_RATE_LIMITED -> "Limite de envio de código";
            case ErrorCodes.REFRESH_INVALID -> "Sessão expirada";
            case ErrorCodes.REFRESH_REUSED -> "Sessão comprometida";
            case ErrorCodes.ACCOUNT_BLOCKED -> "Conta bloqueada";
            case ErrorCodes.CNPJ_INVALID -> "CNPJ inválido";
            case ErrorCodes.CNPJ_IN_USE -> "CNPJ já cadastrado";
            case ErrorCodes.PROFILE_TYPE_INVALID -> "Tipo de perfil inválido";
            case ErrorCodes.TERMS_REQUIRED -> "Termos não aceitos";
            case ErrorCodes.DECLARATION_REQUIRED -> "Declaração obrigatória";
            case ErrorCodes.COMPANY_LOCKED -> "Empresa já submetida";
            case ErrorCodes.COMPANY_NOT_FOUND -> "Empresa não encontrada";
            case ErrorCodes.COMPANY_EXISTS -> "Empresa já cadastrada";
            case ErrorCodes.ATTRIBUTE_INVALID -> "Atributo inválido";
            case ErrorCodes.ATTRIBUTE_NOT_IN_FLOW -> "Atributo fora do fluxo";
            case ErrorCodes.ATTRIBUTE_VALUE_INVALID -> "Valor inválido";
            case ErrorCodes.SUBMIT_INCOMPLETE -> "Onboarding incompleto";
            case ErrorCodes.PROFILE_TYPE_IN_USE -> "Tipo em uso";
            case ErrorCodes.DOCUMENT_INVALID -> "Documento inválido";
            case ErrorCodes.DOCUMENT_NOT_CONFIRMED -> "Documento não confirmado";
            case ErrorCodes.DOCUMENT_UPLOAD_FAILED -> "Falha no upload";
            case ErrorCodes.REVIEW_INVALID_STATE -> "Estado não revisável";
            case ErrorCodes.REVIEW_NOTE_REQUIRED -> "Motivo obrigatório";
            case ErrorCodes.REVIEW_ITEM_INVALID -> "Item de revisão inválido";
            default -> "Erro";
        };
    }
}
