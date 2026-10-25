package com.rolelens.api.job.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv")
public class Cv {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_sha256", nullable = false,
            unique = true, columnDefinition = "bytea")
    private byte[] fileSha256;

    @Column(name = "page_count", nullable = false)
    private short pageCount;

    @Column(name = "raw_text", nullable = false,
            columnDefinition = "text")
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "links", nullable = false, columnDefinition = "jsonb")
    private JsonNode links = JsonNodeFactory.instance.arrayNode();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ats_findings", nullable = false,
            columnDefinition = "jsonb")
    private JsonNode atsFindings = JsonNodeFactory.instance.arrayNode();

    private String language;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    protected Cv() {}

    public Cv(String fileName, byte[] fileSha256,
              int pageCount, String rawText) {
        this.fileName = fileName;
        this.fileSha256 = fileSha256;
        this.pageCount = (short) pageCount;
        this.rawText = rawText;
        this.uploadedAt = OffsetDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = OffsetDateTime.now();
        }
    }

    public void touchUploadedAt() {
        uploadedAt = OffsetDateTime.now();
    }

    public void updateFindings(JsonNode findings) {
        atsFindings = findings;
    }

    public UUID getId() { return id; }
    public String getFileName() { return fileName; }
    public byte[] getFileSha256() { return fileSha256; }
    public short getPageCount() { return pageCount; }
    public String getRawText() { return rawText; }
    public JsonNode getLinks() { return links; }
    public JsonNode getAtsFindings() { return atsFindings; }
    public String getLanguage() { return language; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
}
