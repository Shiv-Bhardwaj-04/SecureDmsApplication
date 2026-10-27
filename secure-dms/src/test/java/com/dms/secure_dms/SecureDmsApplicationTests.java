package com.dms.secure_dms;

import com.dms.secure_dms.entity.CaseFile;
import com.dms.secure_dms.entity.DocumentStatus;
import com.dms.secure_dms.entity.DocumentType;
import com.dms.secure_dms.entity.Role;
import com.dms.secure_dms.entity.SensitivityLevel;
import com.dms.secure_dms.entity.SecureDocument;
import com.dms.secure_dms.entity.User;
import com.dms.secure_dms.repository.DocumentVersionRepository;
import com.dms.secure_dms.repository.UserRepository;
import com.dms.secure_dms.service.AuditService;
import com.dms.secure_dms.service.CaseManagementService;
import com.dms.secure_dms.service.DocumentWorkflowService;
import com.dms.secure_dms.service.DownloadableDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SecureDmsApplicationTests {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CaseManagementService caseManagementService;

	@Autowired
	private DocumentWorkflowService documentWorkflowService;

	@Autowired
	private DocumentVersionRepository documentVersionRepository;

	@Autowired
	private AuditService auditService;

	@Test
	void contextLoads() {
	}

	@Test
	void preservesVersionsAndAuditIntegrityThroughTheReviewWorkflow() throws Exception {
		User admin = userRepository.findByEmail("anita@dms.gov.in").orElseThrow();
		User officer = userRepository.findByEmail("priya@dms.gov.in").orElseThrow();
		User reviewer = userRepository.findByEmail("karan@dms.gov.in").orElseThrow();
		assertEquals(Role.ADMIN, admin.getRole());

		CaseFile caseFile = caseManagementService.createCase(admin, "FIR/TEST/001", "Workflow integration test",
				"Test case", SensitivityLevel.CONFIDENTIAL, officer.getId(), reviewer.getId());
		byte[] original = "Initial witness statement".getBytes(StandardCharsets.UTF_8);
		SecureDocument document = documentWorkflowService.createDraft(officer, caseFile.getId(), "Witness statement",
				DocumentType.WITNESS_STATEMENT,
				new MockMultipartFile("file", "statement.txt", "text/plain", original));

		DownloadableDocument downloaded = documentWorkflowService.download(officer, document.getId(), 1);
		try (var input = downloaded.inputStream()) {
			assertArrayEquals(original, input.readAllBytes());
		}

		documentWorkflowService.submitForReview(officer, document.getId());
		documentWorkflowService.returnForRevision(reviewer, document.getId(), "Please add the interview date.");
		documentWorkflowService.replaceDraft(officer, document.getId(),
				new MockMultipartFile("file", "statement-v2.txt", "text/plain",
						"Revised witness statement with interview date".getBytes(StandardCharsets.UTF_8)));
		documentWorkflowService.submitForReview(officer, document.getId());
		documentWorkflowService.approve(reviewer, document.getId(), "Legally reviewed.");

		SecureDocument completed = documentWorkflowService.details(admin, document.getId()).document();
		assertEquals(DocumentStatus.APPROVED, completed.getStatus());
		assertEquals(2, documentVersionRepository.countByDocument(completed));
		var integrity = auditService.verifyIntegrity();
		assertTrue(integrity.intact(), integrity.message());
	}

}
