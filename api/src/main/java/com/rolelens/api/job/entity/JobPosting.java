package com.rolelens.api.job.entity;

import com.rolelens.api.job.enums.ExtractionStatus;
import com.rolelens.api.job.enums.Source;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_posting")
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private Source source;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "content_hash", nullable = false, unique = true, columnDefinition = "BYTEA")
    private byte[] contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 50)
    private ExtractionStatus extractionStatus = ExtractionStatus.PENDING;

    // Mapping PostgreSQL JSONB using Hibernate's Json type code
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted", columnDefinition = "jsonb")
    private String extracted; // Can also be mapped to a custom Map<String, Object> or DTO class

    @Column(name = "title")
    private String title;

    @Column(name = "company")
    private String company;

    @Column(name = "language")
    private String language;

    @Column(name = "location")
    private String location;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "schema_version")
    private String schemaVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public JobPosting(Source source, String rawText, String sourceUrl) {
        this.source = source;
        this.rawText = rawText;
        this.sourceUrl = sourceUrl;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    // Constructors, Getters, and Setters
    public JobPosting() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public byte[] getContentHash() { return contentHash; }
    public void setContentHash(byte[] contentHash) { this.contentHash = contentHash; }

    public ExtractionStatus getExtractionStatus() { return extractionStatus; }
    public void setExtractionStatus(ExtractionStatus extractionStatus) { this.extractionStatus = extractionStatus; }

    public String getExtracted() { return extracted; }
    public void setExtracted(String extracted) { this.extracted = extracted; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

}
