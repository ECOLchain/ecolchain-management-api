package com.ecolchain.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DocumentStorageServiceTest {

    private static final String ENDPOINT = "https://grmykw8bmw9d.compat.objectstorage.sa-saopaulo-1.oraclecloud.com";
    private static final String BUCKET = "ecolchain-bucket-docs-test";

    @Inject
    DocumentStorageService storage;

    @Inject
    DocumentsConfig cfg;

    private String filenameOf(DocumentStorageService.PresignedUpload u) {
        return u.key().substring("docs/".length() + 36 + 1);
    }

    @Test
    void sanitizesSpacesAndLowercasesExtension() {
        var u = storage.presignUpload("My Doc.PDF", "application/pdf", 10);
        assertEquals("My-Doc.pdf", filenameOf(u));
    }

    @Test
    void stripsDirectoryPart() {
        var u = storage.presignUpload("a/b/c.pdf", "application/pdf", 10);
        assertEquals("c.pdf", filenameOf(u));
        var w = storage.presignUpload("C:\\temp\\relatorio.txt", "text/plain", 10);
        assertEquals("relatorio.txt", filenameOf(w));
    }

    @Test
    void keepsBaseCaseAndLowercasesExtensionOnly() {
        var u = storage.presignUpload("FOTO.JPEG", "image/jpeg", 10);
        assertEquals("FOTO.jpeg", filenameOf(u));
    }

    @Test
    void removesDisallowedCharsAndCollapsesRepeats() {
        var u = storage.presignUpload("meu  arquivo@#$.png", "image/png", 10);
        assertEquals("meu-arquivo.png", filenameOf(u));
    }

    @Test
    void rejectsDisallowedExtension() {
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignUpload("evil.exe", "application/octet-stream", 10));
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignUpload("noext", "text/plain", 10));
    }

    @Test
    void rejectsNullBlankAndDotOnlyNames() {
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignUpload(null, "text/plain", 10));
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignUpload("   ", "text/plain", 10));
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignUpload("...", "text/plain", 10));
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignUpload("..", "text/plain", 10));
    }

    @Test
    void traversalIsReducedToLeafName() {
        var u = storage.presignUpload("../../etc/x.txt", "text/plain", 10);
        assertEquals("x.txt", filenameOf(u));
    }

    @Test
    void rejectsContentTypeMismatch() {
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignUpload("a.pdf", "text/plain", 10));
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignUpload("a.pdf", null, 10));
    }

    @Test
    void rejectsBadContentLength() {
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignUpload("a.pdf", "application/pdf", 0));
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignUpload("a.pdf", "application/pdf", -5));
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignUpload("a.pdf", "application/pdf", cfg.maxSizeBytes() + 1));
    }

    @Test
    void capsSanitizedNameAtHundredChars() {
        String longName = "a".repeat(200) + ".txt";
        var u = storage.presignUpload(longName, "text/plain", 10);
        String name = filenameOf(u);
        assertTrue(name.length() <= 100);
        assertTrue(name.endsWith(".txt"));
    }

    @Test
    void uploadUrlHasExpectedShape() {
        var u = storage.presignUpload("a.txt", "text/plain", 10);
        assertTrue(u.url().startsWith(ENDPOINT + "/" + BUCKET + "/docs/"), u.url());
        String query = URLDecoder.decode(u.url().substring(u.url().indexOf('?') + 1), StandardCharsets.UTF_8);
        assertTrue(query.contains("X-Amz-Signature="), u.url());
        assertTrue(query.contains("X-Amz-Expires=600"), u.url());
        assertTrue(query.contains("X-Amz-SignedHeaders="), u.url());
        String signedHeaders = query.substring(query.indexOf("X-Amz-SignedHeaders="));
        assertTrue(signedHeaders.contains("content-length"), u.url());
        assertTrue(signedHeaders.contains("content-type"), u.url());
        assertEquals(10, u.contentLength());
        assertEquals("text/plain", u.contentType());
    }

    @Test
    void downloadUrlHasExpectedShape() {
        String key = "docs/" + UUID.randomUUID() + "-a.txt";
        var d = storage.presignDownload(key);
        assertTrue(d.url().startsWith(ENDPOINT + "/" + BUCKET + "/docs/"), d.url());
        String query = URLDecoder.decode(d.url().substring(d.url().indexOf('?') + 1), StandardCharsets.UTF_8);
        assertTrue(query.contains("X-Amz-Signature="), d.url());
        assertTrue(query.contains("X-Amz-Expires=300"), d.url());
    }

    @Test
    void rejectsMalformedDownloadKeys() {
        assertThrows(InvalidDocumentException.class, () -> storage.presignDownload("foo/bar"));
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignDownload("docs/../x"));
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignDownload("docs/plainname"));
        assertThrows(InvalidDocumentException.class,
                () -> storage.presignDownload("docs/" + UUID.randomUUID() + "-x.txt/../y"));
        assertThrows(InvalidDocumentException.class, () -> storage.presignDownload(null));
    }
}
