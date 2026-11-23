package org.openhomeo.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;

/**
 * Automated Knowledge Base Generator and Seeder.
 * Compiles classical Kent & Boenninghausen repertory hierarchies,
 * Boericke & Allen Materia Medica, and remedy relationships into
 * an optimized, pre-indexed master_repertory.db with SQLite FTS5.
 */
public class KnowledgeBaseSeeder {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseSeeder.class);

    public static void main(String[] args) {
        String outputPath = args.length > 0 ? args[0] : "master_repertory.db";
        try {
            seed(outputPath);
        } catch (Exception e) {
            log.error("Failed to seed knowledge base", e);
            throw new RuntimeException("Knowledge base seeding failed", e);
        }
    }

    public static void seed(String outputPath) throws Exception {
        log.info("Starting OpenHomeo Knowledge Base Seeder -> {}", outputPath);

        File dbFile = new File(outputPath);
        if (dbFile.exists()) {
            boolean deleted = dbFile.delete();
            log.info("Removed existing database file: {}", deleted);
        }

        Class.forName("org.sqlite.JDBC");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + outputPath)) {
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA journal_mode = WAL;");
                s.execute("PRAGMA synchronous = NORMAL;");
            }
            conn.setAutoCommit(false);
            createSchema(conn);
            seedRemedies(conn);
            seedRemedyRelationships(conn);
            seedMateriaMedica(conn);
            seedRepertoryRubrics(conn);
            conn.commit();
            log.info("Database commit successful. Creating FTS5 index...");
            buildFtsIndex(conn);
            conn.commit();
            verifyKnowledgeBase(conn);
            log.info("OpenHomeo Master Repertory Database compiled successfully to: {}", outputPath);
        }
    }

    public static void createSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE remedies (
                    id TEXT PRIMARY KEY,
                    abbr TEXT UNIQUE NOT NULL,
                    full_name TEXT NOT NULL,
                    common_name TEXT,
                    kingdom TEXT NOT NULL,
                    family TEXT,
                    periodic_stage INTEGER,
                    periodic_series INTEGER,
                    miasm TEXT,
                    thermal TEXT,
                    thirst TEXT
                );
            """);

            stmt.execute("""
                CREATE TABLE remedy_relationships (
                    id TEXT PRIMARY KEY,
                    remedy_id TEXT NOT NULL REFERENCES remedies(id),
                    related_remedy_id TEXT NOT NULL REFERENCES remedies(id),
                    relation_type TEXT NOT NULL,
                    notes TEXT
                );
            """);

            stmt.execute("""
                CREATE TABLE materia_medica (
                    id TEXT PRIMARY KEY,
                    remedy_id TEXT NOT NULL REFERENCES remedies(id),
                    author TEXT NOT NULL,
                    book_title TEXT NOT NULL,
                    section TEXT NOT NULL,
                    body_text TEXT NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE rubrics (
                    id TEXT PRIMARY KEY,
                    chapter TEXT NOT NULL,
                    parent_id TEXT REFERENCES rubrics(id),
                    rubric_text TEXT NOT NULL,
                    full_path TEXT NOT NULL,
                    depth INTEGER NOT NULL DEFAULT 0,
                    is_polar BOOLEAN DEFAULT FALSE,
                    opposite_rubric_id TEXT REFERENCES rubrics(id)
                );
            """);
            stmt.execute("CREATE INDEX idx_rubrics_path ON rubrics(full_path);");
            stmt.execute("CREATE INDEX idx_rubrics_chapter ON rubrics(chapter);");

            stmt.execute("""
                CREATE TABLE rubric_remedies (
                    rubric_id TEXT NOT NULL REFERENCES rubrics(id),
                    remedy_id TEXT NOT NULL REFERENCES remedies(id),
                    grade INTEGER NOT NULL CHECK(grade BETWEEN 1 AND 4),
                    author_citation TEXT,
                    PRIMARY KEY(rubric_id, remedy_id)
                );
            """);
            stmt.execute("CREATE INDEX idx_rr_rem ON rubric_remedies(remedy_id);");
        }
    }

    private static void seedRemedies(Connection conn) throws SQLException {
        String sql = "INSERT INTO remedies VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Polychrests, minerals, plant families, animal poisons, and nosodes
            insertRemedy(ps, "acon", "Acon.", "Aconitum napellus", "Monkshood", "Plant", "Ranunculaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "all-c", "All-c.", "Allium cepa", "Red onion", "Plant", "Amaryllidaceae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "alum", "Alum.", "Alumina", "Oxide of aluminum", "Mineral", "Metals", 13, 3, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "apis", "Apis", "Apis mellifica", "Honey bee", "Animal", "Apidae", null, null, "SYCOSIS", "HOT", "THIRSTLESS");
            insertRemedy(ps, "arg-n", "Arg-n.", "Argentum nitricum", "Silver nitrate", "Mineral", "Silver Series", 11, 5, "SYCOSIS", "HOT", "THIRSTLESS");
            insertRemedy(ps, "arn", "Arn.", "Arnica montana", "Leopard's bane", "Plant", "Compositae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "ars", "Ars.", "Arsenicum album", "White oxide of arsenic", "Mineral", "Arsenic Group", 15, 4, "SYPHILIS", "CHILLY", "THIRSTY");
            insertRemedy(ps, "bell", "Bell.", "Belladonna", "Deadly nightshade", "Plant", "Solanaceae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "bry", "Bry.", "Bryonia alba", "White bryony", "Plant", "Cucurbitaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "calc", "Calc.", "Calcarea carbonica", "Carbonate of lime", "Mineral", "Alkaline Earths", 2, 4, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "calc-p", "Calc-p.", "Calcarea phosphorica", "Phosphate of lime", "Mineral", "Calcium Salts", 2, 4, "TUBERCULAR", "CHILLY", "THIRSTY");
            insertRemedy(ps, "caust", "Caust.", "Causticum", "Hahnemann's tinctura acris sine kali", "Mineral", "Potassium Compound", null, null, "SYCOSIS", "CHILLY", "THIRSTY");
            insertRemedy(ps, "cham", "Cham.", "Chamomilla", "German chamomile", "Plant", "Compositae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "chel", "Chel.", "Chelidonium majus", "Greater celandine", "Plant", "Papaveraceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "chin", "Chin.", "China officinalis", "Peruvian bark", "Plant", "Rubiaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "chlor", "Chlor.", "Chlorum", "Chlorine gas", "Mineral", "Halogens", 17, 3, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "coff", "Coff.", "Coffea cruda", "Unroasted coffee", "Plant", "Rubiaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "dulc", "Dulc.", "Dulcamara", "Bittersweet", "Plant", "Solanaceae", null, null, "SYCOSIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "ferr", "Ferr.", "Ferrum metallicum", "Metallic iron", "Mineral", "Iron Series", 8, 4, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "ferr-p", "Ferr-p.", "Ferrum phosphoricum", "Phosphate of iron", "Mineral", "Iron Salts", 8, 4, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "gels", "Gels.", "Gelsemium sempervirens", "Yellow jessamine", "Plant", "Loganiaceae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "glon", "Glon.", "Glonoinum", "Nitroglycerine", "Mineral", "Compounds", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "graph", "Graph.", "Graphites", "Black lead / Mineral carbon", "Mineral", "Carbon Group", 14, 2, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "hepar", "Hep.", "Hepar sulphuris calcareum", "Hahnemann's calcium sulphide", "Mineral", "Sulphide", 16, 4, "SYPHILIS", "CHILLY", "THIRSTY");
            insertRemedy(ps, "hyos", "Hyos.", "Hyoscyamus niger", "Henbane", "Plant", "Solanaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "ign", "Ign.", "Ignatia amara", "St. Ignatius bean", "Plant", "Loganiaceae", null, null, "PSORA", "AMBITHERMAL", "THIRSTLESS");
            insertRemedy(ps, "iod", "Iod.", "Iodium", "Iodine", "Mineral", "Halogens", 17, 5, "TUBERCULAR", "HOT", "THIRSTY");
            insertRemedy(ps, "iris", "Iris", "Iris versicolor", "Blue flag", "Plant", "Iridaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "kali-c", "Kali-c.", "Kali carbonicum", "Potassium carbonate", "Mineral", "Alkali Metals", 1, 4, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "kali-p", "Kali-p.", "Kali phosphoricum", "Phosphate of potassium", "Mineral", "Nerve Salts", 1, 4, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "lach", "Lach.", "Lachesis muta", "Bushmaster snake venom", "Animal", "Viperidae", null, null, "SYPHILIS", "HOT", "THIRSTY");
            insertRemedy(ps, "lyc", "Lyc.", "Lycopodium clavatum", "Club moss", "Plant", "Lycopodiaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "mag-c", "Mag-c.", "Magnesia carbonica", "Carbonate of magnesia", "Mineral", "Magnesium", 2, 3, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "melil", "Melil.", "Melilotus officinalis", "Yellow sweet clover", "Plant", "Leguminosae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "merc", "Merc.", "Mercurius solubilis", "Hahnemann's soluble mercury", "Mineral", "Heavy Metals", 12, 6, "SYPHILIS", "AMBITHERMAL", "THIRSTY");
            insertRemedy(ps, "nat-m", "Nat-m.", "Natrum muriaticum", "Sodium chloride (Common Salt)", "Mineral", "Halogens", 1, 3, "SYCOSIS", "HOT", "THIRSTY");
            insertRemedy(ps, "nit-ac", "Nit-ac.", "Nitricum acidum", "Nitric acid", "Mineral", "Acids", 15, 2, "SYPHILIS", "CHILLY", "THIRSTY");
            insertRemedy(ps, "nux-v", "Nux-v.", "Nux vomica", "Poison nut", "Plant", "Loganiaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "petr", "Petr.", "Petroleum", "Crude rock oil", "Mineral", "Hydrocarbons", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "phos", "Phos.", "Phosphorus", "Phosphorus", "Mineral", "Phosphorus Group", 15, 3, "TUBERCULAR", "CHILLY", "THIRSTY");
            insertRemedy(ps, "plat", "Plat.", "Platinum metallicum", "Platinum", "Mineral", "Heavy Metals", 10, 6, "SYPHILIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "psor", "Psor.", "Psorinum", "Psoric scabies vesicle nosode", "Nosode", "Nosodes", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "puls", "Puls.", "Pulsatilla pratensis", "Wind flower", "Plant", "Ranunculaceae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "rhus-t", "Rhus-t.", "Rhus toxicodendron", "Poison ivy", "Plant", "Anacardiaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "sec", "Sec.", "Secale cornutum", "Ergot of rye", "Plant", "Fungi", null, null, "SYPHILIS", "HOT", "THIRSTLESS");
            insertRemedy(ps, "sep", "Sep.", "Sepia succus", "Inky juice of cuttlefish", "Animal", "Cephalopoda", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "sil", "Sil.", "Silicea terra", "Pure flint / Silicon dioxide", "Mineral", "Carbon Group", 14, 3, "SYPHILIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "spig", "Spig.", "Spigelia anthelmia", "Pinkroot", "Plant", "Loganiaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "stram", "Stram.", "Stramonium", "Thorn apple", "Plant", "Solanaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "sulph", "Sulph.", "Sulphur", "Sublimed sulphur", "Mineral", "Chalcogens", 16, 3, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "thuj", "Thuj.", "Thuja occidentalis", "Arbor vitae", "Plant", "Cupressaceae", null, null, "SYCOSIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "tub", "Tub.", "Tuberculinum bovinum", "Tuberculous sputum nosode", "Nosode", "Nosodes", null, null, "TUBERCULAR", "CHILLY", "THIRSTY");
            insertRemedy(ps, "med", "Med.", "Medorrhinum", "Gonorrhoeal discharge nosode", "Nosode", "Nosodes", null, null, "SYCOSIS", "HOT", "THIRSTY");
            insertRemedy(ps, "syph", "Syph.", "Syphilinum", "Syphilitic chancre nosode", "Nosode", "Nosodes", null, null, "SYPHILIS", "AMBITHERMAL", "THIRSTY");
            ps.executeBatch();
        }
    }

    private static void insertRemedy(PreparedStatement ps, String id, String abbr, String fullName, String common,
                                     String kingdom, String family, Integer stage, Integer series,
                                     String miasm, String thermal, String thirst) throws SQLException {
        ps.setString(1, id);
        ps.setString(2, abbr);
        ps.setString(3, fullName);
        ps.setString(4, common);
        ps.setString(5, kingdom);
        ps.setString(6, family);
        if (stage != null) ps.setInt(7, stage); else ps.setNull(7, Types.INTEGER);
        if (series != null) ps.setInt(8, series); else ps.setNull(8, Types.INTEGER);
        ps.setString(9, miasm);
        ps.setString(10, thermal);
        ps.setString(11, thirst);
        ps.addBatch();
    }

    private static void seedRemedyRelationships(Connection conn) throws SQLException {
        String sql = "INSERT INTO remedy_relationships VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Classical Gibson Miller and Boenninghausen Concordances
            insertRel(ps, "acon", "sulph", "COMPLEMENTARY", "Sulphur complements acute Aconite in inflammation");
            insertRel(ps, "bell", "calc", "COMPLEMENTARY", "Calcarea carbonica is the chronic complementary of Belladonna");
            insertRel(ps, "bry", "alum", "COMPLEMENTARY", "Alumina complements Bryonia in chronic obstinate constipation");
            insertRel(ps, "sulph", "calc", "FOLLOWS_WELL", "Sulphur followed by Calcarea and Lycopodium (Hahnemannian triad)");
            insertRel(ps, "calc", "lyc", "FOLLOWS_WELL", "Completes the action of Calcarea");
            insertRel(ps, "arn", "rhus-t", "FOLLOWS_WELL", "Follows Arnica in muscle strain and sprains");
            insertRel(ps, "puls", "sil", "COMPLEMENTARY", "Silicea is the chronic of Pulsatilla");
            insertRel(ps, "nat-m", "sep", "COMPLEMENTARY", "Natrum muriaticum complements Sepia in uterine and emotional states");
            insertRel(ps, "cham", "coff", "ANTIDOTE", "Coffea antidotes over-sensitive pain of Chamomilla");
            insertRel(ps, "nux-v", "acon", "ANTIDOTE", "Nux vomica antidotes effects of drugging and excessive stimulation");
            insertRel(ps, "phos", "caust", "INIMICAL", "Phosphorus and Causticum are incompatible; do not follow each other");
            insertRel(ps, "sil", "merc", "INIMICAL", "Silicea and Mercurius are inimical; suppuration risk");
            insertRel(ps, "apis", "rhus-t", "INIMICAL", "Apis and Rhus toxicodendron are inimical; discordant action");
            insertRel(ps, "lach", "nit-ac", "FOLLOWS_WELL", "Follows well in deep destructive septic states");
            insertRel(ps, "thuj", "med", "COMPLEMENTARY", "Medorrhinum complements Thuja in sycotic constitution");
            ps.executeBatch();
        }
    }

    private static void insertRel(PreparedStatement ps, String rem1, String rem2, String type, String notes) throws SQLException {
        ps.setString(1, rem1 + "_" + type.toLowerCase() + "_" + rem2);
        ps.setString(2, rem1);
        ps.setString(3, rem2);
        ps.setString(4, type);
        ps.setString(5, notes);
        ps.addBatch();
    }

    private static void seedMateriaMedica(Connection conn) throws SQLException {
        String sql = "INSERT INTO materia_medica VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Boericke & Allen excerpts for primary polychrests
            insertMM(ps, "lyc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Melancholy; afraid to be alone. Little things annoy; extremely sensitive. Averse to undertaking anything new. Headstrong and haughty when sick; loss of self-confidence. Weak memory, confused thoughts; spells words incorrectly.");
            insertMM(ps, "lyc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Dyspepsia from farinaceous and fermentable food, cabbage, beans. Excessive hunger, but eating ever so little creates fullness. Canine hunger with sudden satiety. Desire for sweet things. Food tastes sour. Incomplete burning eructations.");
            insertMM(ps, "lyc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: nearly all complaints right-sided, from right to left, from above downward; 4 to 8 p.m.; from heat or warm room, hot air, bed. Better: by motion, after midnight, from warm food and drink, on getting cold, uncovering.");

            insertMM(ps, "ars", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Great anguish and restlessness. Changes place continually. Fear of death, of being left alone. Great fear, with cold sweat. Thinks it useless to take medicine. Suicidal. Extremely fastidious; neat, order-loving to an extreme.");
            insertMM(ps, "ars", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Cannot bear the sight or smell of food. Great thirst; drinks much, but little at a time; water deranges the stomach. Nausea, retching, vomiting after eating or drinking. Burning pain like coals of fire. Craves milk and warm drinks.");
            insertMM(ps, "ars", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: wet weather, after midnight (1 to 2 a.m.); from cold drinks and food; right side. Better: from heat in general (except headache which is temporarily relieved by cold bath); warm drinks, head elevated.");

            insertMM(ps, "phos", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Great lowness of spirits. Easily vexed. Fearful, as if something were creeping out of every corner. Clairvoyant state. Anxious, restless, worse alone, during thunderstorm, twilight. Over-sensitive to all external impressions: light, sound, odors.");
            insertMM(ps, "phos", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Hunger soon after eating. Sour taste and sour eructations after every meal. Thirst for very cold water; wants cold food and ice cream; as soon as cold water becomes warm in stomach, it is vomited. Burning in stomach extending to throat.");
            insertMM(ps, "phos", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: touch; physical or mental exertion; twilight; warm food or drink; change of weather, from getting wet in hot weather; evening; lying on left or painful side; during thunderstorm. Better: in dark; lying on right side; cold food and drink; being rubbed; sleep.");

            insertMM(ps, "puls", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Weeps easily; almost impossible to detail symptoms without weeping. Mild, gentle, yielding disposition. Sad, crying, melancholic. Highly emotional. Sympathetic, seeks comforting and soothing words. Children like to be stroked and carried gently.");
            insertMM(ps, "puls", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: from heat, rich fat food, warm room, evening, lying on left side. Better: open air, motion, cold application, cold food and drinks, though thirstless.");

            insertMM(ps, "nux-v", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Very irritable, sensitive to all impressions. Does not want to be touched or spoken to. Sullen, fault-finding, spiteful, violent. Zealous fiery temperament. Cannot bear noises, scents, light. Time passes too slowly.");
            insertMM(ps, "nux-v", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: morning, mental exertion, after eating, touch, spices, stimulants, narcotics, dry cold, sitting. Better: from a nap, in evening, while at rest, in damp wet weather, strong pressure.");

            insertMM(ps, "sulph", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Standing is the most uncomfortable position; dirty, filthy people, prone to skin affections. Averse to being washed. Empty, gone faint feeling in epigastrium at 11 a.m. Burning soles of feet; must stick them out of bed at night. Heat on vertex.");
            ps.executeBatch();
        }
    }

    private static void insertMM(PreparedStatement ps, String remedyId, String author, String book, String section, String body) throws SQLException {
        ps.setString(1, remedyId + "_" + author.replaceAll("\\s+", "").toLowerCase() + "_" + section.toLowerCase());
        ps.setString(2, remedyId);
        ps.setString(3, author);
        ps.setString(4, book);
        ps.setString(5, section);
        ps.setString(6, body);
        ps.addBatch();
    }

    private static void seedRepertoryRubrics(Connection conn) throws SQLException {
        String sqlRubric = "INSERT INTO rubrics VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String sqlRR = "INSERT INTO rubric_remedies VALUES (?, ?, ?, ?)";

        try (PreparedStatement psR = conn.prepareStatement(sqlRubric);
             PreparedStatement psRR = conn.prepareStatement(sqlRR)) {

            // ==========================================
            // 1. MIND CHAPTER
            // ==========================================
            insertRubric(psR, "mind_anxiety", "MIND", null, "ANXIETY", "MIND - ANXIETY", 0, false, null);
            insertRR(psRR, "mind_anxiety", "ars", 4, "k");
            insertRR(psRR, "mind_anxiety", "acon", 4, "k");
            insertRR(psRR, "mind_anxiety", "phos", 3, "k");
            insertRR(psRR, "mind_anxiety", "calc", 3, "k");
            insertRR(psRR, "mind_anxiety", "puls", 3, "k");
            insertRR(psRR, "mind_anxiety", "rhus-t", 3, "k");
            insertRR(psRR, "mind_anxiety", "bell", 2, "k");
            insertRR(psRR, "mind_anxiety", "lyc", 2, "k");
            insertRR(psRR, "mind_anxiety", "nux-v", 2, "k");

            insertRubric(psR, "mind_anxiety_evening", "MIND", "mind_anxiety", "evening", "MIND - ANXIETY - evening", 1, false, null);
            insertRR(psRR, "mind_anxiety_evening", "ars", 4, "k");
            insertRR(psRR, "mind_anxiety_evening", "calc", 3, "k");
            insertRR(psRR, "mind_anxiety_evening", "phos", 3, "k");
            insertRR(psRR, "mind_anxiety_evening", "puls", 3, "k");
            insertRR(psRR, "mind_anxiety_evening", "rhus-t", 3, "k");

            insertRubric(psR, "mind_anxiety_evening_bed", "MIND", "mind_anxiety_evening", "bed, in", "MIND - ANXIETY - evening - bed, in", 2, false, null);
            insertRR(psRR, "mind_anxiety_evening_bed", "ars", 4, "k");
            insertRR(psRR, "mind_anxiety_evening_bed", "calc", 3, "k");
            insertRR(psRR, "mind_anxiety_evening_bed", "mag-c", 2, "k");
            insertRR(psRR, "mind_anxiety_evening_bed", "puls", 3, "k");

            insertRubric(psR, "mind_fear_death", "MIND", null, "FEAR - death, of", "MIND - FEAR - death, of", 1, false, null);
            insertRR(psRR, "mind_fear_death", "acon", 4, "k");
            insertRR(psRR, "mind_fear_death", "ars", 4, "k");
            insertRR(psRR, "mind_fear_death", "calc", 3, "k");
            insertRR(psRR, "mind_fear_death", "phos", 3, "k");
            insertRR(psRR, "mind_fear_death", "gels", 2, "k");
            insertRR(psRR, "mind_fear_death", "nit-ac", 2, "k");

            insertRubric(psR, "mind_fear_dark", "MIND", null, "FEAR - dark, in the", "MIND - FEAR - dark, in the", 1, false, null);
            insertRR(psRR, "mind_fear_dark", "stram", 4, "k");
            insertRR(psRR, "mind_fear_dark", "phos", 3, "k");
            insertRR(psRR, "mind_fear_dark", "bell", 3, "k");
            insertRR(psRR, "mind_fear_dark", "calc", 2, "k");

            insertRubric(psR, "mind_irritability", "MIND", null, "IRRITABILITY", "MIND - IRRITABILITY", 0, false, null);
            insertRR(psRR, "mind_irritability", "cham", 4, "k");
            insertRR(psRR, "mind_irritability", "nux-v", 4, "k");
            insertRR(psRR, "mind_irritability", "bry", 3, "k");
            insertRR(psRR, "mind_irritability", "hepar", 3, "k");
            insertRR(psRR, "mind_irritability", "lyc", 3, "k");
            insertRR(psRR, "mind_irritability", "sulph", 3, "k");

            insertRubric(psR, "mind_weeping", "MIND", null, "WEEPING - tearful mood", "MIND - WEEPING - tearful mood", 0, false, null);
            insertRR(psRR, "mind_weeping", "puls", 4, "k");
            insertRR(psRR, "mind_weeping", "ign", 3, "k");
            insertRR(psRR, "mind_weeping", "nat-m", 3, "k");
            insertRR(psRR, "mind_weeping", "sep", 3, "k");
            insertRR(psRR, "mind_weeping", "plat", 2, "k");

            // ==========================================
            // 2. HEAD CHAPTER
            // ==========================================
            insertRubric(psR, "head_pain_forehead", "HEAD", null, "PAIN - forehead", "HEAD - PAIN - forehead", 1, false, null);
            insertRR(psRR, "head_pain_forehead", "bell", 4, "k");
            insertRR(psRR, "head_pain_forehead", "bry", 4, "k");
            insertRR(psRR, "head_pain_forehead", "nux-v", 3, "k");
            insertRR(psRR, "head_pain_forehead", "gels", 3, "k");
            insertRR(psRR, "head_pain_forehead", "puls", 3, "k");
            insertRR(psRR, "head_pain_forehead", "iris", 2, "k");

            insertRubric(psR, "head_pain_pulsating", "HEAD", null, "PAIN - pulsating", "HEAD - PAIN - pulsating", 1, false, null);
            insertRR(psRR, "head_pain_pulsating", "bell", 4, "k");
            insertRR(psRR, "head_pain_pulsating", "glon", 4, "k");
            insertRR(psRR, "head_pain_pulsating", "nat-m", 3, "k");
            insertRR(psRR, "head_pain_pulsating", "ferr-p", 2, "k");
            insertRR(psRR, "head_pain_pulsating", "melil", 2, "k");

            insertRubric(psR, "head_pain_cold_air_agg", "HEAD", null, "PAIN - cold air, agg.", "HEAD - PAIN - cold air, agg.", 1, true, "head_pain_cold_air_amel");
            insertRR(psRR, "head_pain_cold_air_agg", "sil", 4, "k");
            insertRR(psRR, "head_pain_cold_air_agg", "hepar", 4, "k");
            insertRR(psRR, "head_pain_cold_air_agg", "nux-v", 3, "k");
            insertRR(psRR, "head_pain_cold_air_agg", "ars", 3, "k");
            insertRR(psRR, "head_pain_cold_air_agg", "chin", 2, "k");

            insertRubric(psR, "head_pain_cold_air_amel", "HEAD", null, "PAIN - cold air, amel.", "HEAD - PAIN - cold air, amel.", 1, true, "head_pain_cold_air_agg");
            insertRR(psRR, "head_pain_cold_air_amel", "puls", 4, "k");
            insertRR(psRR, "head_pain_cold_air_amel", "all-c", 3, "k");
            insertRR(psRR, "head_pain_cold_air_amel", "nat-m", 3, "k");
            insertRR(psRR, "head_pain_cold_air_amel", "acon", 2, "k");

            // ==========================================
            // 3. STOMACH CHAPTER
            // ==========================================
            insertRubric(psR, "stomach_thirst_large_quantities", "STOMACH", null, "THIRST - large quantities", "STOMACH - THIRST - large quantities", 1, false, null);
            insertRR(psRR, "stomach_thirst_large_quantities", "bry", 4, "k");
            insertRR(psRR, "stomach_thirst_large_quantities", "acon", 3, "k");
            insertRR(psRR, "stomach_thirst_large_quantities", "phos", 3, "k");
            insertRR(psRR, "stomach_thirst_large_quantities", "nat-m", 3, "k");
            insertRR(psRR, "stomach_thirst_large_quantities", "sulph", 3, "k");

            insertRubric(psR, "stomach_thirst_little_and_often", "STOMACH", null, "THIRST - small quantities, often", "STOMACH - THIRST - small quantities, often", 1, false, null);
            insertRR(psRR, "stomach_thirst_little_and_often", "ars", 4, "k");
            insertRR(psRR, "stomach_thirst_little_and_often", "chin", 3, "k");
            insertRR(psRR, "stomach_thirst_little_and_often", "hyos", 2, "k");

            insertRubric(psR, "stomach_desire_sweets", "STOMACH", null, "DESIRES - sweets", "STOMACH - DESIRES - sweets", 1, false, null);
            insertRR(psRR, "stomach_desire_sweets", "lyc", 4, "k");
            insertRR(psRR, "stomach_desire_sweets", "arg-n", 4, "k");
            insertRR(psRR, "stomach_desire_sweets", "sulph", 4, "k");
            insertRR(psRR, "stomach_desire_sweets", "chin", 2, "k");

            // ==========================================
            // 4. GENERALITIES (Polar Symptoms for Boenninghausen)
            // ==========================================
            insertRubric(psR, "gen_cold_agg", "GENERALITIES", null, "COLD - in general - agg.", "GENERALITIES - COLD - in general - agg.", 1, true, "gen_warm_agg");
            insertRR(psRR, "gen_cold_agg", "ars", 4, "k");
            insertRR(psRR, "gen_cold_agg", "hepar", 4, "k");
            insertRR(psRR, "gen_cold_agg", "sil", 4, "k");
            insertRR(psRR, "gen_cold_agg", "nux-v", 4, "k");
            insertRR(psRR, "gen_cold_agg", "rhus-t", 3, "k");
            insertRR(psRR, "gen_cold_agg", "calc", 3, "k");
            insertRR(psRR, "gen_cold_agg", "caust", 3, "k");

            insertRubric(psR, "gen_warm_agg", "GENERALITIES", null, "WARM - in general - agg.", "GENERALITIES - WARM - in general - agg.", 1, true, "gen_cold_agg");
            insertRR(psRR, "gen_warm_agg", "puls", 4, "k");
            insertRR(psRR, "gen_warm_agg", "apis", 4, "k");
            insertRR(psRR, "gen_warm_agg", "sulph", 4, "k");
            insertRR(psRR, "gen_warm_agg", "nat-m", 3, "k");
            insertRR(psRR, "gen_warm_agg", "iod", 3, "k");
            insertRR(psRR, "gen_warm_agg", "sec", 3, "k");

            insertRubric(psR, "gen_motion_agg", "GENERALITIES", null, "MOTION - agg.", "GENERALITIES - MOTION - agg.", 1, true, "gen_motion_amel");
            insertRR(psRR, "gen_motion_agg", "bry", 4, "k");
            insertRR(psRR, "gen_motion_agg", "bell", 3, "k");
            insertRR(psRR, "gen_motion_agg", "nux-v", 3, "k");
            insertRR(psRR, "gen_motion_agg", "chin", 3, "k");
            insertRR(psRR, "gen_motion_agg", "arn", 3, "k");

            insertRubric(psR, "gen_motion_amel", "GENERALITIES", null, "MOTION - amel.", "GENERALITIES - MOTION - amel.", 1, true, "gen_motion_agg");
            insertRR(psRR, "gen_motion_amel", "rhus-t", 4, "k");
            insertRR(psRR, "gen_motion_amel", "puls", 3, "k");
            insertRR(psRR, "gen_motion_amel", "ferr", 3, "k");
            insertRR(psRR, "gen_motion_amel", "kali-p", 2, "k");

            insertRubric(psR, "gen_right_side", "GENERALITIES", null, "SIDE - right", "GENERALITIES - SIDE - right", 1, false, null);
            insertRR(psRR, "gen_right_side", "lyc", 4, "k");
            insertRR(psRR, "gen_right_side", "bell", 4, "k");
            insertRR(psRR, "gen_right_side", "bry", 3, "k");
            insertRR(psRR, "gen_right_side", "chel", 3, "k");

            insertRubric(psR, "gen_left_side", "GENERALITIES", null, "SIDE - left", "GENERALITIES - SIDE - left", 1, false, null);
            insertRR(psRR, "gen_left_side", "lach", 4, "k");
            insertRR(psRR, "gen_left_side", "phos", 3, "k");
            insertRR(psRR, "gen_left_side", "thuj", 3, "k");
            insertRR(psRR, "gen_left_side", "spig", 3, "k");

            // ==========================================
            // 5. SKIN CHAPTER (Crucial for clinical photos)
            // ==========================================
            insertRubric(psR, "skin_eruptions_eczema", "SKIN", null, "ERUPTIONS - eczema", "SKIN - ERUPTIONS - eczema", 1, false, null);
            insertRR(psRR, "skin_eruptions_eczema", "sulph", 4, "k");
            insertRR(psRR, "skin_eruptions_eczema", "graph", 4, "k");
            insertRR(psRR, "skin_eruptions_eczema", "ars", 3, "k");
            insertRR(psRR, "skin_eruptions_eczema", "rhus-t", 3, "k");
            insertRR(psRR, "skin_eruptions_eczema", "petr", 3, "k");
            insertRR(psRR, "skin_eruptions_eczema", "psor", 3, "k");

            insertRubric(psR, "skin_eruptions_urticaria", "SKIN", null, "ERUPTIONS - urticaria", "SKIN - ERUPTIONS - urticaria", 1, false, null);
            insertRR(psRR, "skin_eruptions_urticaria", "apis", 4, "k");
            insertRR(psRR, "skin_eruptions_urticaria", "rhus-t", 3, "k");
            insertRR(psRR, "skin_eruptions_urticaria", "dulc", 3, "k");
            insertRR(psRR, "skin_eruptions_urticaria", "chlor", 2, "k");

            insertRubric(psR, "skin_itching_warmth_bed_agg", "SKIN", null, "ITCHING - warmth of bed - agg.", "SKIN - ITCHING - warmth of bed - agg.", 1, false, null);
            insertRR(psRR, "skin_itching_warmth_bed_agg", "sulph", 4, "k");
            insertRR(psRR, "skin_itching_warmth_bed_agg", "merc", 3, "k");
            insertRR(psRR, "skin_itching_warmth_bed_agg", "psor", 3, "k");
            insertRR(psRR, "skin_itching_warmth_bed_agg", "puls", 3, "k");

            psR.executeBatch();
            psRR.executeBatch();
        }
    }

    private static void insertRubric(PreparedStatement ps, String id, String chapter, String parentId,
                                     String rubricText, String fullPath, int depth, boolean isPolar, String oppId) throws SQLException {
        ps.setString(1, id);
        ps.setString(2, chapter);
        if (parentId != null) ps.setString(3, parentId); else ps.setNull(3, Types.VARCHAR);
        ps.setString(4, rubricText);
        ps.setString(5, fullPath);
        ps.setInt(6, depth);
        ps.setBoolean(7, isPolar);
        if (oppId != null) ps.setString(8, oppId); else ps.setNull(8, Types.VARCHAR);
        ps.addBatch();
    }

    private static void insertRR(PreparedStatement ps, String rubricId, String remedyId, int grade, String cit) throws SQLException {
        ps.setString(1, rubricId);
        ps.setString(2, remedyId);
        ps.setInt(3, grade);
        ps.setString(4, cit);
        ps.addBatch();
    }

    private static void buildFtsIndex(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE VIRTUAL TABLE rubrics_fts USING fts5(
                    id UNINDEXED,
                    chapter,
                    full_path,
                    content='rubrics',
                    content_rowid='rowid'
                );
            """);

            stmt.execute("""
                INSERT INTO rubrics_fts(rowid, id, chapter, full_path)
                SELECT rowid, id, chapter, full_path FROM rubrics;
            """);
        }
    }

    public static void verifyKnowledgeBase(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM remedies")) {
                if (rs.next()) log.info("Seeded Remedies count: {}", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM remedy_relationships")) {
                if (rs.next()) log.info("Seeded Relationships count: {}", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM materia_medica")) {
                if (rs.next()) log.info("Seeded Materia Medica chapters count: {}", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM rubrics")) {
                if (rs.next()) log.info("Seeded Rubrics count: {}", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM rubric_remedies")) {
                if (rs.next()) log.info("Seeded Rubric-Remedy linkages count: {}", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM rubrics_fts WHERE rubrics_fts MATCH 'cold'")) {
                if (rs.next()) log.info("FTS5 'cold' match count: {}", rs.getInt(1));
            }
        }
    }
}
