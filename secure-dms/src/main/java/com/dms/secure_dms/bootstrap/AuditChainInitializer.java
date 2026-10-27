package com.dms.secure_dms.bootstrap;

import com.dms.secure_dms.service.AuditService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditChainInitializer implements CommandLineRunner {

    private final AuditService auditService;

    public AuditChainInitializer(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void run(String... args) {
        auditService.initializeChain();
    }
}
