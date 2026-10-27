package com.dms.secure_dms.repository;

import com.dms.secure_dms.entity.DocumentComment;
import com.dms.secure_dms.entity.SecureDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentCommentRepository extends JpaRepository<DocumentComment, Long> {

    @Query("""
            select c from DocumentComment c
            join fetch c.author
            where c.document = :document
            order by c.createdAt asc
            """)
    List<DocumentComment> findByDocumentOrderByCreatedAtAsc(@Param("document") SecureDocument document);
}
