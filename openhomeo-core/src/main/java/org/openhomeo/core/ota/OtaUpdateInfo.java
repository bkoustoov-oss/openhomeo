package org.openhomeo.core.ota;

import java.io.Serializable;

/**
 * Top-level metadata record for available Over-The-Air updates.
 */
public record OtaUpdateInfo(
        boolean updateAvailable,
        String currentVersion,
        String latestVersion,
        String releaseDate,
        int totalRemedies,
        String changelog,
        String safetyNotice
) implements Serializable {}
