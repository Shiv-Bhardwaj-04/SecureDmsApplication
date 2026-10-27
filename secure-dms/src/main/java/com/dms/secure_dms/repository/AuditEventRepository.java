package com.dms.secure_dms.repository;

import com.dms.secure_dms.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findAllByOrderByIdAsc();

    List<AuditEvent> findTop100ByOrderByIdDesc();
}
