package com.ecolchain.api.onboarding.application.usecase;

import com.ecolchain.api.onboarding.domain.Company;

/** próximoPasso calculado no servidor (proposta §5). */
public enum NextStep {
    COMPANY_FORM, ONBOARDING, AWAITING_REVIEW, HOME, REJECTED, SUSPENDED;

    public static NextStep of(Company company) {
        if (company == null) return COMPANY_FORM;
        return switch (company.status) {
            case PENDING_DOCUMENTS, CHANGES_REQUESTED, PENDING_UPDATE -> ONBOARDING;
            case UNDER_REVIEW -> AWAITING_REVIEW;
            case APPROVED -> HOME;
            case REJECTED -> REJECTED;
            case SUSPENDED -> SUSPENDED;
        };
    }
}
