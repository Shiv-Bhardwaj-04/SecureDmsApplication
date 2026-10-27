package com.dms.secure_dms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** One locked row serializes creation of the global audit hash chain. */
@Entity
@Getter
@Setter
public class AuditChainState {

    @Id
    private Long id;

    @Column(nullable = false, length = 64)
    private String lastEventHash;

    @Column(nullable = false)
    private long eventCount;

    @Version
    private Long entityVersion;
}
