package com.dms.secure_dms.service;

import com.dms.secure_dms.entity.AuditAction;
import com.dms.secure_dms.entity.CaseFile;
import com.dms.secure_dms.entity.DocumentComment;
import com.dms.secure_dms.entity.DocumentStatus;
import com.dms.secure_dms.entity.DocumentType;
import com.dms.secure_dms.entity.DocumentVersion;
import com.dms.secure_dms.entity.Role;
import com.dms.secure_dms.entity.SecureDocument;
import com.dms.secure_dms.entity.User;
import com.dms.secure_dms.repository.CaseFileRepository;
import com.dms.secure_dms.repository.DocumentCommentRepository;
import com.dms.secure_dms.repository.DocumentVersionRepository;
import com.dms.secure_dms.repository.SecureDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class DocumentWorkflowService {

    private final CaseFileRepository caseFileRepository;
    private final SecureDocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentCommentRepository documentCommentRepository;
    private final CaseAccessService caseAccessService;
    private final EncryptedFileStorageService encryptedFileStorageService;
    private final AuditService auditService;

    public DocumentWorkflowService(CaseFileRepository caseFileRepository,
                                   SecureDocumentRepository documentRepository,
                                   DocumentVersionRepository documentVersionRepository,
                                   DocumentCommentRepository documentCommentRepository,
                                   CaseAccessService caseAccessService,
                                   EncryptedFileStorageService encryptedFileStorageService,
                                   AuditService auditService) {
        this.caseFileRepository = caseFileRepository;
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentCommentRepository = documentCommentRepository;
        this.caseAccessService = caseAccessService;
        this.encryptedFileStorageService = encryptedFileStorageService;
        this.auditService = auditService;
    }

    @Transactional
    public SecureDocument createDraft(User actor, Long caseId, String title, DocumentType documentType, MultipartFile upload) {
        caseAccessService.requireRole(actor, Role.IO);
        CaseFile caseFile = caseFileRepository.findById(caseId == null ? -1L : caseId)
                .orElseThrow(() -> new IllegalArgumentException("Choose a case assigned to you"));
        caseAccessService.requireCanWorkOnCase(actor, caseFile);

        String normalizedTitle = requireText(title, "Document title", 200);
        if (documentType == null) {
            throw new IllegalArgumentException("Choose a document type");
        }

        StoredFile storedFile = encryptedFileStorageService.store(upload);
        try {
            SecureDocument document = new SecureDocument();
            document.setCaseFile(caseFile);
            document.setTitle(normalizedTitle);
            document.setDocumentType(documentType);
            document.setStatus(DocumentStatus.DRAFT);
            document.setUploadedBy(actor);
            SecureDocument savedDocument = documentRepository.saveAndFlush(document);

            DocumentVersion version = createVersion(savedDocument, actor, 1, storedFile);
            documentVersionRepository.save(version);
            savedDocument.setCurrentVersion(version);
            documentRepository.save(savedDocument);

            auditService.record(actor, AuditAction.DOCUMENT_UPLOADED, caseFile.getId(), savedDocument.getId(), 1,
                    "type=" + documentType + "; sha256=" + storedFile.sha256() + "; bytes=" + storedFile.byteSize());
            return savedDocument;
        } catch (RuntimeException exception) {
            encryptedFileStorageService.discard(storedFile.storageKey());
            throw exception;
        }
    }

    @Transactional
    public void replaceDraft(User actor, Long documentId, MultipartFile upload) {
        SecureDocument document = lockedDocument(documentId);
        caseAccessService.requireCanWorkOnCase(actor, document.getCaseFile());
        if (!sameUser(actor, document.getUploadedBy())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the submitting officer can add a new version");
        }
        if (document.getStatus() != DocumentStatus.DRAFT && document.getStatus() != DocumentStatus.REVISION_REQUIRED) {
            throw new IllegalStateException("Only a draft or returned document can receive a new version");
        }

        StoredFile storedFile = encryptedFileStorageService.store(upload);
        try {
            int nextVersion = Math.toIntExact(documentVersionRepository.countByDocument(document) + 1);
            DocumentVersion version = createVersion(document, actor, nextVersion, storedFile);
            documentVersionRepository.save(version);
            document.setCurrentVersion(version);
            document.setStatus(DocumentStatus.DRAFT);
            document.setSubmittedAt(null);
            document.setReviewedAt(null);
            document.setReviewedBy(null);

            auditService.record(actor, AuditAction.DOCUMENT_VERSION_ADDED, document.getCaseFile().getId(), document.getId(),
                    nextVersion, "sha256=" + storedFile.sha256() + "; bytes=" + storedFile.byteSize());
        } catch (RuntimeException exception) {
            encryptedFileStorageService.discard(storedFile.storageKey());
            throw exception;
        }
    }

    @Transactional
    public void submitForReview(User actor, Long documentId) {
        SecureDocument document = lockedDocument(documentId);
        caseAccessService.requireCanWorkOnCase(actor, document.getCaseFile());
        if (!sameUser(actor, document.getUploadedBy())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the submitting officer can submit this document");
        }
        if (document.getStatus() != DocumentStatus.DRAFT && document.getStatus() != DocumentStatus.REVISION_REQUIRED) {
            throw new IllegalStateException("Only a draft or revised document can be submitted for review");
        }
        if (document.getCurrentVersion() == null) {
            throw new IllegalStateException("A document must have a file version before review");
        }

        document.setStatus(DocumentStatus.UNDER_REVIEW);
        document.setSubmittedAt(Instant.now());
        auditService.record(actor, AuditAction.DOCUMENT_SUBMITTED, document.getCaseFile().getId(), document.getId(),
                document.getCurrentVersion().getVersionNumber(), "status=UNDER_REVIEW");
    }

    @Transactional
    public void approve(User actor, Long documentId, String comment) {
        SecureDocument document = lockedDocument(documentId);
        requireReviewableBy(actor, document);
        addCommentIfPresent(document, actor, comment);

        document.setStatus(DocumentStatus.APPROVED);
        document.setReviewedBy(actor);
        document.setReviewedAt(Instant.now());
        auditService.record(actor, AuditAction.DOCUMENT_APPROVED, document.getCaseFile().getId(), document.getId(),
                document.getCurrentVersion().getVersionNumber(), "status=APPROVED");
    }

    @Transactional
    public void returnForRevision(User actor, Long documentId, String comment) {
        SecureDocument document = lockedDocument(documentId);
        requireReviewableBy(actor, document);
        String requiredComment = requireText(comment, "A revision note", 2_000);
        addComment(document, actor, requiredComment);

        document.setStatus(DocumentStatus.REVISION_REQUIRED);
        document.setReviewedBy(actor);
        document.setReviewedAt(Instant.now());
        auditService.record(actor, AuditAction.DOCUMENT_RETURNED_FOR_REVISION, document.getCaseFile().getId(), document.getId(),
                document.getCurrentVersion().getVersionNumber(), "status=REVISION_REQUIRED");
    }

    @Transactional
    public DocumentDetails details(User actor, Long documentId) {
        SecureDocument document = documentRepository.findWithDetailsById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        caseAccessService.requireCanReadDocument(actor, document);
        List<DocumentVersion> versions = documentVersionRepository.findByDocumentOrderByVersionNumberDesc(document);
        List<DocumentComment> comments = documentCommentRepository.findByDocumentOrderByCreatedAtAsc(document);
        boolean canReplace = actor.getRole() == Role.IO
                && sameUser(actor, document.getUploadedBy())
                && (document.getStatus() == DocumentStatus.DRAFT || document.getStatus() == DocumentStatus.REVISION_REQUIRED);
        boolean canReview = actor.getRole() == Role.REVIEWER
                && sameUser(actor, document.getCaseFile().getReviewer())
                && document.getStatus() == DocumentStatus.UNDER_REVIEW;

        auditService.record(actor, AuditAction.DOCUMENT_VIEWED, document.getCaseFile().getId(), document.getId(),
                document.getCurrentVersion() == null ? null : document.getCurrentVersion().getVersionNumber(), "metadata-view");
        return new DocumentDetails(document, versions, comments, canReplace, canReview);
    }

    @Transactional
    public DownloadableDocument download(User actor, Long documentId, int versionNumber) {
        SecureDocument document = documentRepository.findWithDetailsById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        caseAccessService.requireCanReadDocument(actor, document);
        DocumentVersion version = documentVersionRepository.findByDocumentAndVersionNumber(document, versionNumber)
                .orElseThrow(() -> new IllegalArgumentException("Document version not found"));

        auditService.record(actor, AuditAction.DOCUMENT_DOWNLOADED, document.getCaseFile().getId(), document.getId(),
                version.getVersionNumber(), "sha256=" + version.getSha256());
        return new DownloadableDocument(version.getOriginalFilename(), version.getContentType(), version.getByteSize(),
                encryptedFileStorageService.open(version));
    }

    @Transactional(readOnly = true)
    public List<SecureDocument> visibleDocuments(User actor) {
        return switch (actor.getRole()) {
            case IO -> documentRepository.findVisibleToOfficer(actor);
            case REVIEWER -> documentRepository.findVisibleToReviewer(actor);
            case ADMIN, AUDITOR -> documentRepository.findAllWithDetails();
        };
    }

    @Transactional(readOnly = true)
    public List<SecureDocument> reviewQueue(User reviewer) {
        caseAccessService.requireRole(reviewer, Role.REVIEWER);
        return documentRepository.findReviewQueue(reviewer, DocumentStatus.UNDER_REVIEW);
    }

    private SecureDocument lockedDocument(Long documentId) {
        return documentRepository.findByIdForUpdate(documentId == null ? -1L : documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    private void requireReviewableBy(User actor, SecureDocument document) {
        caseAccessService.requireCanReviewDocument(actor, document);
        if (document.getStatus() != DocumentStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Only a submitted document can be reviewed");
        }
        if (document.getCurrentVersion() == null) {
            throw new IllegalStateException("Document has no current file version");
        }
    }

    private DocumentVersion createVersion(SecureDocument document, User actor, int versionNumber, StoredFile storedFile) {
        DocumentVersion version = new DocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(versionNumber);
        version.setOriginalFilename(storedFile.originalFilename());
        version.setContentType(storedFile.contentType());
        version.setByteSize(storedFile.byteSize());
        version.setSha256(storedFile.sha256());
        version.setStorageKey(storedFile.storageKey());
        version.setEncryptionIv(storedFile.encryptionIv());
        version.setCreatedBy(actor);
        return version;
    }

    private void addCommentIfPresent(SecureDocument document, User actor, String comment) {
        if (comment != null && !comment.trim().isEmpty()) {
            addComment(document, actor, requireText(comment, "Review comment", 2_000));
        }
    }

    private void addComment(SecureDocument document, User actor, String comment) {
        DocumentComment documentComment = new DocumentComment();
        documentComment.setDocument(document);
        documentComment.setAuthor(actor);
        documentComment.setMessage(comment);
        documentCommentRepository.save(documentComment);
        auditService.record(actor, AuditAction.REVIEW_COMMENT_ADDED, document.getCaseFile().getId(), document.getId(),
                document.getCurrentVersion().getVersionNumber(), "commentSha256=" + sha256(comment));
    }

    private String requireText(String value, String label, int maximumLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(label + " exceeds " + maximumLength + " characters");
        }
        return normalized;
    }

    private boolean sameUser(User first, User second) {
        return first != null && second != null && first.getId() != null && first.getId().equals(second.getId());
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
