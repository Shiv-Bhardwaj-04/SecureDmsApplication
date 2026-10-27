package com.dms.secure_dms.web;

import com.dms.secure_dms.entity.User;
import com.dms.secure_dms.service.CurrentUserService;
import com.dms.secure_dms.service.DocumentWorkflowService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReviewerController {

    private final CurrentUserService currentUserService;
    private final DocumentWorkflowService documentWorkflowService;

    public ReviewerController(CurrentUserService currentUserService, DocumentWorkflowService documentWorkflowService) {
        this.currentUserService = currentUserService;
        this.documentWorkflowService = documentWorkflowService;
    }

    @GetMapping("/reviewer")
    public String dashboard(Authentication authentication, Model model) {
        User actor = currentUserService.requireCurrentUser(authentication);
        model.addAttribute("user", actor);
        model.addAttribute("reviewQueue", documentWorkflowService.reviewQueue(actor));
        model.addAttribute("documents", documentWorkflowService.visibleDocuments(actor));
        return "reviewer";
    }

    @PostMapping("/reviewer/documents/{documentId}/approve")
    public String approve(Authentication authentication,
                          @PathVariable Long documentId,
                          @RequestParam(required = false) String comment,
                          RedirectAttributes redirectAttributes) {
        try {
            User actor = currentUserService.requireCurrentUser(authentication);
            documentWorkflowService.approve(actor, documentId, comment);
            redirectAttributes.addFlashAttribute("success", "Document approved and locked from further changes.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/documents/" + documentId;
    }

    @PostMapping("/reviewer/documents/{documentId}/return")
    public String returnForRevision(Authentication authentication,
                                    @PathVariable Long documentId,
                                    @RequestParam String comment,
                                    RedirectAttributes redirectAttributes) {
        try {
            User actor = currentUserService.requireCurrentUser(authentication);
            documentWorkflowService.returnForRevision(actor, documentId, comment);
            redirectAttributes.addFlashAttribute("success", "Document returned to the Investigating Officer with a tracked revision note.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/documents/" + documentId;
    }
}
