package org.openhomeo.core.model;

import java.time.Instant;

/**
 * Domain record representing an audited Over-The-Air (OTA) system patch,
 * knowledge base upgrade, or non-destructive database migration on the client machine.
 */
public record SystemPatchRecord(
        String id,
        String version,
        String patchType,          // e.g. "FULL_SYSTEM_UPGRADE", "KNOWLEDGE_BASE_HOTFIX", "SCHEMA_MIGRATION"
        String releaseDate,
        String repertoryHash,      // SHA-256 integrity hash
        String status,             // "APPLIED_SUCCESS", "ROLLED_BACK", "FAILED"
        String appliedBy,          // Username or practitioner role who triggered update
        String changelogSummary,   // Summary of feature and knowledge additions
        String executionLog,       // Detailed step-by-step trace of actions executed
        Instant appliedAt
) {
}
