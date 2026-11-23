package org.openhomeo.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Automated Master Knowledge Base Generator and Seeder.
 * Compiles extensive international and Indian (HPI) remedies,
 * complete structured Materia Medica (Boericke, Allen, HPI monographs),
 * clinical relationships, and Kent & Boenninghausen repertory rubrics
 * into an optimized, pre-indexed master_repertory.db with SQLite FTS5.
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
                    is_polar BOOLEAN NOT NULL DEFAULT FALSE,
                    opposite_rubric_id TEXT REFERENCES rubrics(id)
                );
            """);
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
        String sql = "INSERT OR IGNORE INTO remedies VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // =========================================================================
            // CLASSICAL & INTERNATIONAL POLYCHRESTS
            // =========================================================================
            insertRemedy(ps, "acon", "Acon.", "Aconitum napellus", "Monkshood", "Plant", "Ranunculaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "all-c", "All-c.", "Allium cepa", "Red onion", "Plant", "Amaryllidaceae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "aloe", "Aloe", "Aloe socotrina", "Socotrine aloes", "Plant", "Liliaceae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "alum", "Alum.", "Alumina", "Oxide of aluminum", "Mineral", "Metals", 13, 3, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "anac", "Anac.", "Anacardium orientale", "Marking nut", "Plant", "Anacardiaceae", null, null, "SYPHILIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "ant-c", "Ant-c.", "Antimonium crudum", "Black sulphide of antimony", "Mineral", "Nitrogen Group", 15, 5, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "ant-t", "Ant-t.", "Antimonium tartaricum", "Tartar emetic", "Mineral", "Compounds", 15, 5, "SYCOSIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "apis", "Apis", "Apis mellifica", "Honey bee", "Animal", "Apidae", null, null, "SYCOSIS", "HOT", "THIRSTLESS");
            insertRemedy(ps, "arg-m", "Arg-m.", "Argentum metallicum", "Silver metal", "Mineral", "Silver Series", 11, 5, "SYCOSIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "arg-n", "Arg-n.", "Argentum nitricum", "Silver nitrate", "Mineral", "Silver Series", 11, 5, "SYCOSIS", "HOT", "THIRSTLESS");
            insertRemedy(ps, "arn", "Arn.", "Arnica montana", "Leopard's bane", "Plant", "Compositae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "ars", "Ars.", "Arsenicum album", "White oxide of arsenic", "Mineral", "Arsenic Group", 15, 4, "SYPHILIS", "CHILLY", "THIRSTY");
            insertRemedy(ps, "ars-i", "Ars-i.", "Arsenicum iodatum", "Iodide of arsenic", "Mineral", "Compounds", 15, 4, "TUBERCULAR", "HOT", "THIRSTY");
            insertRemedy(ps, "aur", "Aur.", "Aurum metallicum", "Metallic gold", "Mineral", "Gold Series", 11, 6, "SYPHILIS", "CHILLY", "THIRSTY");
            insertRemedy(ps, "bapt", "Bapt.", "Baptisia tinctoria", "Wild indigo", "Plant", "Leguminosae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "bar-c", "Bar-c.", "Baryta carbonica", "Carbonate of barium", "Mineral", "Barium Group", 2, 6, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "bell", "Bell.", "Belladonna", "Deadly nightshade", "Plant", "Solanaceae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "bell-p", "Bell-p.", "Bellis perennis", "English daisy", "Plant", "Compositae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "berb", "Berb.", "Berberis vulgaris", "Barberry", "Plant", "Berberidaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "borx", "Borx.", "Borax veneta", "Biborate of sodium", "Mineral", "Boron Group", 13, 2, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "bry", "Bry.", "Bryonia alba", "White bryony", "Plant", "Cucurbitaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "cact", "Cact.", "Cactus grandiflorus", "Night blooming cereus", "Plant", "Cactaceae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "calc", "Calc.", "Calcarea carbonica", "Carbonate of lime", "Mineral", "Alkaline Earths", 2, 4, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "calc-f", "Calc-f.", "Calcarea fluorica", "Fluoride of lime", "Mineral", "Calcium Salts", 2, 4, "SYPHILIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "calc-p", "Calc-p.", "Calcarea phosphorica", "Phosphate of lime", "Mineral", "Calcium Salts", 2, 4, "TUBERCULAR", "CHILLY", "THIRSTY");
            insertRemedy(ps, "calc-s", "Calc-s.", "Calcarea sulphurica", "Sulphate of lime (Gypsum)", "Mineral", "Calcium Salts", 2, 4, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "calen", "Calen.", "Calendula officinalis", "Marigold", "Plant", "Compositae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "camph", "Camph.", "Camphora", "Camphor", "Plant", "Lauraceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "cann-i", "Cann-i.", "Cannabis indica", "Indian hemp", "Plant", "Cannabaceae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "canth", "Canth.", "Cantharis vesicatoria", "Spanish fly", "Animal", "Cantharidae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "carb-v", "Carb-v.", "Carbo vegetabilis", "Vegetable charcoal", "Mineral", "Carbon Group", 14, 2, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "caul", "Caul.", "Caulophyllum thalictroides", "Blue cohosh", "Plant", "Berberidaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "caust", "Caust.", "Causticum", "Hahnemann's tinctura acris sine kali", "Mineral", "Potassium Compound", null, null, "SYCOSIS", "CHILLY", "THIRSTY");
            insertRemedy(ps, "cham", "Cham.", "Chamomilla", "German chamomile", "Plant", "Compositae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "chel", "Chel.", "Chelidonium majus", "Greater celandine", "Plant", "Papaveraceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "chin", "Chin.", "China officinalis", "Peruvian bark", "Plant", "Rubiaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "cic", "Cic.", "Cicuta virosa", "Water hemlock", "Plant", "Umbelliferae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "cina", "Cina", "Cina maritima", "Wormseed", "Plant", "Compositae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "cocc", "Cocc.", "Cocculus indicus", "Indian cockle", "Plant", "Menispermaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "coff", "Coff.", "Coffea cruda", "Unroasted coffee", "Plant", "Rubiaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "colch", "Colch.", "Colchicum autumnale", "Meadow saffron", "Plant", "Liliaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "coloc", "Coloc.", "Colocynthis", "Bitter cucumber", "Plant", "Cucurbitaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "con", "Con.", "Conium maculatum", "Poison hemlock", "Plant", "Umbelliferae", null, null, "SYPHILIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "cupr", "Cupr.", "Cuprum metallicum", "Metallic copper", "Mineral", "Copper Series", 11, 4, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "dig", "Dig.", "Digitalis purpurea", "Foxglove", "Plant", "Scrophulariaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "dros", "Dros.", "Drosera rotundifolia", "Sundew", "Plant", "Droseraceae", null, null, "TUBERCULAR", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "dulc", "Dulc.", "Dulcamara", "Bittersweet", "Plant", "Solanaceae", null, null, "SYCOSIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "echi", "Echi.", "Echinacea angustifolia", "Purple cone-flower", "Plant", "Compositae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "eup-per", "Eup-per.", "Eupatorium perfoliatum", "Boneset", "Plant", "Compositae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "euphr", "Euphr.", "Euphrasia officinalis", "Eyebright", "Plant", "Scrophulariaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "ferr", "Ferr.", "Ferrum metallicum", "Metallic iron", "Mineral", "Iron Series", 8, 4, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "ferr-p", "Ferr-p.", "Ferrum phosphoricum", "Phosphate of iron", "Mineral", "Iron Salts", 8, 4, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "fl-ac", "Fl-ac.", "Fluoricum acidum", "Hydrofluoric acid", "Mineral", "Acids", 17, 2, "SYPHILIS", "HOT", "THIRSTLESS");
            insertRemedy(ps, "gels", "Gels.", "Gelsemium sempervirens", "Yellow jessamine", "Plant", "Loganiaceae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "glon", "Glon.", "Glonoinum", "Nitroglycerine", "Mineral", "Compounds", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "graph", "Graph.", "Graphites", "Black lead / Mineral carbon", "Mineral", "Carbon Group", 14, 2, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "ham", "Ham.", "Hamamelis virginica", "Witch hazel", "Plant", "Hamamelidaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "hell", "Hell.", "Helleborus niger", "Black hellebore", "Plant", "Ranunculaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "hepar", "Hep.", "Hepar sulphuris calcareum", "Hahnemann's calcium sulphide", "Mineral", "Sulphide", 16, 4, "SYPHILIS", "CHILLY", "THIRSTY");
            insertRemedy(ps, "hydr", "Hydr.", "Hydrastis canadensis", "Golden seal", "Plant", "Ranunculaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "hyos", "Hyos.", "Hyoscyamus niger", "Henbane", "Plant", "Solanaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "hyper", "Hyper.", "Hypericum perforatum", "St. John's wort", "Plant", "Hypericaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "ign", "Ign.", "Ignatia amara", "St. Ignatius bean", "Plant", "Loganiaceae", null, null, "PSORA", "AMBITHERMAL", "THIRSTLESS");
            insertRemedy(ps, "iod", "Iod.", "Iodium", "Iodine", "Mineral", "Halogens", 17, 5, "TUBERCULAR", "HOT", "THIRSTY");
            insertRemedy(ps, "ip", "Ip.", "Ipecacuanha", "Ipecac root", "Plant", "Rubiaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "kali-bi", "Kali-bi.", "Kali bichromicum", "Potassium bichromate", "Mineral", "Chromium Salts", 6, 4, "SYCOSIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "kali-c", "Kali-c.", "Kali carbonicum", "Potassium carbonate", "Mineral", "Alkali Metals", 1, 4, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "kali-p", "Kali-p.", "Kali phosphoricum", "Phosphate of potassium", "Mineral", "Nerve Salts", 1, 4, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "kali-s", "Kali-s.", "Kali sulphuricum", "Sulphate of potassium", "Mineral", "Tissue Salts", 1, 4, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "kalm", "Kalm.", "Kalmia latifolia", "Mountain laurel", "Plant", "Ericaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "kreos", "Kreos.", "Kreosotum", "Beechwood kreosote", "Plant", "Phenols", null, null, "SYPHILIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "lac-c", "Lac-c.", "Lac caninum", "Bitch's milk", "Animal", "Mammals", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "lach", "Lach.", "Lachesis muta", "Bushmaster snake venom", "Animal", "Viperidae", null, null, "SYPHILIS", "HOT", "THIRSTY");
            insertRemedy(ps, "led", "Led.", "Ledum palustre", "Marsh tea", "Plant", "Ericaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "lyc", "Lyc.", "Lycopodium clavatum", "Club moss", "Plant", "Lycopodiaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "mag-c", "Mag-c.", "Magnesia carbonica", "Carbonate of magnesia", "Mineral", "Magnesium Salts", 2, 3, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "mag-p", "Mag-p.", "Magnesia phosphorica", "Phosphate of magnesia", "Mineral", "Tissue Salts", 2, 3, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "med", "Med.", "Medorrhinum", "Gonorrhoeal virus nosode", "Nosode", "Nosodes", null, null, "SYCOSIS", "HOT", "THIRSTY");
            insertRemedy(ps, "merc", "Merc.", "Mercurius solubilis", "Hahnemann's soluble mercury", "Mineral", "Mercury Series", 12, 6, "SYPHILIS", "AMBITHERMAL", "THIRSTY");
            insertRemedy(ps, "merc-c", "Merc-c.", "Mercurius corrosivus", "Corrosive sublimate", "Mineral", "Mercury Salts", 12, 6, "SYPHILIS", "AMBITHERMAL", "THIRSTY");
            insertRemedy(ps, "mez", "Mez.", "Mezereum", "Spurge olive", "Plant", "Thymelaeaceae", null, null, "SYPHILIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "nat-c", "Nat-c.", "Natrum carbonicum", "Carbonate of sodium", "Mineral", "Sodium Salts", 1, 3, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "nat-m", "Nat-m.", "Natrum muriaticum", "Common salt", "Mineral", "Halite", 1, 3, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "nat-p", "Nat-p.", "Natrum phosphoricum", "Phosphate of sodium", "Mineral", "Tissue Salts", 1, 3, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "nat-s", "Nat-s.", "Natrum sulphuricum", "Glauber's salt", "Mineral", "Tissue Salts", 1, 3, "SYCOSIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "nit-ac", "Nit-ac.", "Nitricum acidum", "Nitric acid", "Mineral", "Acids", 15, 2, "SYPHILIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "nux-m", "Nux-m.", "Nux moschata", "Nutmeg", "Plant", "Myristicaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "nux-v", "Nux-v.", "Nux vomica", "Poison nut", "Plant", "Loganiaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "op", "Op.", "Opium", "Poppy juice", "Plant", "Papaveraceae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "petr", "Petr.", "Petroleum", "Crude rock oil", "Mineral", "Hydrocarbons", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "ph-ac", "Ph-ac.", "Phosphoricum acidum", "Phosphoric acid", "Mineral", "Acids", 15, 3, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "phos", "Phos.", "Phosphorus", "Elemental phosphorus", "Mineral", "Phosphorus Group", 15, 3, "TUBERCULAR", "CHILLY", "THIRSTY");
            insertRemedy(ps, "phyt", "Phyt.", "Phytolacca decandra", "Poke root", "Plant", "Phytolaccaceae", null, null, "SYPHILIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "plat", "Plat.", "Platinum metallicum", "Metallic platinum", "Mineral", "Platinum Series", 10, 6, "SYPHILIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "plb", "Plb.", "Plumbum metallicum", "Metallic lead", "Mineral", "Lead Series", 14, 6, "SYPHILIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "podo", "Podo.", "Podophyllum peltatum", "May apple", "Plant", "Berberidaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "psor", "Psor.", "Psorinum", "Scabies vesicle nosode", "Nosode", "Nosodes", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "puls", "Puls.", "Pulsatilla nigricans", "Wind flower", "Plant", "Ranunculaceae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "pyrog", "Pyrog.", "Pyrogenium", "Artificial sepsine", "Nosode", "Nosodes", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "rhus-t", "Rhus-t.", "Rhus toxicodendron", "Poison ivy", "Plant", "Anacardiaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "ruta", "Ruta", "Ruta graveolens", "Rue", "Plant", "Rutaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "sec", "Sec.", "Secale cornutum", "Ergot of rye", "Plant", "Fungi", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "sep", "Sep.", "Sepia officinalis", "Cuttlefish ink", "Animal", "Cephalopoda", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "sil", "Sil.", "Silicea", "Pure flint / Silica", "Mineral", "Silicon Series", 14, 3, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "spig", "Spig.", "Spigelia anthelmia", "Pink root", "Plant", "Loganiaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "spong", "Spong.", "Spongia tosta", "Roasted sponge", "Animal", "Porifera", null, null, "TUBERCULAR", "HOT", "THIRSTY");
            insertRemedy(ps, "staph", "Staph.", "Staphysagria", "Stavesacre", "Plant", "Ranunculaceae", null, null, "SYCOSIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "stram", "Stram.", "Stramonium", "Thorn apple", "Plant", "Solanaceae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "sulph", "Sulph.", "Sulphur", "Brimstone / Sublimed sulphur", "Mineral", "Sulphur Group", 16, 3, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "sul-ac", "Sul-ac.", "Sulphuricum acidum", "Sulphuric acid", "Mineral", "Acids", 16, 3, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "symph", "Symph.", "Symphytum officinale", "Comfrey / Knitbone", "Plant", "Boraginaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "syph", "Syph.", "Syphilinum", "Syphilitic virus nosode", "Nosode", "Nosodes", null, null, "SYPHILIS", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "thuj", "Thuj.", "Thuja occidentalis", "Arbor vitae", "Plant", "Cupressaceae", null, null, "SYCOSIS", "CHILLY", "THIRSTY");
            insertRemedy(ps, "tub", "Tub.", "Tuberculinum bovinum", "Bacillus tuberculosis nosode", "Nosode", "Nosodes", null, null, "TUBERCULAR", "HOT", "THIRSTY");
            insertRemedy(ps, "verat", "Verat.", "Veratrum album", "White hellebore", "Plant", "Melanthiaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "zinc", "Zinc.", "Zincum metallicum", "Metallic zinc", "Mineral", "Zinc Series", 12, 4, "PSORA", "CHILLY", "THIRSTLESS");

            // =========================================================================
            // INDIAN INDIGENOUS & HPI (HOMOEOPATHIC PHARMACOPOEIA OF INDIA) REMEDIES
            // =========================================================================
            insertRemedy(ps, "azad-i", "Azad.", "Azadirachta indica", "Neem / Margosa tree", "Plant", "Meliaceae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "ocim-s", "Ocim-s.", "Ocimum sanctum", "Tulsi / Holy basil", "Plant", "Labiatae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "term-a", "Term-a.", "Terminalia arjuna", "Arjuna tree", "Plant", "Combretaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "syzyg-j", "Syzyg.", "Syzygium jambolanum", "Jamun / Black plum", "Plant", "Myrtaceae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "andro-p", "Andro-p.", "Andrographis paniculata", "Kalmegh / King of bitters", "Plant", "Acanthaceae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "ficus-r", "Fic-r.", "Ficus religiosa", "Ashwatha / Peepal tree", "Plant", "Moraceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "ficus-i", "Fic-i.", "Ficus indica", "Banyan / Indian fig tree", "Plant", "Moraceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "rauw-s", "Rauw.", "Rauwolfia serpentina", "Sarpagandha / Indian snakeroot", "Plant", "Apocynaceae", null, null, "SYCOSIS", "HOT", "THIRSTLESS");
            insertRemedy(ps, "gymn-s", "Gymn.", "Gymnema sylvestre", "Gurmar / Sugar destroyer", "Plant", "Asclepiadaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "boer-d", "Boer-d.", "Boerhaavia diffusa", "Punarnava / Spreading hogweed", "Plant", "Nyctaginaceae", null, null, "SYCOSIS", "HOT", "THIRSTY");
            insertRemedy(ps, "tinos-c", "Tinos.", "Tinospora cordifolia", "Giloy / Guduchi", "Plant", "Menispermaceae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "holar-a", "Holar.", "Holarrhena antidysenterica", "Kurchi / Conessi bark", "Plant", "Apocynaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "aegle-m", "Aegle-m.", "Aegle marmelos", "Bael / Bengal quince", "Plant", "Rutaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "calot-g", "Calot.", "Calotropis gigantea", "Madar / Akanda", "Plant", "Asclepiadaceae", null, null, "SYPHILIS", "HOT", "THIRSTLESS");
            insertRemedy(ps, "ceph-i", "Ceph-i.", "Cephalandra indica", "Telakucha / Ivy gourd", "Plant", "Cucurbitaceae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "hydro-a", "Hydro.", "Hydrocotyle asiatica", "Brahmi / Indian pennywort", "Plant", "Umbelliferae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "just-a", "Just.", "Justicia adhatoda", "Vasaka / Malabar nut", "Plant", "Acanthaceae", null, null, "TUBERCULAR", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "cass-f", "Cass-f.", "Cassia fistula", "Amaltas / Golden shower", "Plant", "Leguminosae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "trib-t", "Trib-t.", "Tribulus terrestris", "Gokshura / Puncture vine", "Plant", "Zygophyllaceae", null, null, "SYCOSIS", "HOT", "THIRSTY");
            insertRemedy(ps, "with-s", "With.", "Withania somnifera", "Ashwagandha / Indian ginseng", "Plant", "Solanaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "curc-l", "Curc.", "Curcuma longa", "Haldi / Turmeric", "Plant", "Zingiberaceae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "zing-o", "Zing.", "Zingiber officinale", "Ginger / Adrak", "Plant", "Zingiberaceae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "embel-r", "Embel.", "Embelia ribes", "Vidanga / False black pepper", "Plant", "Myrsinaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "saraca-i", "Sarac.", "Saraca indica", "Ashoka tree", "Plant", "Leguminosae", null, null, "SYCOSIS", "HOT", "THIRSTLESS");
            insertRemedy(ps, "swert-c", "Swert.", "Swertia chirata", "Chirata / East Indian balmony", "Plant", "Gentianaceae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "carica-p", "Caric.", "Carica papaya", "Papaya", "Plant", "Caricaceae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "momor-c", "Momor.", "Momordica charantia", "Karela / Bitter gourd", "Plant", "Cucurbitaceae", null, null, "PSORA", "HOT", "THIRSTY");
            insertRemedy(ps, "phyll-n", "Phyll.", "Phyllanthus niruri", "Bhumyamalaki / Stonebreaker", "Plant", "Euphorbiaceae", null, null, "PSORA", "CHILLY", "THIRSTY");
            insertRemedy(ps, "cynod-d", "Cynod.", "Cynodon dactylon", "Durva / Bermuda grass", "Plant", "Gramineae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "cass-s", "Cass-s.", "Cassia sophera", "Kasondi / Senna sophera", "Plant", "Leguminosae", null, null, "TUBERCULAR", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "acaly-i", "Acaly.", "Acalypha indica", "Muktajhuri / Indian nettle", "Plant", "Euphorbiaceae", null, null, "TUBERCULAR", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "abroma-a", "Abrom.", "Abroma augusta", "Olatkambal / Devil's cotton", "Plant", "Sterculiaceae", null, null, "SYCOSIS", "HOT", "THIRSTY");
            insertRemedy(ps, "alston-s", "Alst-s.", "Alstonia scholaris", "Chhatim / Dita bark", "Plant", "Apocynaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "blumea-o", "Blum.", "Blumea odorata", "Kukursunga", "Plant", "Compositae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "butea-f", "Butea", "Butea frondosa", "Palash / Flame of forest", "Plant", "Leguminosae", null, null, "PSORA", "HOT", "THIRSTLESS");
            insertRemedy(ps, "nyct-a", "Nyct.", "Nyctanthes arbor-tristis", "Parijat / Night jasmine", "Plant", "Oleaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "solan-x", "Sol-x.", "Solanum xanthocarpum", "Kantakari / Yellow berried nightshade", "Plant", "Solanaceae", null, null, "TUBERCULAR", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "term-c", "Term-c.", "Terminalia chebula", "Haritaki / Chebulic myrobalan", "Plant", "Combretaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "term-b", "Term-b.", "Terminalia belerica", "Bahera / Belleric myrobalan", "Plant", "Combretaceae", null, null, "PSORA", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "tylop-i", "Tylop.", "Tylophora indica", "Antamul / Indian ipecac", "Plant", "Asclepiadaceae", null, null, "TUBERCULAR", "CHILLY", "THIRSTLESS");
            insertRemedy(ps, "wright-t", "Wrigh.", "Wrightia tinctoria", "Indrajau / Sweet indrajao", "Plant", "Apocynaceae", null, null, "SYCOSIS", "HOT", "THIRSTLESS");

            // Ingest all additional remedies from exhaustive master dataset
            InputStream tempIn = KnowledgeBaseSeeder.class.getResourceAsStream("/data/master_medicines_exhaustive.tsv");
            if (tempIn == null) {
                File f = new File("../../openhomeo-core/src/main/resources/data/master_medicines_exhaustive.tsv");
                if (!f.exists()) f = new File("openhomeo-core/src/main/resources/data/master_medicines_exhaustive.tsv");
                if (f.exists()) {
                    try { tempIn = new FileInputStream(f); } catch (Exception ignored) {}
                }
            }
            if (tempIn != null) {
                try (InputStream in = tempIn; BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    boolean first = true;
                    while ((line = reader.readLine()) != null) {
                        if (first) { first = false; continue; }
                        String[] p = line.split("\t", -1);
                        if (p.length >= 3) {
                            String remId = p[0].trim();
                            String remName = p[1].trim();
                            String commName = p[2].trim();
                            insertRemedy(ps, remId, remId, remName, commName, "General", "Homeopathic", null, null, "PSORA", "AMBITHERMAL", "THIRSTLESS");
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Could not import additional remedies from TSV: {}", ex.getMessage());
                }
            }

            ps.executeBatch();
        }
    }

    private static void insertRemedy(PreparedStatement ps, String id, String abbr, String fullName,
                                     String commonName, String kingdom, String family,
                                     Integer stage, Integer series, String miasm,
                                     String thermal, String thirst) throws SQLException {
        ps.setString(1, id);
        ps.setString(2, abbr);
        ps.setString(3, fullName);
        ps.setString(4, commonName);
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
            // Classical Gibson Miller Clinical Relationships
            insertRel(ps, "acon", "sulph", "COMPLEMENTARY", "Sulphur completes cure of acute inflammatory storms begun by Aconite");
            insertRel(ps, "acon", "coff", "ANTIDOTE", "Wine and Coffea antidote toxic excitement of Aconite");
            insertRel(ps, "ars", "thuj", "COMPLEMENTARY", "Thuja is complementary in chronic asthma and cachexia");
            insertRel(ps, "ars", "nux-v", "FOLLOWS_WELL", "Nux vomica follows well after gastric derangements");
            insertRel(ps, "calc", "bell", "COMPLEMENTARY", "Belladonna is acute complementary of Calcarea carbonica");
            insertRel(ps, "calc", "rhus-t", "FOLLOWS_WELL", "Rhus tox follows Calcarea in joint strains");
            insertRel(ps, "calc", "bry", "INIMICAL", "Calcarea and Bryonia are inimical and should rarely follow closely");
            insertRel(ps, "caust", "phos", "INIMICAL", "Causticum and Phosphorus are mutually inimical");
            insertRel(ps, "lyc", "iod", "COMPLEMENTARY", "Iodium acts as a constitutional complement");
            insertRel(ps, "lyc", "puls", "FOLLOWS_WELL", "Pulsatilla frequently follows Lycopodium");
            insertRel(ps, "nat-m", "sep", "COMPLEMENTARY", "Sepia complements chronic uterine and melancholic states");
            insertRel(ps, "nux-v", "sep", "COMPLEMENTARY", "Sepia follows Nux in pelvic congestion");
            insertRel(ps, "phos", "calc", "COMPLEMENTARY", "Calcarea carbonica completes cure in scrofulous diathesis");
            insertRel(ps, "puls", "sil", "COMPLEMENTARY", "Silicea is chronic complementary to Pulsatilla");
            insertRel(ps, "rhus-t", "calc", "COMPLEMENTARY", "Calcarea carb is chronic of Rhus tox");
            insertRel(ps, "sep", "nat-m", "COMPLEMENTARY", "Natrum mur complements Sepia");
            insertRel(ps, "sil", "puls", "COMPLEMENTARY", "Pulsatilla complements Silicea in catarrhal states");
            insertRel(ps, "sulph", "acon", "COMPLEMENTARY", "Aconite acute storm yields to Sulphur");
            insertRel(ps, "sulph", "aloe", "COMPLEMENTARY", "Aloe is acute counterpart in early morning diarrhoea");
            insertRel(ps, "thuj", "med", "COMPLEMENTARY", "Medorrhinum deepens the constitutional sycotic response");

            // Indian Indigenous Clinical Relationships
            insertRel(ps, "azad-i", "sulph", "FOLLOWS_WELL", "Sulphur follows well in chronic obstinate dermatoses");
            insertRel(ps, "ocim-s", "ars", "COMPLEMENTARY", "Arsenicum complements Ocimum in malarial cachexia and respiratory catarrh");
            insertRel(ps, "term-a", "cact", "COMPLEMENTARY", "Cactus grandiflorus complements Terminalia arjuna in cardiac constriction");
            insertRel(ps, "term-a", "dig", "FOLLOWS_WELL", "Digitalis follows well in myocardial weakness and irregular pulse");
            insertRel(ps, "syzyg-j", "gymn-s", "COMPLEMENTARY", "Gymnema sylvestre acts complementarily to reduce glycosuria and polydipsia");
            insertRel(ps, "andro-p", "chel", "COMPLEMENTARY", "Chelidonium complements Kalmegh in chronic jaundice and gall-bladder sluggishness");
            insertRel(ps, "rauw-s", "bar-c", "FOLLOWS_WELL", "Baryta carb follows well in senile vascular hypertension and cerebral congestion");
            insertRel(ps, "boer-d", "apis", "COMPLEMENTARY", "Apis mellifica complements Punarnava in generalized dropsical effusions");
            insertRel(ps, "tinos-c", "chin", "FOLLOWS_WELL", "China follows Tinospora in post-febrile convalescence and anaemia");
            insertRel(ps, "just-a", "dros", "FOLLOWS_WELL", "Drosera follows well in nocturnal paroxysmal cough");
            insertRel(ps, "saraca-i", "puls", "COMPLEMENTARY", "Pulsatilla complements Ashoka in delayed menses and chlorosis");
            insertRel(ps, "holar-a", "merc-c", "COMPLEMENTARY", "Mercurius corrosivus complements Kurchi in severe tenesmus and bloody stool");

            ps.executeBatch();
        }
    }

    private static void insertRel(PreparedStatement ps, String rem1, String rem2, String type, String notes) throws SQLException {
        ps.setString(1, rem1 + "_" + rem2 + "_" + type.toLowerCase());
        ps.setString(2, rem1);
        ps.setString(3, rem2);
        ps.setString(4, type);
        ps.setString(5, notes);
        ps.addBatch();
    }

    private static void seedMateriaMedica(Connection conn) throws SQLException {
        String sql = "INSERT INTO materia_medica VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            // =========================================================================
            // 1. ACONITUM NAPELLUS
            // =========================================================================
            insertMM(ps, "acon", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "A state of fear, anxiety; physical and mental restlessness. Acute sudden and violent invasion with fever. Sudden chill followed by intense dry heat. Complaints caused by exposure to dry cold winds. Great general physical and mental restlessness.");
            insertMM(ps, "acon", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Great fear, anxiety, and worry with every complaint. Agonizing, unbearable fear of death; predicts the very day and hour of death. Fears crowds, crossing the busy street. Restless, tosses about in torment.");
            insertMM(ps, "acon", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Fullness, heavy pulsating, burning headache; as if brain were boiling out. Headache with sensation of boiling in brain. Vertigo upon rising, head feels enormously large and heavy.");
            insertMM(ps, "acon", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Unquenchable burning thirst for large quantities of cold water; everything tastes bitter except water. Burning from stomach up to throat. Intense vomiting with profuse sweat.");
            insertMM(ps, "acon", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: in warm room; toward evening and night; lying on affected side; from dry cold winds. Better: in open air; after perspiration.");

            // =========================================================================
            // 2. ALLIUM CEPA
            // =========================================================================
            insertMM(ps, "all-c", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Coryza with profuse acrid nasal discharge and bland lachrymation. Neuralgic pains like long threads in face, head, and neck. Spring coryza, hay-fever, attacks after cold damp winds.");
            insertMM(ps, "all-c", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Melancholic, anxious, confused sensorium during catarrhal headache. Averse to noise, lights, or mental exertion.");
            insertMM(ps, "all-c", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Catarrhal headache, mostly forehead and eyes; worse warm room, better open air. Thread-like pains across forehead and temple.");
            insertMM(ps, "all-c", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Canine hunger; appetite increased or lost; desires onions and raw food. Severe colicky pain from eating cold salads and cucumbers.");
            insertMM(ps, "all-c", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: in evening, in warm room, warm air. Better: in cold room, in open air.");

            // =========================================================================
            // 3. APIS MELLIFICA
            // =========================================================================
            insertMM(ps, "apis", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Edematous swelling, puffy under eyes; stinging burning pains; extreme intolerance of heat. Sudden shrill piercing scream in children (cri encephalique). Great debility with trembling.");
            insertMM(ps, "apis", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Cannot concentrate; absent-minded, awkward, drops things. Jealousy, rage, tearful, whining. Sudden shrieking in sleep.");
            insertMM(ps, "apis", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Brain feels tired and bruised; hydrocephalus; rolling head from side to side in stupor.");
            insertMM(ps, "apis", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Thirstlessness in almost all complaints; vomiting of food and bile. Abdomen sensitive to slightest touch.");
            insertMM(ps, "apis", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: heat in all forms, right side, afternoon (3 to 5 p.m.), touching. Better: open air, cold water applications, uncovering.");

            // =========================================================================
            // 4. ARNICA MONTANA
            // =========================================================================
            insertMM(ps, "arn", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Trauma, blunt impact, sprains, bruises, extravasation of blood. Sensation of bruised soreness all over body; bed feels too hard. Says there is nothing the matter with him.");
            insertMM(ps, "arn", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Fears being touched or approached. Unconscious; when aroused answers correctly, but falls back into stupor. Claims to be well when dangerously ill.");
            insertMM(ps, "arn", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Hot head with cold body; concussive headache after blunt blow. Confusion, vertigo upon closing eyes.");
            insertMM(ps, "arn", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Foul eructations tasting like rotten eggs. Aversion to meat and broth. Pressure in stomach as if from a stone.");
            insertMM(ps, "arn", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: slightest touch, motion, damp cold, rest. Better: lying down with head low, gentle support.");

            // =========================================================================
            // 5. ARSENICUM ALBUM
            // =========================================================================
            insertMM(ps, "ars", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Prostration out of proportion to urgency of disease. Burning pains relieved by heat. Rapid emaciation. Malignancy of symptoms, septic state. Cadaveric odor of discharges.");
            insertMM(ps, "ars", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Great anguish and restlessness; changes place continually. Intense fear of death, thinks it useless to take medicine. Fastidious, demands order and spotlessness.");
            insertMM(ps, "ars", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Headaches relieved by cold applications, but body must be warmly covered. Periodical headaches; intense burning.");
            insertMM(ps, "ars", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Intense thirst for small quantities of water at short intervals; water is vomited immediately it reaches stomach. Cannot bear smell or sight of food.");
            insertMM(ps, "ars", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: wet weather, cold drinks, after midnight (1 to 2 a.m.). Better: from heat in general, warm food and drinks, head elevated.");

            // =========================================================================
            // 6. BELLADONNA
            // =========================================================================
            insertMM(ps, "bell", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Suddenness of onset; violent throbbing; red flushed face, dilated pupils; hot dry burning skin. Radiates heat like an open furnace. Congestion to head.");
            insertMM(ps, "bell", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Furious delirium, bites, strikes, spits; tries to escape from bed. Sees ghosts, wild monsters, black animals. Hallucinations.");
            insertMM(ps, "bell", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Throbbing, pulsating headache; carotid arteries beat visibly. Sensation as if brain would burst through forehead.");
            insertMM(ps, "bell", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Aversion to meat and water; craves lemonade. Spasmodic pain in epigastrium; shooting to spine.");
            insertMM(ps, "bell", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: touch, jar, noise, draught of air, after 3 p.m., lying down. Better: semi-erect posture, quiet, warm room.");

            // =========================================================================
            // 7. BRYONIA ALBA
            // =========================================================================
            insertMM(ps, "bry", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Excessive dryness of mucous membranes. Stitching, tearing pains. Absolute aggravation from the slightest motion; must keep perfectly quiet. Holds chest when coughing.");
            insertMM(ps, "bry", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Irritable, angry at trifles. Delirium; talks constantly of business and daily affairs. Wants to go home when already at home.");
            insertMM(ps, "bry", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Bursting, splitting headache; as if forehead would split open; worse stooping, opening eyes. Sensation as if skull would crack.");
            insertMM(ps, "bry", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Great thirst for large draughts of cold water at long intervals. Dry mouth, cracked lips. Pressure as from a heavy stone after eating.");
            insertMM(ps, "bry", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: slightest motion, exertion, touch, morning, warmth. Better: lying on painful side, absolute rest, cold drinks, pressure.");

            // =========================================================================
            // 8. CALCAREA CARBONICA
            // =========================================================================
            insertMM(ps, "calc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Fair, fat, flabby, cold damp feet. Profuse perspiration about head, especially during sleep, wetting pillow. Slow development, late closing of fontanelles, delayed dentition.");
            insertMM(ps, "calc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Apprehensive, fears loss of reason, insanity, fatal disease. Easily frightened, thinks people look at her with suspicion.");
            insertMM(ps, "calc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Coldness in and on head; icy cold sensations. Profuse sour sweat on scalp during sleep.");
            insertMM(ps, "calc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Craves boiled eggs, chalk, coal, indigestible things. Aversion to meat and milk. Distension of stomach like an inverted saucer.");
            insertMM(ps, "calc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: cold, damp air, wet weather, full moon, physical or mental exertion. Better: dry weather, lying on painful side.");

            // =========================================================================
            // 9. CAUSTICUM
            // =========================================================================
            insertMM(ps, "caust", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Manifests action in motor paralysis, vocal cords, bladder sphincter. Burns, raw sore sensations. Involuntary urination when coughing or sneezing.");
            insertMM(ps, "caust", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Intensely sympathetic for others' suffering; cannot bear injustice. Hopeless, fearful, anxious at twilight.");
            insertMM(ps, "caust", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Paralytic weakness; facial paralysis, usually right-sided from cold dry winds.");
            insertMM(ps, "caust", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Aversion to sweets; desires smoked meat. Sensation of burning rawness in stomach.");
            insertMM(ps, "caust", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: dry cold winds, clear fine weather. Better: damp wet weather, warm air, warm bath.");

            // =========================================================================
            // 10. GELSEMIUM SEMPERVIRENS
            // =========================================================================
            insertMM(ps, "gels", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "The four D's: Dizziness, Drowsiness, Dullness, and Drooping eyelids. Muscular weakness, motor paralysis, trembling from anticipation or bad news.");
            insertMM(ps, "gels", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Bad effects from fright, fear, exciting news; anticipation of stage examination, interview causing diarrhoea. Apathy.");
            insertMM(ps, "gels", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Occipital headache extending over vertex into forehead; relieved by profuse urination. Heavy drooping eyelids; double vision.");
            insertMM(ps, "gels", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Complete thirstlessness; sensation of emptiness in stomach. Diarrhea after emotional excitement.");
            insertMM(ps, "gels", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: damp weather, fog, before a thunderstorm, emotions, anticipation. Better: profuse urination, open air, bending forward.");

            // =========================================================================
            // 11. LYCOPODIUM CLAVATUM
            // =========================================================================
            insertMM(ps, "lyc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Right-sided remedy; complaints travel right to left, from above downwards. Aggravation 4 to 8 p.m. Deep digestive and urinary affections. Premature old age.");
            insertMM(ps, "lyc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Melancholy; afraid to be alone. Loss of self-confidence yet tyrannical at home. Averse to undertaking anything new; performs well once started.");
            insertMM(ps, "lyc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Throbbing headache worse warm room; premature graying of hair; fan-like motion of alae nasi in pneumonia.");
            insertMM(ps, "lyc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Dyspepsia from farinaceous food; excessive flatulence; eating ever so little creates intense fullness. Desire for sweets and hot drinks.");
            insertMM(ps, "lyc", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: 4 to 8 p.m., right side, warm room. Better: warm food and drinks, motion, cool open air.");

            // =========================================================================
            // 12. NATRUM MURIATICUM
            // =========================================================================
            insertMM(ps, "nat-m", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Great emaciation, losing flesh while eating well; especially about the neck. Anaemic, cachectic, greasy skin. Aversion to sun heat. Mapping of tongue.");
            insertMM(ps, "nat-m", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Psychic depression; consolation aggravates. Weeps when alone; dwells upon past grievances. Laughs at serious things; awkward, drops objects.");
            insertMM(ps, "nat-m", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Hammering, throbbing headache as if bursting; comes on with rising sun and lasts until sunset. Blinding headache.");
            insertMM(ps, "nat-m", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Craves salt; unquenchable thirst. Deep crack in middle of lower lip. Bitter taste.");
            insertMM(ps, "nat-m", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: noise, mental exertion, sun heat, seaside, 10 to 11 a.m. Better: open air, cold bathing, lying on right side.");

            // =========================================================================
            // 13. NUX VOMICA
            // =========================================================================
            insertMM(ps, "nux-v", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Sedentary habits, high living, business executives, wine, spices, narcotics. Frequent ineffectual urging to stool; passes little. Over-sensitive to all impressions.");
            insertMM(ps, "nux-v", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Fiery, excitable, easily angered; irritable; cannot bear noise, light, odors. Fault-finding, spiteful, impatient.");
            insertMM(ps, "nux-v", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Frontal headache over eyes on waking in morning; from hangover or late hours. Scalp sensitive to touch.");
            insertMM(ps, "nux-v", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Sour taste and nausea in morning. Pressure in stomach an hour or two after eating, as from a stone. Loves fats, spices, stimulants.");
            insertMM(ps, "nux-v", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: morning, mental exertion, spices, stimulants, cold dry weather. Better: from a nap, in evening, strong pressure, damp wet weather.");

            // =========================================================================
            // 14. PHOSPHORUS
            // =========================================================================
            insertMM(ps, "phos", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Tall, slender, narrow-chested, growing too rapidly. Tendency to frequent haemorrhages of bright red blood. Burning sensations along spine and between scapulae.");
            insertMM(ps, "phos", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Sympathetic, highly affectionate, clairvoyant; great fear of darkness, thunderstorms, being alone, sickness.");
            insertMM(ps, "phos", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Congestive headache with burning heat; relieved by cold applications and washing in cold water. Vertigo on rising.");
            insertMM(ps, "phos", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Craves ice-cold water and ice cream; as soon as cold water becomes warm in stomach it is vomited. Canine hunger soon after eating.");
            insertMM(ps, "phos", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: physical or mental exertion, twilight, thunderstorms, lying on left side. Better: in dark, lying on right side, cold food and drink, sleep.");

            // =========================================================================
            // 15. PULSATILLA NIGRICANS
            // =========================================================================
            insertMM(ps, "puls", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Changeable symptoms; wander from joint to joint; discharges thick, bland, yellowish-green. Chilly yet suffocated in warm room; craves open cool air.");
            insertMM(ps, "puls", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Mild, gentle, weeping disposition; weeps while relating her complaints. Consolation relieves; craves sympathy and affectionate support.");
            insertMM(ps, "puls", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Wandering headache, neuralgic; worse in warm room, better slowly walking in open air.");
            insertMM(ps, "puls", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Complete thirstlessness with dry mouth. Aversion to fat food, butter, pork; pastry produces heartburn and dyspepsia.");
            insertMM(ps, "puls", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: from heat, rich fat food, warm room, evening, lying on left side. Better: open air, slow gentle motion, cold food and drinks.");

            // =========================================================================
            // 16. RHUS TOXICODENDRON
            // =========================================================================
            insertMM(ps, "rhus-t", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Ailments from spraining, over-lifting, getting wet while overheated. Restlessness; cannot keep still; initial motion painful, continuous motion relieves.");
            insertMM(ps, "rhus-t", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Restless, changes place continually; apprehensive anxiety worse at night; suicidal thoughts with fear of death.");
            insertMM(ps, "rhus-t", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Occipital headache; brain feels loose when stepping. Red triangular tip of tongue.");
            insertMM(ps, "rhus-t", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Dry mouth, great thirst for cold drinks; craves milk. Loss of appetite for anything but cold water.");
            insertMM(ps, "rhus-t", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: cold, damp rainy weather, during rest, sitting still, beginning of motion. Better: continued motion, dry warm weather, heat.");

            // =========================================================================
            // 17. SEPIA OFFICINALIS
            // =========================================================================
            insertMM(ps, "sep", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Venous stasis; bearing-down sensation as if everything would escape through vulva; must cross legs. Brown saddle across nose. Indifference to loved ones.");
            insertMM(ps, "sep", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Indifferent to business, family, those loved most. Irritable, easily offended; wants to get away and be quiet. Weeps when questioned.");
            insertMM(ps, "sep", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Hemicrania, left-sided; drawing, boring pains; better lying on painful side.");
            insertMM(ps, "sep", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Nausea at smell or sight of food. Empty 'all-gone' sensation in pit of stomach, not relieved by eating. Craves vinegar and pickles.");
            insertMM(ps, "sep", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: morning and evening, cold damp wind, laundry work, before thunderstorm. Better: vigorous exercise, violent motion, warmth.");

            // =========================================================================
            // 18. SILICEA
            // =========================================================================
            insertMM(ps, "sil", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "Imperfect assimilation and defective nutrition; chilly, wants warm wraps even in summer. Lack of vital heat. Promotes expulsion of foreign bodies.");
            insertMM(ps, "sil", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Yielding, faint-hearted, anxious; lacks self-confidence, dreads failure yet executes tasks with extreme conscientiousness.");
            insertMM(ps, "sil", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Headache starting from nape of neck, going over vertex and settling over right eye; relieved by wrapping head warmly.");
            insertMM(ps, "sil", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Aversion to warm cooked food; desires cold raw things. Disgust for meat. Sour eructations after eating.");
            insertMM(ps, "sil", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: cold, draughts of air, damp, new moon, mental exertion. Better: warmth, wrapping head warmly, profuse urination.");

            // =========================================================================
            // 19. SULPHUR
            // =========================================================================
            insertMM(ps, "sulph", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "The great Hahnemannian antipsoric. Standing is most uncomfortable position; dirty, filthy people, prone to skin eruptions. Burning soles; sticks feet out of bed.");
            insertMM(ps, "sulph", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Philosophical mania; dwells on religious or philosophical speculations. Selfish, ragged philosopher; believes old rags are precious silks.");
            insertMM(ps, "sulph", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Heat on vertex; morning headache starting in back of head; worse weekend and Sundays. Scalp dry, pruritic.");
            insertMM(ps, "sulph", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Empty, gone, faint feeling in epigastrium at 11 a.m.; must have food. Canine hunger or loss of appetite. Craves sweets.");
            insertMM(ps, "sulph", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: at rest, when standing, warmth in bed, washing, bathing, 11 a.m. Better: dry warm weather, lying on right side.");

            // =========================================================================
            // 20. THUJA OCCIDENTALIS
            // =========================================================================
            insertMM(ps, "thuj", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "GENERALITIES",
                    "The chief Hahnemannian antipsycotic. Warty excrescences, condylomata, polypi, fig-warts. Bad effects of vaccination (vaccinosis). Sweat smelling like boiled honey.");
            insertMM(ps, "thuj", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MIND",
                    "Fixed ideas: as if a strange person were by his side; as if body and soul were separated; as if limbs were made of glass and would easily break.");
            insertMM(ps, "thuj", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "HEAD",
                    "Headache as if a nail were driven into vertex; or piercing left frontal eminence; worse tea, sun, 3 p.m.");
            insertMM(ps, "thuj", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "STOMACH",
                    "Complete aversion to meat and onions. Rapid satiety after few mouthfuls. Abdomen enlarged like an animal moving inside.");
            insertMM(ps, "thuj", "William Boericke", "Pocket Manual of Homeopathic Materia Medica", "MODALITIES",
                    "Worse: damp wet cold, 3 a.m. and 3 p.m., after vaccination, tea, fat food. Better: left side, dry warmth.");

            // =========================================================================
            // 21. AZADIRACHTA INDICA (NEEM - HPI)
            // =========================================================================
            insertMM(ps, "azad-i", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "GENERALITIES",
                    "Chief Indian blood purifier and antipyretic. Powerful action on skin and gastro-intestinal tract. Chronic fevers, intermitting type, with severe aching along spine and glowing heat in palms. Urticaria, boils, carbuncles, eczema with intense itching.");
            insertMM(ps, "azad-i", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MIND",
                    "Forgetful, depressed, irritable; cannot concentrate on intellectual pursuits. Anxious dreams of quarrelling and wild beasts.");
            insertMM(ps, "azad-i", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "HEAD",
                    "Throbbing ache in right temple, relieved by firm pressure. Sensation as if skull were cracked. Scalp painful with dry scaling dandruff.");
            insertMM(ps, "azad-i", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "STOMACH",
                    "Clammy mouth with bitter taste. Great thirst for large quantities of water. Burning in epigastrium with vomiting of bilious matter. Liver tender on pressure.");
            insertMM(ps, "azad-i", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MODALITIES",
                    "Worse: in afternoon (3 to 5 p.m.), touching scalp, heat of sun. Better: cold applications, open air, pressure.");

            // =========================================================================
            // 22. OCIMUM SANCTUM (TULSI - HPI)
            // =========================================================================
            insertMM(ps, "ocim-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "GENERALITIES",
                    "Adapted to intermittent and remittent fevers with profound liver congestion. Acute bronchitis and catarrhal fevers. Renal colic with red sand and brick-dust sediment in urine. General anti-inflammatory and adaptogenic vitality stimulator.");
            insertMM(ps, "ocim-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MIND",
                    "Restless, confused during febrile state. Anxiety about family members. Drowsiness with delirious mutterings during fever.");
            insertMM(ps, "ocim-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "HEAD",
                    "Severe throbbing headache with redness of eyes and hot face, aggravated by sun heat and light. Catarrhal heaviness in forehead.");
            insertMM(ps, "ocim-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "STOMACH",
                    "Thirst for cold water during paroxysm of fever. Nausea and vomiting of bile. Flatulent distension after meals.");
            insertMM(ps, "ocim-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MODALITIES",
                    "Worse: exposure to cold air, evening, movement. Better: warm wraps, rest, warm drinks.");

            // =========================================================================
            // 23. TERMINALIA ARJUNA (ARJUNA - HPI)
            // =========================================================================
            insertMM(ps, "term-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "GENERALITIES",
                    "Premier Indian cardiac remedy and coronary tonic. Indicated in angina pectoris, myocardial insufficiency, palpitations with suffocative feeling, post-infarction rehabilitation, and essential hypertension. Restores regular cardiac rhythm and ventricular power.");
            insertMM(ps, "term-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MIND",
                    "Anxiety about cardiac health; sudden fear of heart failure. Despondent, nervous trembling, sleepless from cardiac awareness.");
            insertMM(ps, "term-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "HEAD",
                    "Vertigo on sudden standing; fullness and throbbing in temporal vessels due to hypertensive pressure.");
            insertMM(ps, "term-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "STOMACH",
                    "Epigastric pulsation; digestive weakness with sense of sinking. Thirstless; poor assimilation of meals.");
            insertMM(ps, "term-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MODALITIES",
                    "Worse: ascending stairs, physical exertion, emotional shock, lying on left side. Better: quiet rest, erect posture, fresh air.");

            // =========================================================================
            // 24. SYZYGIUM JAMBOLANUM (JAMUN - HPI)
            // =========================================================================
            insertMM(ps, "syzyg-j", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "GENERALITIES",
                    "Unsurpassed remedy in Diabetes Mellitus. Prompts rapid reduction of sugar in urine. Marked polyuria, high specific gravity, extreme weakness, emaciation. Diabetic ulcers and furuncles.");
            insertMM(ps, "syzyg-j", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MIND",
                    "Melancholy, weary of life, anxious about physical decline and loss of vigor.");
            insertMM(ps, "syzyg-j", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "HEAD",
                    "Dull heavy frontal ache with profound dryness of tongue and throat.");
            insertMM(ps, "syzyg-j", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "STOMACH",
                    "Insatiable canine hunger; unquenchable thirst, drinks quarts of water. Profuse urination, 8 to 10 pints daily.");
            insertMM(ps, "syzyg-j", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MODALITIES",
                    "Worse: cold, night, sweets, starchy food. Better: regulated diet, warm baths.");

            // =========================================================================
            // 25. ANDROGRAPHIS PANICULATA (KALMEGH - HPI)
            // =========================================================================
            insertMM(ps, "andro-p", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "GENERALITIES",
                    "Renowned hepatoprotective agent of the Indian Pharmacopoeia. Indicated in chronic sluggish liver, jaundice, infantile jaundice, post-dengue/malarial debility. Heaviness in right hypochondrium with yellow coating of tongue.");
            insertMM(ps, "andro-p", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MIND",
                    "Extremely irritable and sullen; refuses to talk. Dull, drowsy during day.");
            insertMM(ps, "andro-p", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "HEAD",
                    "Bilious headache over forehead and right eye, relieved by vomiting bile.");
            insertMM(ps, "andro-p", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "STOMACH",
                    "Intense bitter taste; anorexia; complete loss of appetite. Liver enlarged and tender to touch; clay-colored stools.");
            insertMM(ps, "andro-p", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MODALITIES",
                    "Worse: fatty food, rich diet, morning, humid heat. Better: cool drinks, fresh air, lying on right side.");

            // =========================================================================
            // 26. RAUWOLFIA SERPENTINA (SARPAGANDHA - HPI)
            // =========================================================================
            insertMM(ps, "rauw-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "GENERALITIES",
                    "Specific for essential hypertension with marked neuropsychiatric agitation. Violent insomnia, high blood pressure with mental confusion and dizziness. Relaxes arteriolar tone without depression of cardiac output.");
            insertMM(ps, "rauw-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MIND",
                    "Insanity, violent mania, delirium, unprovoked anger, emotional volatility. Extreme anxiety neurosis with tachycardia.");
            insertMM(ps, "rauw-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "HEAD",
                    "Throbbing congestive headache in occiput; sensation of tightness around skull.");
            insertMM(ps, "rauw-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "STOMACH",
                    "Dryness of throat; increased appetite with craving for spices.");
            insertMM(ps, "rauw-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MODALITIES",
                    "Worse: emotional agitation, after midnight, mental tension. Better: calm surroundings, cool breeze, quiet rest.");

            // =========================================================================
            // 27. JUSTICIA ADHATODA (VASAKA - HPI)
            // =========================================================================
            insertMM(ps, "just-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "GENERALITIES",
                    "Invaluable respiratory remedy of the Indian flora. Paroxysmal suffocative cough, whooping cough, acute catarrhal bronchitis, haemoptysis. Tightness across chest with dry barking cough followed by tenacious expectoration.");
            insertMM(ps, "just-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MIND",
                    "Irritable and impatient during coughing spasms; anxiety about suffocating.");
            insertMM(ps, "just-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "HEAD",
                    "Frontal headache with coryza, sneezing, burning in pharynx.");
            insertMM(ps, "just-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "STOMACH",
                    "Loss of appetite; vomiting with cough from violent gagging.");
            insertMM(ps, "just-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MODALITIES",
                    "Worse: lying down, night, entering warm room, dust. Better: sitting erect, warm sips.");

            // =========================================================================
            // 28. HYDROCOTYLE ASIATICA (BRAHMI - HPI)
            // =========================================================================
            insertMM(ps, "hydro-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "GENERALITIES",
                    "Celebrated Indian remedy for cutaneous hypertrophy, psoriasis, elephantiasis, scleroderma, and circular scaly lesions. Renowned also as a nervine revitalizer improving cerebral concentration and cognitive clarity.");
            insertMM(ps, "hydro-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MIND",
                    "Mental clarity, dispels intellectual fatigue and memory weakness.");
            insertMM(ps, "hydro-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "HEAD",
                    "Scaly eruption on scalp with circular crusts and loss of hair.");
            insertMM(ps, "hydro-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "STOMACH",
                    "Anorexia, clean tongue, slight burning at pylorus.");
            insertMM(ps, "hydro-a", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MODALITIES",
                    "Worse: heat of bed, damp weather. Better: open air, cool washing.");

            // =========================================================================
            // 29. WITHANIA SOMNIFERA (ASHWAGANDHA - HPI)
            // =========================================================================
            insertMM(ps, "with-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "GENERALITIES",
                    "The premier Ayurvedic-Homeopathic revitalizing adaptogen. Debility, nervous exhaustion, loss of muscular power, emaciation in children and the elderly. Impotence, spermatorrhoea, and restorative tonic after chronic debilitating diseases.");
            insertMM(ps, "with-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MIND",
                    "Loss of memory, mental brain-fag, nervous breakdown from overwork. Chronic insomnia with daytime lethargy.");
            insertMM(ps, "with-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "HEAD",
                    "Dull aching heaviness in vertex and occiput with mental fatigue.");
            insertMM(ps, "with-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "STOMACH",
                    "Weak digestion, flatulent colic, impaired absorption of nutrients.");
            insertMM(ps, "with-s", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MODALITIES",
                    "Worse: mental over-exertion, sexual excess, loss of sleep. Better: nourishing warm milk, rest, warmth.");

            // =========================================================================
            // 30. TINOSPORA CORDIFOLIA (GILOY / GUDUCHI - HPI)
            // =========================================================================
            insertMM(ps, "tinos-c", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "GENERALITIES",
                    "Renowned Indian immune-modulator and antipyretic. Chronic intermittent fevers, malarial and viral fevers with splenomegaly and general prostration. Arthritic pains, raised uric acid, gout, and restorative after prolonged viral attacks.");
            insertMM(ps, "tinos-c", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MIND",
                    "Dull, listless during fever; irritable on being questioned.");
            insertMM(ps, "tinos-c", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "HEAD",
                    "Severe bursting frontal headache accompanied by burning eyes.");
            insertMM(ps, "tinos-c", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "STOMACH",
                    "Extreme thirst during febrile state; bilious vomiting; enlarged spleen.");
            insertMM(ps, "tinos-c", "Homoeopathic Pharmacopoeia of India", "Monographs of Indian Medicinal Plants", "MODALITIES",
                    "Worse: motion, morning, heat. Better: quiet rest, cool environment.");

            // Seed Clinical Rhymes & Keynotes for all remedies that have verses
            try (Statement sRem = conn.createStatement();
                 ResultSet rsRem = sRem.executeQuery("SELECT id, full_name, common_name FROM remedies")) {
                while (rsRem.next()) {
                    String remId = rsRem.getString("id");
                    String remFullName = rsRem.getString("full_name");
                    String remCommon = rsRem.getString("common_name");
                    String rhyme = org.openhomeo.core.materia_medica.HomeopathicClinicalRhymes.findRhyme(remId, remFullName, remCommon);
                    if (rhyme != null) {
                        insertMM(ps, remId, "Classical Clinical Mnemonic Verses",
                                "Clinical Rhymes & Keynotes for Remedy Selection", "CLINICAL RHYMES & KEYNOTES",
                                "🎵 CLINICAL RHYME & KEYNOTE FOR MEDICINE CHOOSING:\n\n" + rhyme);
                    }
                }
            }

            ps.executeBatch();
        }
    }

    private static void insertMM(PreparedStatement ps, String remedyId, String author, String book, String section, String body) throws SQLException {
        ps.setString(1, remedyId + "_" + author.replaceAll("[^a-zA-Z0-9]", "").toLowerCase() + "_" + section.toLowerCase());
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

            // =========================================================================
            // 1. MIND CHAPTER
            // =========================================================================
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
            insertRR(psRR, "mind_anxiety", "rauw-s", 3, "hpi");
            insertRR(psRR, "mind_anxiety", "with-s", 3, "hpi");
            insertRR(psRR, "mind_anxiety", "term-a", 2, "hpi");

            insertRubric(psR, "mind_anxiety_evening", "MIND", "mind_anxiety", "evening", "MIND - ANXIETY - evening", 1, false, null);
            insertRR(psRR, "mind_anxiety_evening", "ars", 4, "k");
            insertRR(psRR, "mind_anxiety_evening", "calc", 3, "k");
            insertRR(psRR, "mind_anxiety_evening", "puls", 3, "k");
            insertRR(psRR, "mind_anxiety_evening", "acon", 2, "k");

            insertRubric(psR, "mind_anxiety_evening_bed", "MIND", "mind_anxiety_evening", "bed, in", "MIND - ANXIETY - evening - bed, in", 2, false, null);
            insertRR(psRR, "mind_anxiety_evening_bed", "ars", 4, "k");
            insertRR(psRR, "mind_anxiety_evening_bed", "calc", 3, "k");
            insertRR(psRR, "mind_anxiety_evening_bed", "puls", 3, "k");

            insertRubric(psR, "mind_fear_death", "MIND", null, "FEAR - death, of", "MIND - FEAR - death, of", 0, false, null);
            insertRR(psRR, "mind_fear_death", "acon", 4, "k");
            insertRR(psRR, "mind_fear_death", "ars", 4, "k");
            insertRR(psRR, "mind_fear_death", "phos", 3, "k");
            insertRR(psRR, "mind_fear_death", "calc", 3, "k");
            insertRR(psRR, "mind_fear_death", "term-a", 3, "hpi");

            insertRubric(psR, "mind_weeping", "MIND", null, "WEEPING - tearful mood", "MIND - WEEPING - tearful mood", 0, false, null);
            insertRR(psRR, "mind_weeping", "puls", 4, "k");
            insertRR(psRR, "mind_weeping", "sep", 3, "k");
            insertRR(psRR, "mind_weeping", "nat-m", 3, "k");
            insertRR(psRR, "mind_weeping", "ign", 3, "k");

            insertRubric(psR, "mind_irritability", "MIND", null, "IRRITABILITY", "MIND - IRRITABILITY", 0, false, null);
            insertRR(psRR, "mind_irritability", "nux-v", 4, "k");
            insertRR(psRR, "mind_irritability", "cham", 4, "k");
            insertRR(psRR, "mind_irritability", "bry", 3, "k");
            insertRR(psRR, "mind_irritability", "lyc", 3, "k");
            insertRR(psRR, "mind_irritability", "andro-p", 3, "hpi");
            insertRR(psRR, "mind_irritability", "azad-i", 2, "hpi");

            // =========================================================================
            // 2. HEAD CHAPTER
            // =========================================================================
            insertRubric(psR, "head_pain_motion_agg", "HEAD", null, "PAIN - motion agg.", "HEAD - PAIN - motion agg.", 0, true, "head_pain_motion_amel");
            insertRR(psRR, "head_pain_motion_agg", "bry", 4, "k");
            insertRR(psRR, "head_pain_motion_agg", "bell", 4, "k");
            insertRR(psRR, "head_pain_motion_agg", "spig", 3, "k");
            insertRR(psRR, "head_pain_motion_agg", "acon", 3, "k");

            insertRubric(psR, "head_pain_motion_amel", "HEAD", null, "PAIN - motion amel.", "HEAD - PAIN - motion amel.", 0, true, "head_pain_motion_agg");
            insertRR(psRR, "head_pain_motion_amel", "rhus-t", 3, "k");
            insertRR(psRR, "head_pain_motion_amel", "puls", 3, "k");

            insertRubric(psR, "head_pain_cold_air_agg", "HEAD", null, "PAIN - cold air agg.", "HEAD - PAIN - cold air agg.", 0, false, null);
            insertRR(psRR, "head_pain_cold_air_agg", "sil", 4, "k");
            insertRR(psRR, "head_pain_cold_air_agg", "ars", 4, "k");
            insertRR(psRR, "head_pain_cold_air_agg", "chin", 3, "k");
            insertRR(psRR, "head_pain_cold_air_agg", "hepar", 3, "k");

            insertRubric(psR, "head_pain_sun_agg", "HEAD", null, "PAIN - sun, from exposure to", "HEAD - PAIN - sun, from exposure to", 0, false, null);
            insertRR(psRR, "head_pain_sun_agg", "glon", 4, "k");
            insertRR(psRR, "head_pain_sun_agg", "nat-m", 4, "k");
            insertRR(psRR, "head_pain_sun_agg", "bell", 3, "k");
            insertRR(psRR, "head_pain_sun_agg", "ocim-s", 3, "hpi");

            // =========================================================================
            // 3. STOMACH & ABDOMEN CHAPTER
            // =========================================================================
            insertRubric(psR, "stomach_thirst_large_quantities", "STOMACH", null, "THIRST - large quantities", "STOMACH - THIRST - large quantities", 0, false, null);
            insertRR(psRR, "stomach_thirst_large_quantities", "bry", 4, "k");
            insertRR(psRR, "stomach_thirst_large_quantities", "acon", 4, "k");
            insertRR(psRR, "stomach_thirst_large_quantities", "nat-m", 3, "k");
            insertRR(psRR, "stomach_thirst_large_quantities", "syzyg-j", 4, "hpi");
            insertRR(psRR, "stomach_thirst_large_quantities", "azad-i", 3, "hpi");

            insertRubric(psR, "stomach_thirst_small_quantities", "STOMACH", null, "THIRST - small quantities often", "STOMACH - THIRST - small quantities often", 0, false, null);
            insertRR(psRR, "stomach_thirst_small_quantities", "ars", 4, "k");
            insertRR(psRR, "stomach_thirst_small_quantities", "chin", 3, "k");
            insertRR(psRR, "stomach_thirst_small_quantities", "bell", 2, "k");

            insertRubric(psR, "stomach_thirst_unquenchable", "STOMACH", null, "THIRST - unquenchable", "STOMACH - THIRST - unquenchable", 0, true, "stomach_thirstless");
            insertRR(psRR, "stomach_thirst_unquenchable", "acon", 4, "k");
            insertRR(psRR, "stomach_thirst_unquenchable", "ars", 4, "k");
            insertRR(psRR, "stomach_thirst_unquenchable", "bry", 4, "k");
            insertRR(psRR, "stomach_thirst_unquenchable", "syzyg-j", 4, "hpi");
            insertRR(psRR, "stomach_thirst_unquenchable", "azad-i", 3, "hpi");
            insertRR(psRR, "stomach_thirst_unquenchable", "tinos-c", 3, "hpi");

            insertRubric(psR, "stomach_thirstless", "STOMACH", null, "THIRSTLESS", "STOMACH - THIRSTLESS", 0, true, "stomach_thirst_unquenchable");
            insertRR(psRR, "stomach_thirstless", "apis", 4, "k");
            insertRR(psRR, "stomach_thirstless", "puls", 4, "k");
            insertRR(psRR, "stomach_thirstless", "gels", 3, "k");
            insertRR(psRR, "stomach_thirstless", "term-a", 2, "hpi");

            insertRubric(psR, "stomach_desire_sweets", "STOMACH", null, "DESIRES - sweets", "STOMACH - DESIRES - sweets", 0, false, null);
            insertRR(psRR, "stomach_desire_sweets", "lyc", 4, "k");
            insertRR(psRR, "stomach_desire_sweets", "arg-n", 4, "k");
            insertRR(psRR, "stomach_desire_sweets", "sulph", 4, "k");
            insertRR(psRR, "stomach_desire_sweets", "chin", 2, "k");

            insertRubric(psR, "abd_liver_enlarged", "ABDOMEN", null, "LIVER - enlarged, tender", "ABDOMEN - LIVER - enlarged, tender", 0, false, null);
            insertRR(psRR, "abd_liver_enlarged", "chel", 4, "k");
            insertRR(psRR, "abd_liver_enlarged", "andro-p", 4, "hpi");
            insertRR(psRR, "abd_liver_enlarged", "lyc", 3, "k");
            insertRR(psRR, "abd_liver_enlarged", "azad-i", 3, "hpi");

            insertRubric(psR, "abd_diabetes_sugar", "ABDOMEN", null, "DIABETES MELLITUS - glycosuria", "ABDOMEN - DIABETES MELLITUS - glycosuria", 0, false, null);
            insertRR(psRR, "abd_diabetes_sugar", "syzyg-j", 4, "hpi");
            insertRR(psRR, "abd_diabetes_sugar", "gymn-s", 4, "hpi");
            insertRR(psRR, "abd_diabetes_sugar", "phos", 3, "k");

            // =========================================================================
            // 4. RESPIRATORY & CHEST CHAPTER
            // =========================================================================
            insertRubric(psR, "resp_asthma_nocturnal", "RESPIRATORY", null, "ASTHMA - night agg.", "RESPIRATORY - ASTHMA - night agg.", 0, false, null);
            insertRR(psRR, "resp_asthma_nocturnal", "ars", 4, "k");
            insertRR(psRR, "resp_asthma_nocturnal", "just-a", 4, "hpi");
            insertRR(psRR, "resp_asthma_nocturnal", "tylop-i", 3, "hpi");
            insertRR(psRR, "resp_asthma_nocturnal", "ip", 3, "k");

            insertRubric(psR, "chest_heart_angina", "CHEST", null, "HEART - angina pectoris", "CHEST - HEART - angina pectoris", 0, false, null);
            insertRR(psRR, "chest_heart_angina", "term-a", 4, "hpi");
            insertRR(psRR, "chest_heart_angina", "cact", 4, "k");
            insertRR(psRR, "chest_heart_angina", "glon", 3, "k");
            insertRR(psRR, "chest_heart_angina", "ars", 3, "k");

            // =========================================================================
            // 5. SKIN CHAPTER
            // =========================================================================
            insertRubric(psR, "skin_eruptions_eczema", "SKIN", null, "ERUPTIONS - eczema", "SKIN - ERUPTIONS - eczema", 0, false, null);
            insertRR(psRR, "skin_eruptions_eczema", "sulph", 4, "k");
            insertRR(psRR, "skin_eruptions_eczema", "graph", 4, "k");
            insertRR(psRR, "skin_eruptions_eczema", "azad-i", 4, "hpi");
            insertRR(psRR, "skin_eruptions_eczema", "hydro-a", 3, "hpi");

            // =========================================================================
            // 6. GENERALITIES CHAPTER
            // =========================================================================
            insertRubric(psR, "gen_cold_agg", "GENERALITIES", null, "COLD - in general - agg.", "GENERALITIES - COLD - in general - agg.", 0, true, "gen_warm_agg");
            insertRR(psRR, "gen_cold_agg", "ars", 4, "k");
            insertRR(psRR, "gen_cold_agg", "sil", 4, "k");
            insertRR(psRR, "gen_cold_agg", "calc", 4, "k");
            insertRR(psRR, "gen_cold_agg", "hepar", 4, "k");
            insertRR(psRR, "gen_cold_agg", "nux-v", 4, "k");
            insertRR(psRR, "gen_cold_agg", "rhus-t", 3, "k");

            insertRubric(psR, "gen_warm_agg", "GENERALITIES", null, "WARMTH - agg.", "GENERALITIES - WARMTH - agg.", 0, true, "gen_cold_agg");
            insertRR(psRR, "gen_warm_agg", "puls", 4, "k");
            insertRR(psRR, "gen_warm_agg", "apis", 4, "k");
            insertRR(psRR, "gen_warm_agg", "iod", 4, "k");
            insertRR(psRR, "gen_warm_agg", "sulph", 4, "k");
            insertRR(psRR, "gen_warm_agg", "nat-m", 3, "k");

            insertRubric(psR, "gen_hypertension", "GENERALITIES", null, "BLOOD PRESSURE - high, hypertension", "GENERALITIES - BLOOD PRESSURE - high, hypertension", 0, false, null);
            insertRR(psRR, "gen_hypertension", "rauw-s", 4, "hpi");
            insertRR(psRR, "gen_hypertension", "term-a", 4, "hpi");
            insertRR(psRR, "gen_hypertension", "glon", 3, "k");
            insertRR(psRR, "gen_hypertension", "bar-c", 3, "k");

            psR.executeBatch();
            psRR.executeBatch();
        }
    }

    private static void insertRubric(PreparedStatement ps, String id, String chapter, String parentId,
                                     String rubricText, String fullPath, int depth, boolean isPolar,
                                     String oppositeId) throws SQLException {
        ps.setString(1, id);
        ps.setString(2, chapter);
        ps.setString(3, parentId);
        ps.setString(4, rubricText);
        ps.setString(5, fullPath);
        ps.setInt(6, depth);
        ps.setBoolean(7, isPolar);
        ps.setString(8, oppositeId);
        ps.addBatch();
    }

    private static void insertRR(PreparedStatement ps, String rubricId, String remedyId, int grade, String citation) throws SQLException {
        ps.setString(1, rubricId);
        ps.setString(2, remedyId);
        ps.setInt(3, grade);
        ps.setString(4, citation);
        ps.addBatch();
    }

    public static void buildFtsIndex(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE VIRTUAL TABLE IF NOT EXISTS rubrics_fts USING fts5(rubric_text, full_path, chapter, content='rubrics', content_rowid='rowid');");
            stmt.execute("INSERT INTO rubrics_fts(rowid, rubric_text, full_path, chapter) SELECT rowid, rubric_text, full_path, chapter FROM rubrics;");
        }
    }

    private static void verifyKnowledgeBase(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM remedies")) {
                if (rs.next()) log.info("Verified remedies count: {}", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM materia_medica")) {
                if (rs.next()) log.info("Verified materia_medica entries: {}", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM rubrics")) {
                if (rs.next()) log.info("Verified rubrics count: {}", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM rubric_remedies")) {
                if (rs.next()) log.info("Verified rubric_remedies citations: {}", rs.getInt(1));
            }
        }
    }
}
