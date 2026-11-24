package org.openhomeo.core.model;

/**
 * Detailed taxonomical classification of a homeopathic remedy across:
 * - Kingdom (Plant, Mineral, Animal, Nosode, Sarcode, Fungi, Imponderabilia)
 * - Subkingdom / Phylum / Division (e.g. Angiospermae, Chordata, Arthropoda)
 * - Order / Periodic Series (e.g. Ranunculales, Solanales, Series 4 Ferrum)
 * - Family / Periodic Stage (e.g. Ranunculaceae, Solanaceae, Stage 10)
 * - Sankaran Vital Sensation / Miasmatic Affinity
 */
public record FamilyTaxonomy(
        String remedyId,
        String remedyName,
        String kingdom,
        String subKingdom,
        String orderOrSeries,
        String familyOrStage,
        String miasmPrimary,
        String miasmSecondary,
        String sensationTheme,
        String clinicalKeynote
) {}
