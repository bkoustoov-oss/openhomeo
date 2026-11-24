package org.openhomeo.core.ota;

import java.io.Serializable;

/**
 * Robust, top-level result record for Over-The-Air (OTA) updates.
 * Independent of inner-class compilation or synthetic accessor quirks.
 */
public record OtaApplyResult(
        boolean success,
        String version,
        String message,
        String executionLog,
        String hash
) implements Serializable {
    public OtaApplyResult(boolean success, String version, String message) {
        this(success, version, message, message, "N/A");
    }
}
