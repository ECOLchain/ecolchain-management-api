package com.ecolchain.api.onboarding.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Company {
    public enum Status {
        PENDING_DOCUMENTS, UNDER_REVIEW, CHANGES_REQUESTED,
        APPROVED, REJECTED, SUSPENDED, PENDING_UPDATE
    }

    public UUID id;
    public String cnpj;
    public String razaoSocial;
    public Status status;
    public String reviewNotes;
    public String termsVersion;
    public Instant termsAcceptedAt;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant submittedAt;
    public List<String> profileTypeCodes = List.of();

    public boolean editable() {
        return status == Status.PENDING_DOCUMENTS || status == Status.CHANGES_REQUESTED
                || status == Status.PENDING_UPDATE;
    }
}
