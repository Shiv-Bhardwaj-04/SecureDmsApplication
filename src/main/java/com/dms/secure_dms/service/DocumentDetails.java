package com.dms.secure_dms.service;

import com.dms.secure_dms.entity.DocumentComment;
import com.dms.secure_dms.entity.DocumentVersion;
import com.dms.secure_dms.entity.SecureDocument;

import java.util.List;

/** Fully initialized document data suitable for the read-only document detail page. */
public record DocumentDetails(
        SecureDocument document,
        List<DocumentVersion> versions,
        List<DocumentComment> comments,
        boolean canReplace,
        boolean canReview
) {
}
