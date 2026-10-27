package com.dms.secure_dms.repository;

import com.dms.secure_dms.entity.DocumentStatus;
import com.dms.secure_dms.entity.SecureDocument;
import com.dms.secure_dms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface SecureDocumentRepository extends JpaRepository<SecureDocument, Long> {

    @Query("""
            select d from SecureDocument d
            join fetch d.caseFile c
            join fetch d.uploadedBy u
            left join fetch d.reviewedBy rb
            left join fetch d.currentVersion cv
            where c.investigatingOfficer = :officer
            order by d.updatedAt desc
            """)
    List<SecureDocument> findVisibleToOfficer(@Param("officer") User officer);

    @Query("""
            select d from SecureDocument d
            join fetch d.caseFile c
            join fetch d.uploadedBy u
            left join fetch d.reviewedBy rb
            left join fetch d.currentVersion cv
            where c.reviewer = :reviewer
            order by d.updatedAt desc
            """)
    List<SecureDocument> findVisibleToReviewer(@Param("reviewer") User reviewer);

    @Query("""
            select d from SecureDocument d
            join fetch d.caseFile c
            join fetch d.uploadedBy u
            left join fetch d.reviewedBy rb
            left join fetch d.currentVersion cv
            order by d.updatedAt desc
            """)
    List<SecureDocument> findAllWithDetails();

    @Query("""
            select d from SecureDocument d
            join fetch d.caseFile c
            join fetch d.uploadedBy u
            left join fetch d.reviewedBy rb
            left join fetch d.currentVersion cv
            where c.reviewer = :reviewer and d.status = :status
            order by d.submittedAt asc
            """)
    List<SecureDocument> findReviewQueue(@Param("reviewer") User reviewer,
                                         @Param("status") DocumentStatus status);

    @Query("""
            select d from SecureDocument d
            join fetch d.caseFile c
            join fetch d.uploadedBy u
            left join fetch d.reviewedBy rb
            left join fetch d.currentVersion cv
            where d.id = :id
            """)
    Optional<SecureDocument> findWithDetailsById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SecureDocument d where d.id = :id")
    Optional<SecureDocument> findByIdForUpdate(@Param("id") Long id);
}
