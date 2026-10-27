package com.dms.secure_dms.web;

import com.dms.secure_dms.entity.DocumentType;
import com.dms.secure_dms.entity.User;
import com.dms.secure_dms.service.CaseManagementService;
import com.dms.secure_dms.service.CurrentUserService;
import com.dms.secure_dms.service.DocumentWorkflowService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OfficerController {

    private final CurrentUserService currentUserService;
    private final CaseManagementService caseManagementService;
    private final DocumentWorkflowService documentWorkflowService;

    public OfficerController(CurrentUserService currentUserService,
                             CaseManagementService caseManagementService,
                             DocumentWorkflowService documentWorkflowService) {
        this.currentUserService = currentUserService;
        this.caseManagementService = caseManagementService;
        this.documentWorkflowService = documentWorkflowService;
    }

    @GetMapping("/io")
    public String dashboard(Authentication authentication, Model model) {
        User actor = currentUserService.requireCurrentUser(authentication);
        model.addAttribute("user", actor);
        model.addAttribute("cases", caseManagementService.visibleCases(actor));
        model.addAttribute("documents", documentWorkflowService.visibleDocuments(actor));
        model.addAttribute("documentTypes", DocumentType.values());
        return "io";
    }

    @PostMapping("/io/documents")
    public String upload(Authentication authentication,
                         @RequestParam Long caseId,
                         @RequestParam String title,
                         @RequestParam DocumentType documentType,
                         @RequestParam MultipartFile file,
                         RedirectAttributes redirectAttributes) {
        try {
            User actor = currentUserService.requireCurrentUser(authentication);
            documentWorkflowService.createDraft(actor, caseId, title, documentType, file);
            redirectAttributes.addFlashAttribute("success", "Encrypted draft uploaded. Its SHA-256 fingerprint is recorded in the audit trail.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/io";
    }

    @PostMapping("/io/documents/{documentId}/versions")
    public String replaceDraft(Authentication authentication,
                               @PathVariable Long documentId,
                               @RequestParam MultipartFile file,
                               RedirectAttributes redirectAttributes) {
        try {
            User actor = currentUserService.requireCurrentUser(authentication);
            documentWorkflowService.replaceDraft(actor, documentId, file);
            redirectAttributes.addFlashAttribute("success", "A new immutable encrypted version was added.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/documents/" + documentId;
    }

    @PostMapping("/io/documents/{documentId}/submit")
    public String submit(Authentication authentication,
                         @PathVariable Long documentId,
                         RedirectAttributes redirectAttributes) {
        try {
            User actor = currentUserService.requireCurrentUser(authentication);
            documentWorkflowService.submitForReview(actor, documentId);
            redirectAttributes.addFlashAttribute("success", "Document submitted to the assigned Legal Reviewer.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/documents/" + documentId;
    }
}
