package com.dms.secure_dms.web;

import com.dms.secure_dms.entity.Role;
import com.dms.secure_dms.entity.SensitivityLevel;
import com.dms.secure_dms.entity.User;
import com.dms.secure_dms.repository.UserRepository;
import com.dms.secure_dms.service.CaseManagementService;
import com.dms.secure_dms.service.CurrentUserService;
import com.dms.secure_dms.service.DocumentWorkflowService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminController {

    private final CurrentUserService currentUserService;
    private final CaseManagementService caseManagementService;
    private final DocumentWorkflowService documentWorkflowService;
    private final UserRepository userRepository;

    public AdminController(CurrentUserService currentUserService,
                           CaseManagementService caseManagementService,
                           DocumentWorkflowService documentWorkflowService,
                           UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.caseManagementService = caseManagementService;
        this.documentWorkflowService = documentWorkflowService;
        this.userRepository = userRepository;
    }

    @GetMapping("/admin")
    public String dashboard(Authentication authentication, Model model) {
        User actor = currentUserService.requireCurrentUser(authentication);
        model.addAttribute("user", actor);
        model.addAttribute("cases", caseManagementService.visibleCases(actor));
        model.addAttribute("documents", documentWorkflowService.visibleDocuments(actor));
        model.addAttribute("officers", userRepository.findByRoleOrderByFullNameAsc(Role.IO));
        model.addAttribute("reviewers", userRepository.findByRoleOrderByFullNameAsc(Role.REVIEWER));
        model.addAttribute("sensitivityLevels", SensitivityLevel.values());
        return "admin";
    }

    @PostMapping("/admin/cases")
    public String createCase(Authentication authentication,
                             @RequestParam String caseNumber,
                             @RequestParam String title,
                             @RequestParam(required = false) String description,
                             @RequestParam SensitivityLevel sensitivityLevel,
                             @RequestParam Long officerId,
                             @RequestParam Long reviewerId,
                             RedirectAttributes redirectAttributes) {
        try {
            User actor = currentUserService.requireCurrentUser(authentication);
            caseManagementService.createCase(actor, caseNumber, title, description, sensitivityLevel, officerId, reviewerId);
            redirectAttributes.addFlashAttribute("success", "Case created and securely assigned.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin";
    }
}
