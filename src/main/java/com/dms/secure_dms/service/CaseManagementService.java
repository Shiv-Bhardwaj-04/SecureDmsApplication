package com.dms.secure_dms.service;

import com.dms.secure_dms.entity.AuditAction;
import com.dms.secure_dms.entity.CaseFile;
import com.dms.secure_dms.entity.CaseStatus;
import com.dms.secure_dms.entity.Role;
import com.dms.secure_dms.entity.SensitivityLevel;
import com.dms.secure_dms.entity.User;
import com.dms.secure_dms.repository.CaseFileRepository;
import com.dms.secure_dms.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class CaseManagementService {

    private final CaseFileRepository caseFileRepository;
    private final UserRepository userRepository;
    private final CaseAccessService caseAccessService;
    private final AuditService auditService;

    public CaseManagementService(CaseFileRepository caseFileRepository,
                                 UserRepository userRepository,
                                 CaseAccessService caseAccessService,
                                 AuditService auditService) {
        this.caseFileRepository = caseFileRepository;
        this.userRepository = userRepository;
        this.caseAccessService = caseAccessService;
        this.auditService = auditService;
    }

    @Transactional
    public CaseFile createCase(User actor, String caseNumber, String title, String description,
                               SensitivityLevel sensitivityLevel, Long officerId, Long reviewerId) {
        caseAccessService.requireRole(actor, Role.ADMIN);
        String normalizedCaseNumber = normalizeCaseNumber(caseNumber);
        String normalizedTitle = requireText(title, "Case title", 200);
        String normalizedDescription = optionalText(description, 2_000);

        if (sensitivityLevel == null) {
            throw new IllegalArgumentException("Choose a sensitivity level");
        }
        if (caseFileRepository.findByCaseNumberIgnoreCase(normalizedCaseNumber).isPresent()) {
            throw new IllegalArgumentException("That case number already exists");
        }

        User officer = userRepository.findById(officerId == null ? -1L : officerId)
                .orElseThrow(() -> new IllegalArgumentException("Choose a valid Investigating Officer"));
        User reviewer = userRepository.findById(reviewerId == null ? -1L : reviewerId)
                .orElseThrow(() -> new IllegalArgumentException("Choose a valid Legal Reviewer"));
        if (officer.getRole() != Role.IO || reviewer.getRole() != Role.REVIEWER) {
            throw new IllegalArgumentException("Case assignments must use an IO and a Legal Reviewer");
        }

        CaseFile caseFile = new CaseFile();
        caseFile.setCaseNumber(normalizedCaseNumber);
        caseFile.setTitle(normalizedTitle);
        caseFile.setDescription(normalizedDescription);
        caseFile.setSensitivityLevel(sensitivityLevel);
        caseFile.setStatus(CaseStatus.ACTIVE);
        caseFile.setInvestigatingOfficer(officer);
        caseFile.setReviewer(reviewer);

        CaseFile saved = caseFileRepository.save(caseFile);
        auditService.record(actor, AuditAction.CASE_CREATED, saved.getId(), null, null,
                "case=" + normalizedCaseNumber + "; sensitivity=" + sensitivityLevel + "; assignedIO=" + officer.getId()
                        + "; assignedReviewer=" + reviewer.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CaseFile> visibleCases(User actor) {
        return switch (actor.getRole()) {
            case ADMIN, AUDITOR -> caseFileRepository.findAllByOrderByUpdatedAtDesc();
            case IO -> caseFileRepository.findByInvestigatingOfficerOrderByUpdatedAtDesc(actor);
            case REVIEWER -> caseFileRepository.findByReviewerOrderByUpdatedAtDesc(actor);
        };
    }

    private String normalizeCaseNumber(String caseNumber) {
        String normalized = requireText(caseNumber, "Case number", 64).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9/_-]{2,63}")) {
            throw new IllegalArgumentException("Case number may use letters, numbers, slash, underscore, and hyphen");
        }
        return normalized;
    }

    private String requireText(String value, String label, int maxLength) {
        String normalized = optionalText(value, maxLength);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Text exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
