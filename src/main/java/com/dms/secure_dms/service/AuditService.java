package com.dms.secure_dms.service;

import com.dms.secure_dms.entity.AuditAction;
import com.dms.secure_dms.entity.AuditChainState;
import com.dms.secure_dms.entity.AuditEvent;
import com.dms.secure_dms.entity.User;
import com.dms.secure_dms.repository.AuditChainStateRepository;
import com.dms.secure_dms.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/** Appends and verifies the application's tamper-evident, HMAC hash-chained audit history. */
@Service
public class AuditService {

    public static final String GENESIS_HASH = "0".repeat(64);
    private static final long CHAIN_STATE_ID = 1L;

    private final AuditEventRepository auditEventRepository;
    private final AuditChainStateRepository auditChainStateRepository;
    private final CryptoKeyService cryptoKeyService;

    public AuditService(AuditEventRepository auditEventRepository,
                        AuditChainStateRepository auditChainStateRepository,
                        CryptoKeyService cryptoKeyService) {
        this.auditEventRepository = auditEventRepository;
        this.auditChainStateRepository = auditChainStateRepository;
        this.cryptoKeyService = cryptoKeyService;
    }

    @Transactional
    public void initializeChain() {
        if (auditChainStateRepository.existsById(CHAIN_STATE_ID)) {
            return;
        }
        AuditChainState state = new AuditChainState();
        state.setId(CHAIN_STATE_ID);
        state.setLastEventHash(GENESIS_HASH);
        state.setEventCount(0);
        auditChainStateRepository.save(state);
    }

    @Transactional
    public AuditEvent record(User actor, AuditAction action, Long caseFileId, Long documentId,
                             Integer documentVersionNumber, String details) {
        AuditChainState state = auditChainStateRepository.findByIdForUpdate(CHAIN_STATE_ID)
                .orElseThrow(() -> new IllegalStateException("Audit chain state is not initialized"));

        AuditEvent event = new AuditEvent();
        // TIMESTAMP(6) is used by MySQL/Hibernate, so sign the precision that is
        // actually persisted rather than nanoseconds that would be truncated.
        event.setOccurredAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        event.setActorId(actor.getId());
        event.setActorEmail(actor.getEmail());
        event.setAction(action);
        event.setCaseFileId(caseFileId);
        event.setDocumentId(documentId);
        event.setDocumentVersionNumber(documentVersionNumber);
        event.setDetails(normalizeDetails(details));
        event.setPreviousHash(state.getLastEventHash());
        event.setEventHash(sign(event));

        AuditEvent saved = auditEventRepository.save(event);
        state.setLastEventHash(saved.getEventHash());
        state.setEventCount(state.getEventCount() + 1);
        return saved;
    }

    @Transactional(readOnly = true)
    public AuditIntegrityReport verifyIntegrity() {
        List<AuditEvent> events = auditEventRepository.findAllByOrderByIdAsc();
        String expectedPreviousHash = GENESIS_HASH;

        for (AuditEvent event : events) {
            if (!MessageDigest.isEqual(expectedPreviousHash.getBytes(StandardCharsets.US_ASCII),
                    event.getPreviousHash().getBytes(StandardCharsets.US_ASCII))) {
                return new AuditIntegrityReport(false, events.size(), "Broken previous-hash link at audit event #" + event.getId());
            }
            String expectedHash = sign(event);
            if (!MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                    event.getEventHash().getBytes(StandardCharsets.US_ASCII))) {
                return new AuditIntegrityReport(false, events.size(), "HMAC verification failed at audit event #" + event.getId());
            }
            expectedPreviousHash = event.getEventHash();
        }

        AuditChainState state = auditChainStateRepository.findById(CHAIN_STATE_ID).orElse(null);
        if (state == null || state.getEventCount() != events.size()
                || !MessageDigest.isEqual(expectedPreviousHash.getBytes(StandardCharsets.US_ASCII),
                state.getLastEventHash().getBytes(StandardCharsets.US_ASCII))) {
            return new AuditIntegrityReport(false, events.size(), "Audit chain checkpoint does not match the event history");
        }
        return new AuditIntegrityReport(true, events.size(), "All event links and HMAC signatures are valid");
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> recentEvents() {
        return auditEventRepository.findTop100ByOrderByIdDesc();
    }

    private String sign(AuditEvent event) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cryptoKeyService.auditHmacKey(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(event).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign audit event", exception);
        }
    }

    private String canonical(AuditEvent event) {
        return String.join("\n",
                "secure-dms-audit-v1",
                value(event.getOccurredAt()),
                value(event.getActorId()),
                encoded(event.getActorEmail()),
                value(event.getAction()),
                value(event.getCaseFileId()),
                value(event.getDocumentId()),
                value(event.getDocumentVersionNumber()),
                encoded(event.getDetails()),
                value(event.getPreviousHash()));
    }

    private String normalizeDetails(String details) {
        String normalized = details == null ? "" : details.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 1_000) {
            throw new IllegalArgumentException("Audit details exceed the permitted length");
        }
        return normalized;
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }
}
