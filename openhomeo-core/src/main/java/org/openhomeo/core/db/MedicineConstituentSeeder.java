package org.openhomeo.core.db;

import org.openhomeo.core.model.MedicineConstituent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeder for readymade Phytochemical, Biochemical, and Pharmacological
 * Constituent data across Homeopathic Remedies, with Common and Indian names.
 * Loads the exhaustive A-Z homeopathic universe from bundled master_medicines_exhaustive.tsv.
 */
public class MedicineConstituentSeeder {
    private static final Logger log = LoggerFactory.getLogger(MedicineConstituentSeeder.class);

    public static void seedConstituentsIfEmpty(Connection conn) {
        List<MedicineConstituent> list = buildMasterConstituents();
        if (list.isEmpty()) {
            log.warn("Exhaustive master medicine list is empty; skipping seeding.");
            return;
        }

        try {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM medicine_constituents")) {
                if (rs.next() && rs.getInt(1) >= list.size()) {
                    return; // Already populated with complete exhaustive catalog
                }
            }
        } catch (SQLException e) {
            log.warn("Could not check medicine_constituents count: {}", e.getMessage());
        }

        log.info("Seeding comprehensive master medicine constituent database ({} remedies) with Indian and International pharmacopoeias...", list.size());

        String sql = """
            INSERT INTO medicine_constituents (
                remedy_id, remedy_name, common_and_indian_name, active_constituents,
                pharmacological_action, organ_affinity, toxicology_and_antidotes, annexed_remarks
            ) VALUES (?, ?, ?, ?, ?, ?, ?, '')
            ON CONFLICT(remedy_id) DO UPDATE SET
                remedy_name = excluded.remedy_name,
                common_and_indian_name = excluded.common_and_indian_name,
                active_constituents = excluded.active_constituents,
                pharmacological_action = excluded.pharmacological_action,
                organ_affinity = excluded.organ_affinity,
                toxicology_and_antidotes = excluded.toxicology_and_antidotes
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (MedicineConstituent mc : list) {
                ps.setString(1, mc.remedyId());
                ps.setString(2, mc.remedyName());
                ps.setString(3, mc.commonAndIndianName());
                ps.setString(4, mc.activeConstituents());
                ps.setString(5, mc.pharmacologicalAction());
                ps.setString(6, mc.organAffinity());
                ps.setString(7, mc.toxicologyAndAntidotes());
                ps.addBatch();
            }
            ps.executeBatch();
            log.info("Successfully seeded {} exhaustive medicine constituent records into master catalog.", list.size());
        } catch (SQLException e) {
            log.error("Failed to seed medicine constituents", e);
        }
    }

    public static List<MedicineConstituent> buildMasterConstituents() {
        List<MedicineConstituent> list = new ArrayList<>();
        try (InputStream in = MedicineConstituentSeeder.class.getResourceAsStream("/data/master_medicines_exhaustive.tsv")) {
            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    boolean isFirst = true;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        if (isFirst) {
                            isFirst = false;
                            if (line.startsWith("remedy_id")) continue; // Skip header
                        }
                        String[] parts = line.split("\t", -1);
                        if (parts.length >= 7) {
                            list.add(MedicineConstituent.of(
                                    parts[0].trim(),
                                    parts[1].trim(),
                                    parts[2].trim(),
                                    parts[3].trim(),
                                    parts[4].trim(),
                                    parts[5].trim(),
                                    parts[6].trim()
                            ));
                        }
                    }
                }
            } else {
                log.warn("Bundled /data/master_medicines_exhaustive.tsv not found in classpath resources! Falling back to core built-in list.");
            }
        } catch (Exception e) {
            log.error("Error reading master_medicines_exhaustive.tsv", e);
        }

        if (list.isEmpty()) {
            list.addAll(getCoreFallbackList());
        }
        return list;
    }

    private static List<MedicineConstituent> getCoreFallbackList() {
        List<MedicineConstituent> list = new ArrayList<>();
        list.add(MedicineConstituent.of(
                "calen", "Calendula officinalis", "Marigold / Zendu / Genda Phool",
                "Oleanolic acid, calendulosides, triterpene saponins, faradiol monoesters, carotenoids, flavonoids",
                "Stimulates fibroblast proliferation, accelerates collagen synthesis and neo-vascularization, promotes rapid epithelial migration across lacerated tissue without keloid or scar contracture. Remarkable antiseptic and hemostatic in mechanical trauma.",
                "Dermis, epidermal cells, capillary endothelium, mucocutaneous junctions, surgical wounds",
                "Physiologically non-toxic; antidotes: Arnica, Hepar sulph"
        ));
        list.add(MedicineConstituent.of(
                "hyper", "Hypericum perforatum", "St. John's Wort / Basant",
                "Hypericin, pseudohypericin, hyperforin, flavonoids (quercetin, rutin), xanthones",
                "Direct modulation of central and peripheral neurogenic pain pathways. Inhibits neuronal reuptake of serotonin, dopamine, and norepinephrine. Accelerates peripheral axon sprouting, prevents neuroma formation, and relieves shooting, lancinating neuralgias after puncture or crush injuries.",
                "Spinal cord, cerebrospinal axis, peripheral sensory nerves, nerve endings, coccyx, fingertips",
                "Antidoted by Arsenicum album, Chamomilla"
        ));
        list.add(MedicineConstituent.of(
                "arn", "Arnica montana", "Leopard's Bane / Mountain Arnica",
                "Helenalin, 11α,13-dihydrohelenalin, sesquiterpene lactones, caffeic acid derivatives, flavonoids",
                "Inhibits NF-κB transcription factor activation and platelet aggregation via alkylation of sulfhydryl groups by sesquiterpene lactones. Prevents venous micro-thrombi, reduces vascular permeability and extravasation, resolving ecchymosis, hematoma, and bruised muscular soreness.",
                "Capillary endothelium, vascular system, striated muscle fibers, subcutaneous extravasated blood",
                "Antidoted by Camphora, Aconitum, Rhus tox; incompatible: avoid simultaneous Arnica and wine"
        ));
        list.add(MedicineConstituent.of(
                "andro-p", "Andrographis paniculata", "Kalmegh / King of Bitters / Mahatita",
                "Andrographolide, neoandrographolide, 14-deoxyandrographolide, diterpene lactones",
                "Potent hepatoprotective and antioxidant; modulates NF-κB, stimulates bile flow, enhances CYP450 enzyme induction, and down-regulates pro-inflammatory cytokines in intermittent fever and sluggish liver.",
                "Hepatocytes, biliary tract, reticuloendothelial system, intestinal enterocytes",
                "Antidotes: Cinchona, Chelidonium majus"
        ));
        return list;
    }
}
