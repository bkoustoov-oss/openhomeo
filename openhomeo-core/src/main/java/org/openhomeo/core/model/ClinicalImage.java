package org.openhomeo.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Domain record for clinical color photographs (lesions, tongue, skin).
 */
public record ClinicalImage(
        String id,
        String patientId,
        String consultationId,
        String contentHash,        // SHA-256 hash for content-addressable storage
        String fileFormat,         // "PNG", "JPEG", "TIFF"
        String bodyPart,           // "Tongue", "Face", "Dorsum of right hand"
        String colorProfile,       // "sRGB", "D65_Standardized"
        Double meanLabL,           // CIE-L* Lightness (0-100)
        Double meanLabA,           // CIE-a* Red-Green axis (positive = erythema/redness)
        Double meanLabB,           // CIE-b* Yellow-Blue axis
        Double deltaEBaseline,     // CIEDE2000 difference from initial visit baseline
        String notes,
        Instant capturedAt,
        List<ImageAnnotation> annotations
) {}
