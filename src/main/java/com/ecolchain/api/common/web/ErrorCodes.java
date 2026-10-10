package com.ecolchain.api.common.web;

/**
 * Stable machine-readable error codes carried in {@code erros[].code}.
 * The SPA translates messages by code, never by text — keep values stable.
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    // generic / envelope
    public static final String VALIDATION = "VALIDATION";
    public static final String AUTH_REQUIRED = "AUTH_REQUIRED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String INTERNAL = "INTERNAL_ERROR";
    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";

    // auth
    public static final String OTP_INVALID = "OTP_INVALID";
    public static final String OTP_RATE_LIMITED = "OTP_RATE_LIMITED";
    public static final String REFRESH_INVALID = "REFRESH_INVALID";
    public static final String REFRESH_REUSED = "REFRESH_REUSED";
    public static final String ACCOUNT_BLOCKED = "ACCOUNT_BLOCKED";
    public static final String ACCOUNT_IN_USE = "ACCOUNT_IN_USE";

    // company
    public static final String CNPJ_INVALID = "CNPJ_INVALID";
    public static final String CNPJ_IN_USE = "CNPJ_IN_USE";
    public static final String PROFILE_TYPE_INVALID = "PROFILE_TYPE_INVALID";
    public static final String TERMS_REQUIRED = "TERMS_REQUIRED";
    public static final String DECLARATION_REQUIRED = "DECLARATION_REQUIRED";
    public static final String COMPANY_LOCKED = "COMPANY_LOCKED";
    public static final String COMPANY_NOT_FOUND = "COMPANY_NOT_FOUND";
    public static final String COMPANY_EXISTS = "COMPANY_EXISTS";

    // catalog / onboarding
    public static final String ATTRIBUTE_INVALID = "ATTRIBUTE_INVALID";
    public static final String ATTRIBUTE_NOT_IN_FLOW = "ATTRIBUTE_NOT_IN_FLOW";
    public static final String ATTRIBUTE_VALUE_INVALID = "ATTRIBUTE_VALUE_INVALID";
    public static final String SUBMIT_INCOMPLETE = "SUBMIT_INCOMPLETE";
    public static final String PROFILE_TYPE_IN_USE = "PROFILE_TYPE_IN_USE";

    // documents
    public static final String DOCUMENT_INVALID = "DOCUMENT_INVALID";
    public static final String DOCUMENT_NOT_CONFIRMED = "DOCUMENT_NOT_CONFIRMED";
    public static final String DOCUMENT_UPLOAD_FAILED = "DOCUMENT_UPLOAD_FAILED";

    // review
    public static final String REVIEW_INVALID_STATE = "REVIEW_INVALID_STATE";
    public static final String REVIEW_NOTE_REQUIRED = "REVIEW_NOTE_REQUIRED";
    public static final String REVIEW_ITEM_INVALID = "REVIEW_ITEM_INVALID";
}
