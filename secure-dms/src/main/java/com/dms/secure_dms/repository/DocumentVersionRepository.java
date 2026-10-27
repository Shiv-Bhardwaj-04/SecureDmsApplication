package com.dms.secure_dms.repository;

import com.dms.secure_dms.entity.DocumentVersion;
import com.dms.secure_dms.entity.SecureDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    Optional<DocumentVersion> findByDocumentAndVersionNumber(SecureDocument document, int versionNumber);

    @Query("""
            select v from DocumentVersion v
            join fetch v.createdBy
            where v.document = :document
            order by v.versionNumber desc
            """)
    List<DocumentVersion> findByDocumentOrderByVersionNumberDesc(@Param("document") SecureDocument document);

    long countByDocument(SecureDocument document);
}
