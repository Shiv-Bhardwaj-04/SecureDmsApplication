package com.dms.secure_dms.service;

import com.dms.secure_dms.entity.CaseFile;
import com.dms.secure_dms.entity.Role;
import com.dms.secure_dms.entity.SecureDocument;
import com.dms.secure_dms.entity.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Enforces the case-level rules that route-based role checks alone cannot express. */
@Service
public class CaseAccessService {

    public void requireRole(User actor, Role requiredRole) {
        if (actor.getRole() != requiredRole) {
            throw new AccessDeniedException("This action is not permitted for the current role");
        }
    }

    public void requireCanReadCase(User actor, CaseFile caseFile) {
        if (actor.getRole() == Role.ADMIN || actor.getRole() == Role.AUDITOR) {
            return;
        }
        if (actor.getRole() == Role.IO && sameUser(actor, caseFile.getInvestigatingOfficer())) {
            return;
        }
        if (actor.getRole() == Role.REVIEWER && sameUser(actor, caseFile.getReviewer())) {
            return;
        }
        throw new AccessDeniedException("You are not assigned to this case");
    }

    public void requireCanWorkOnCase(User actor, CaseFile caseFile) {
        requireRole(actor, Role.IO);
        if (!sameUser(actor, caseFile.getInvestigatingOfficer())) {
            throw new AccessDeniedException("You are not assigned to this case");
        }
    }

    public void requireCanReviewDocument(User actor, SecureDocument document) {
        requireRole(actor, Role.REVIEWER);
        if (!sameUser(actor, document.getCaseFile().getReviewer())) {
            throw new AccessDeniedException("You are not assigned to review this case");
        }
    }

    public void requireCanReadDocument(User actor, SecureDocument document) {
        requireCanReadCase(actor, document.getCaseFile());
    }

    private boolean sameUser(User first, User second) {
        return first != null && second != null && first.getId() != null && first.getId().equals(second.getId());
    }
}
