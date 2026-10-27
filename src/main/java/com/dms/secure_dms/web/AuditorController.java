package com.dms.secure_dms.web;

import com.dms.secure_dms.entity.User;
import com.dms.secure_dms.service.AuditService;
import com.dms.secure_dms.service.CurrentUserService;
import com.dms.secure_dms.service.DocumentWorkflowService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuditorController {

    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final DocumentWorkflowService documentWorkflowService;

    public AuditorController(CurrentUserService currentUserService,
                             AuditService auditService,
                             DocumentWorkflowService documentWorkflowService) {
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.documentWorkflowService = documentWorkflowService;
    }

    @GetMapping("/auditor")
    public String dashboard(Authentication authentication, Model model) {
        User actor = currentUserService.requireCurrentUser(authentication);
        model.addAttribute("user", actor);
        model.addAttribute("integrity", auditService.verifyIntegrity());
        model.addAttribute("events", auditService.recentEvents());
        model.addAttribute("documents", documentWorkflowService.visibleDocuments(actor));
        return "auditor";
    }
}
