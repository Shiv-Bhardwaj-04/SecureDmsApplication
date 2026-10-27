package com.dms.secure_dms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Append-only application audit record. eventHash is an HMAC over this event and
 * the prior hash, which makes changes to the stored history detectable.
 */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(nullable = false, updatable = false)
    private Long actorId;

    @Column(nullable = false, updatable = false, length = 254)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private AuditAction action;

    @Column(updatable = false)
    private Long caseFileId;

    @Column(updatable = false)
    private Long documentId;

    @Column(updatable = false)
    private Integer documentVersionNumber;

    @Column(nullable = false, updatable = false, length = 1_000)
    private String details;

    @Column(nullable = false, updatable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, updatable = false, length = 64)
    private String eventHash;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
