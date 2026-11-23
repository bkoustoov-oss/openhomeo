package org.openhomeo.core.materia_medica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.model.MedicineConstituent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Community Proving Ingestion Pipeline (Strategic Recommendation 1).
 * Imports standardized custom, newly proven, or institution-tested (e.g. CCRH)
 * homeopathic proving monographs from JSON or CSV into the local clinic database.
 * Strictly maintains English and standardized Latin pharmacopoeial nomenclature.
 */
public class ProvingIngestionService {
    private static final Logger log = LoggerFactory.getLogger(ProvingIngestionService.class);
    private final ClinicDao clinicDao;
    private final ObjectMapper objectMapper;

    public record IngestionResult(
            boolean success,
            String remedyId,
            String remedyName,
            int sectionsIngested,
            String message
    ) {}

    public ProvingIngestionService(ClinicDao clinicDao) {
        this.clinicDao = clinicDao;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Ingests a structured JSON proving monograph.
     * Expected schema:
     * {
     *   "remedy_id": "with-ind",
     *   "remedy_name": "Withania somnifera (CCRH Proving)",
     *   "common_name": "Ashwagandha / Indian Ginseng",
     *   "prover_director": "Central Council for Research in Homoeopathy (CCRH)",
     *   "proving_year": 2024,
     *   "active_constituents": "Withanolides, withaferin A, withanone",
     *   "pharmacological_action": "Modulates HPA axis, reduces elevated cortisol...",
     *   "organ_affinity": "Central nervous system, adrenal cortex",
     *   "sections": {
     *     "MIND": "Great mental fatigue from intellectual overwork...",
     *     "HEAD": "Dull heavy ache in occiput...",
     *     "GENERALITIES": "Weakness worse physical exertion...",
     *     "MODALITIES": "Better warm drinks, worse 3 to 4 AM"
     *   }
     * }
     */
    public IngestionResult ingestJson(String jsonContent) {
        if (jsonContent == null || jsonContent.isBlank()) {
            return new IngestionResult(false, null, null, 0, "JSON content is empty.");
        }

        try {
            JsonNode root = objectMapper.readTree(jsonContent);
            String remId = root.path("remedy_id").asText("").trim().toLowerCase();
            String remName = root.path("remedy_name").asText("").trim();
            String commonName = root.path("common_name").asText("").trim();
            String prover = root.path("prover_director").asText("Research Institute / Prover Protocol").trim();
            int year = root.path("proving_year").asInt(2025);
            String activeConst = root.path("active_constituents").asText("Verified bio-active constituents").trim();
            String pharmAction = root.path("pharmacological_action").asText("Hahnemannian biological proving profile").trim();
            String organAffinity = root.path("organ_affinity").asText("General systemic affinity").trim();

            if (remId.isEmpty() || remName.isEmpty()) {
                return new IngestionResult(false, remId, remName, 0, "Missing required remedy_id or remedy_name in JSON.");
            }

            JsonNode sectionsNode = root.path("sections");
            int count = 0;
            if (sectionsNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = sectionsNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String sectionName = entry.getKey().toUpperCase().trim();
                    String symptomText = entry.getValue().asText().trim();
                    if (!symptomText.isEmpty()) {
                        clinicDao.saveCustomProving(new ClinicDao.CustomProving(
                                UUID.randomUUID().toString(),
                                remId,
                                remName,
                                prover,
                                year,
                                sectionName,
                                symptomText,
                                "JSON",
                                Instant.now()
                        ));
                        count++;
                    }
                }
            }

            // Register into master constituents if not present
            clinicDao.saveMedicineConstituent(new MedicineConstituent(
                    remId, remName, commonName, activeConst, pharmAction, organAffinity,
                    "Proving Director: " + prover + " (" + year + ")", "", Instant.now()
            ));

            log.info("Successfully ingested JSON proving for [{}] with {} sections.", remId, count);
            return new IngestionResult(true, remId, remName, count,
                    "Successfully ingested " + count + " clinical proving sections for " + remName + " into local clinic database.");
        } catch (Exception e) {
            log.error("Failed to ingest JSON proving", e);
            return new IngestionResult(false, null, null, 0, "JSON Ingestion Error: " + e.getMessage());
        }
    }

    /**
     * Ingests a CSV or TSV proving file.
     * Expected header: remedy_id,remedy_name,common_name,prover,year,section,symptoms,active_constituents
     */
    public IngestionResult ingestCsv(File file) {
        if (!file.exists() || !file.canRead()) {
            return new IngestionResult(false, null, null, 0, "Proving file does not exist or cannot be read.");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            String delimiter = file.getName().endsWith(".tsv") ? "\t" : ",";
            int count = 0;
            String lastRemId = null;
            String lastRemName = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (first) {
                    first = false;
                    if (line.toLowerCase().startsWith("remedy_id")) continue;
                }

                String[] p = line.split(delimiter, -1);
                if (p.length >= 7) {
                    String remId = p[0].trim().toLowerCase();
                    String remName = p[1].trim();
                    String common = p[2].trim();
                    String prover = p[3].trim();
                    int year = 2025;
                    try { year = Integer.parseInt(p[4].trim()); } catch (Exception ignored) {}
                    String section = p[5].trim().toUpperCase();
                    String symptoms = p[6].trim();
                    String active = p.length >= 8 ? p[7].trim() : "Phytochemical Proving Extract";

                    lastRemId = remId;
                    lastRemName = remName;

                    clinicDao.saveCustomProving(new ClinicDao.CustomProving(
                            UUID.randomUUID().toString(), remId, remName, prover, year,
                            section, symptoms, "CSV", Instant.now()
                    ));
                    count++;

                    clinicDao.saveMedicineConstituent(new MedicineConstituent(
                            remId, remName, common, active, "Biological Proving Protocol", "Systemic",
                            "Prover: " + prover, "", Instant.now()
                    ));
                }
            }

            return new IngestionResult(true, lastRemId, lastRemName, count,
                    "Successfully imported " + count + " proving symptom records from " + file.getName());
        } catch (Exception e) {
            log.error("Failed to ingest CSV proving", e);
            return new IngestionResult(false, null, null, 0, "CSV Ingestion Error: " + e.getMessage());
        }
    }
}
