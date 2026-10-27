package com.dms.secure_dms.web;

import com.dms.secure_dms.entity.User;
import com.dms.secure_dms.service.CurrentUserService;
import com.dms.secure_dms.service.DocumentDetails;
import com.dms.secure_dms.service.DocumentWorkflowService;
import com.dms.secure_dms.service.DownloadableDocument;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@Controller
public class DocumentController {

    private final CurrentUserService currentUserService;
    private final DocumentWorkflowService documentWorkflowService;

    public DocumentController(CurrentUserService currentUserService, DocumentWorkflowService documentWorkflowService) {
        this.currentUserService = currentUserService;
        this.documentWorkflowService = documentWorkflowService;
    }

    @GetMapping("/documents/{documentId}")
    public String details(Authentication authentication, @PathVariable Long documentId, Model model) {
        User actor = currentUserService.requireCurrentUser(authentication);
        DocumentDetails details = documentWorkflowService.details(actor, documentId);
        model.addAttribute("user", actor);
        model.addAttribute("details", details);
        return "document-detail";
    }

    @GetMapping("/documents/{documentId}/versions/{versionNumber}/download")
    public ResponseEntity<StreamingResponseBody> download(Authentication authentication,
                                                           @PathVariable Long documentId,
                                                           @PathVariable int versionNumber) {
        User actor = currentUserService.requireCurrentUser(authentication);
        DownloadableDocument document = documentWorkflowService.download(actor, documentId, versionNumber);
        MediaType mediaType = safeMediaType(document.contentType());
        StreamingResponseBody body = outputStream -> {
            try (var inputStream = document.inputStream()) {
                inputStream.transferTo(outputStream);
            }
        };
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(document.byteSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private MediaType safeMediaType(String value) {
        try {
            MediaType parsed = MediaType.parseMediaType(value);
            return parsed.isConcrete() ? parsed : MediaType.APPLICATION_OCTET_STREAM;
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
