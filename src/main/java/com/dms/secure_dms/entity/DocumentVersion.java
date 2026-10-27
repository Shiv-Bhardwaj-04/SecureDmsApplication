package com.dms.secure_dms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Immutable metadata for one encrypted file version. */
@Entity
@Table(name = "document_version", uniqueConstraints = @UniqueConstraint(
        name = "uk_document_version_number", columnNames = {"document_id", "version_number"}))
@Getter
@Setter
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private SecureDocument document;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false)
    private long byteSize;

    /** SHA-256 of the unencrypted source bytes, used as the evidence fingerprint. */
    @Column(nullable = false, length = 64)
    private String sha256;

    /** Random opaque name; no client-controlled path is ever stored here. */
    @Column(nullable = false, unique = true, length = 80)
    private String storageKey;

    /** Base64-encoded AES-GCM IV needed only to decrypt this specific blob. */
    @Column(nullable = false, length = 32)
    private String encryptionIv;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
