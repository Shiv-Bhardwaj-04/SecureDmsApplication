package com.dms.secure_dms.service;

/** Result shown to an auditor after independently recalculating the audit chain. */
public record AuditIntegrityReport(boolean intact, long eventsChecked, String message) {
}
