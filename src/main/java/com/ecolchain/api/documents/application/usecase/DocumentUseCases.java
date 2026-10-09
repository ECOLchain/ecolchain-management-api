package com.ecolchain.api.documents.application.usecase;

import com.ecolchain.api.InvalidDocumentException;
import com.ecolchain.api.catalog.application.port.out.CatalogStore;
import com.ecolchain.api.catalog.domain.AttributeDef;
import com.ecolchain.api.common.web.BusinessException;
import com.ecolchain.api.common.web.ErrorCodes;
import com.ecolchain.api.documents.application.port.out.DocumentStore;
import com.ecolchain.api.documents.application.port.out.ObjectStorage;
import com.ecolchain.api.documents.domain.CompanyDocument;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.onboarding.application.port.out.AuditStore;
import com.ecolchain.api.onboarding.application.port.out.CompanyStore;
import com.ecolchain.api.onboarding.application.usecase.GetOnboardingUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;

/**
 * Upload direto ao bucket: POST upload-url (cria intenção DOCUMENT), confirm (HEAD no S3),
 * download-url (leitura pelo dono/admin). docs/features/documentos-upload.md
 */
@ApplicationScoped
public class DocumentUseCases {

    public record UploadUrl(String url, String objectKey, long expiresIn, String contentType, long sizeBytes) {}
    public record DownloadUrl(String url, String objectKey, long expiresIn) {}

    @Inject CompanyStore companies;
    @Inject CatalogStore catalog;
    @Inject DocumentStore documents;
    @Inject ObjectStorage storage;
    @Inject GetOnboardingUseCase onboarding;
    @Inject AuditStore audit;

    private com.ecolchain.api.onboarding.domain.Company editableCompanyOf(Account account) {
        var c = companies.byMemberAccountId(account.id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.COMPANY_NOT_FOUND, 404, "empresa não cadastrada"));
        if (!c.editable()) {
            throw new BusinessException(ErrorCodes.COMPANY_LOCKED, 409, "cadastro em análise não pode ser alterado");
        }
        return c;
    }

    private AttributeDef documentAttrInFlow(Account account, String code) {
        var def = catalog.attributeByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCodes.DOCUMENT_INVALID, 404, "documento desconhecido"));
        if (def.kind != AttributeDef.Kind.DOCUMENT) {
            throw new BusinessException(ErrorCodes.DOCUMENT_INVALID, 400, "atributo não é documento");
        }
        var inFlow = onboarding.execute(account).steps().stream()
                .flatMap(s -> s.attributes().stream())
                .anyMatch(a -> a.def().id.equals(def.id));
        if (!inFlow) {
            throw new BusinessException(ErrorCodes.ATTRIBUTE_NOT_IN_FLOW, 400, "documento não faz parte do seu fluxo");
        }
        return def;
    }

    @Transactional
    public UploadUrl createUploadUrl(Account account, String code, String fileName, String contentType, long sizeBytes) {
        var company = editableCompanyOf(account);
        var def = documentAttrInFlow(account, code);
        try {
            var presigned = storage.presignUpload(fileName, contentType, sizeBytes);
            var doc = documents.byCompanyAndAttribute(company.id, def.id).orElseGet(CompanyDocument::new);
            doc.companyId = company.id;
            doc.attributeId = def.id;
            doc.objectKey = presigned.objectKey();
            doc.fileName = fileName;
            doc.contentType = contentType;
            doc.sizeBytes = sizeBytes;
            doc.status = CompanyDocument.Status.PENDING_UPLOAD;
            doc.reviewNote = null;
            doc.uploadedAt = null;
            documents.save(doc);
            audit.log(account.id, account.email, "DOCUMENT_UPLOAD_URL", "company",
                    company.id.toString(), code + " " + presigned.objectKey());
            return new UploadUrl(presigned.url(), presigned.objectKey(), presigned.expiresInSeconds(),
                    presigned.contentType(), sizeBytes);
        } catch (InvalidDocumentException e) {
            throw new BusinessException(ErrorCodes.DOCUMENT_INVALID, 400, e.getMessage());
        }
    }

    @Transactional
    public void confirmUpload(Account account, String code, String objectKey) {
        var company = editableCompanyOf(account);
        var def = documentAttrInFlow(account, code);
        var doc = documents.byCompanyAndAttribute(company.id, def.id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.DOCUMENT_NOT_CONFIRMED, 404,
                        "nenhum upload iniciado para este documento"));
        if (!doc.objectKey.equals(objectKey) || !storage.exists(objectKey)) {
            throw new BusinessException(ErrorCodes.DOCUMENT_UPLOAD_FAILED, 400,
                    "objeto não encontrado no storage; refaça o upload");
        }
        doc.status = CompanyDocument.Status.UPLOADED;
        doc.uploadedAt = Instant.now();
        documents.save(doc);
        audit.log(account.id, account.email, "DOCUMENT_CONFIRMED", "company", company.id.toString(), code);
    }

    public DownloadUrl myDownloadUrl(Account account, String code) {
        var company = companies.byMemberAccountId(account.id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.COMPANY_NOT_FOUND, 404, "empresa não cadastrada"));
        var def = catalog.attributeByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCodes.DOCUMENT_INVALID, 404, "documento desconhecido"));
        var doc = documents.byCompanyAndAttribute(company.id, def.id)
                .filter(d -> d.status == CompanyDocument.Status.UPLOADED
                        || d.status == CompanyDocument.Status.ACCEPTED)
                .orElseThrow(() -> new BusinessException(ErrorCodes.DOCUMENT_NOT_CONFIRMED, 404,
                        "documento não enviado"));
        var p = storage.presignDownload(doc.objectKey);
        return new DownloadUrl(p.url(), p.objectKey(), p.expiresInSeconds());
    }

    public DownloadUrl adminDownloadUrl(UUID companyId, String code) {
        var def = catalog.attributeByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCodes.DOCUMENT_INVALID, 404, "documento desconhecido"));
        var doc = documents.byCompanyAndAttribute(companyId, def.id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.DOCUMENT_NOT_CONFIRMED, 404,
                        "documento não enviado"));
        var p = storage.presignDownload(doc.objectKey);
        return new DownloadUrl(p.url(), p.objectKey(), p.expiresInSeconds());
    }
}
