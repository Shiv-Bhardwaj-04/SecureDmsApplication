package com.dms.secure_dms.repository;

import com.dms.secure_dms.entity.CaseFile;
import com.dms.secure_dms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface CaseFileRepository extends JpaRepository<CaseFile, Long> {

    Optional<CaseFile> findByCaseNumberIgnoreCase(String caseNumber);

    @EntityGraph(attributePaths = {"investigatingOfficer", "reviewer"})
    List<CaseFile> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"investigatingOfficer", "reviewer"})
    List<CaseFile> findByInvestigatingOfficerOrderByUpdatedAtDesc(User investigatingOfficer);

    @EntityGraph(attributePaths = {"investigatingOfficer", "reviewer"})
    List<CaseFile> findByReviewerOrderByUpdatedAtDesc(User reviewer);
}
