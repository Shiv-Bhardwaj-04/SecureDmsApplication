package com.dms.secure_dms.repository;

import com.dms.secure_dms.entity.AuditChainState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuditChainStateRepository extends JpaRepository<AuditChainState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from AuditChainState state where state.id = :id")
    Optional<AuditChainState> findByIdForUpdate(@Param("id") Long id);
}
