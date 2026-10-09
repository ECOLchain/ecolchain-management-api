package com.ecolchain.api.common.web;

/**
 * Domain/application rule violation. Carries the stable {@code code},
 * the HTTP status and an optional field name ({@code campo}) so the
 * mapper can build a Problem Details item without knowing the context.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final int status;
    private final String campo;

    public BusinessException(String code, int status, String detail) {
        this(code, status, detail, null);
    }

    public BusinessException(String code, int status, String detail, String campo) {
        super(detail);
        this.code = code;
        this.status = status;
        this.campo = campo;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public String campo() {
        return campo;
    }
}
