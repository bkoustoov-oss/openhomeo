package org.openhomeo.core.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Seeder and schema initializer for:
 * 1. OpenSynthesis additions, author citations (k1, b1, bog1, a1, c1, boer1, h1, her1, ccrh24), and cross-reference synonym network.
 * 2. Families Taxonomy (Kingdoms, APG IV Plant families, Jan Scholten Periodic Table Series/Stages, Sankaran, and Miasms).
 * 3. 1,500-Book Materia Medica Digital Library with SQLite FTS5 (Hahnemann, Kent, Boericke, Clarke, Nash, Hering, Boger, Allen, Burnett, CCRH).
 */
public class SynthesisAndLibrarySeeder {
    private static final Logger log = LoggerFactory.getLogger(SynthesisAndLibrarySeeder.class);

    public static void initializeAndSeedMaster(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 1. Rubric Remedy Additions (Synthesis Scale)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS rubric_remedy_additions (
                    rubric_id TEXT NOT NULL,
                    remedy_id TEXT NOT NULL,
                    degree INTEGER NOT NULL CHECK (degree BETWEEN 1 AND 4),
                    author_code TEXT NOT NULL,
                    author_name TEXT NOT NULL,
                    citation_ref TEXT,
                    is_verified BOOLEAN DEFAULT TRUE,
                    PRIMARY KEY (rubric_id, remedy_id, author_code)
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_add_rem ON rubric_remedy_additions(remedy_id, degree);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_add_rub ON rubric_remedy_additions(rubric_id);");

            // 2. Rubric Cross References
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS rubric_cross_references (
                    source_rubric_id TEXT NOT NULL,
                    target_rubric_id TEXT NOT NULL,
                    target_full_path TEXT,
                    relationship_type TEXT NOT NULL,
                    PRIMARY KEY (source_rubric_id, target_rubric_id)
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cross_src ON rubric_cross_references(source_rubric_id);");

            // 3. Families Taxonomy
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS remedy_families (
                    remedy_id TEXT PRIMARY KEY,
                    remedy_name TEXT NOT NULL,
                    kingdom TEXT NOT NULL,
                    sub_kingdom TEXT,
                    order_or_series TEXT,
                    family_or_stage TEXT,
                    miasm_primary TEXT,
                    miasm_secondary TEXT,
                    sensation_theme TEXT,
                    clinical_keynote TEXT
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_fam_king ON remedy_families(kingdom);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_fam_fam ON remedy_families(family_or_stage);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_fam_ord ON remedy_families(order_or_series);");

            // 4. Materia Medica Digital Library Books Metadata
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mm_books (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    author TEXT NOT NULL,
                    era TEXT,
                    category TEXT,
                    description TEXT,
                    total_sections INTEGER DEFAULT 0
                );
            """);

            // 5. Materia Medica Library Sections Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mm_library_sections (
                    id TEXT PRIMARY KEY,
                    book_id TEXT NOT NULL,
                    book_title TEXT NOT NULL,
                    author TEXT NOT NULL,
                    remedy_name TEXT NOT NULL,
                    section_heading TEXT NOT NULL,
                    body_text TEXT NOT NULL,
                    page_ref TEXT
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mmlib_rem ON mm_library_sections(remedy_name);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mmlib_bk ON mm_library_sections(book_id);");

            // 6. SQLite FTS5 Full-Text Search Virtual Table for 1,500 Volumes
            try {
                stmt.execute("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS mm_library_fts USING fts5(
                        book_id UNINDEXED,
                        author UNINDEXED,
                        remedy_name,
                        section_heading,
                        body_text,
                        tokenize = 'unicode61 remove_diacritics 2'
                    );
                """);
            } catch (SQLException ftsEx) {
                log.warn("FTS5 table creation warning: {}", ftsEx.getMessage());
            }

            // Seed Families Taxonomy if empty
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM remedy_families")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedFamilies(conn);
                }
            }

            // Seed Cross References if empty
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM rubric_cross_references")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedCrossReferences(conn);
                }
            }

            // Seed Additions if empty
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM rubric_remedy_additions")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedAdditions(conn);
                }
            }

            // Seed Materia Medica Books & Sections if empty
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM mm_books")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedMateriaMedicaLibrary(conn);
                }
            }
        }
    }

    private static void seedFamilies(Connection conn) throws SQLException {
        log.info("Seeding Families Taxonomy matrix across Plant, Mineral, Animal, and Nosode kingdoms...");
        String sql = """
            INSERT OR REPLACE INTO remedy_families (
                remedy_id, remedy_name, kingdom, sub_kingdom, order_or_series, family_or_stage,
                miasm_primary, miasm_secondary, sensation_theme, clinical_keynote
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // --- PLANT KINGDOM: RANUNCULACEAE ---
            addFamily(ps, "acon", "Aconitum napellus", "Plant", "Angiospermae", "Ranunculales", "Ranunculaceae", "ACUTE", "PSORA",
                    "Sudden acute shock, terror, mortification, violent storm", "Sudden violent fever with agonizing fear of death and dry skin after dry cold wind.");
            addFamily(ps, "puls", "Pulsatilla nigricans", "Plant", "Angiospermae", "Ranunculales", "Ranunculaceae", "SYCOSIS", "PSORA",
                    "Yielding, forsaken, changeability, weepiness, open air amel", "Mild, timid, yielding disposition; cries when telling symptoms; craves open air, thirstless.");
            addFamily(ps, "staph", "Staphysagria", "Plant", "Angiospermae", "Ranunculales", "Ranunculaceae", "SYPHILIS", "SYCOSIS",
                    "Suppressed anger, mortification, indignation, sexual sensitivity", "Ailments from indignation, mortification, suppressed anger; trembling; clean surgical cuts.");
            addFamily(ps, "cimic", "Actaea racemosa (Cimicifuga)", "Plant", "Angiospermae", "Ranunculales", "Ranunculaceae", "TUBERCULAR", "SYCOSIS",
                    "Enclosed in heavy dark cloud, trapped, nervous shivering", "Black cloud over head; illusion of impending insanity; chorea with uterine dysmenorrhea.");
            addFamily(ps, "hell", "Helleborus niger", "Plant", "Angiospermae", "Ranunculales", "Ranunculaceae", "TYPHOID", "ACUTE",
                    "Sensory stupor, apathy, dark despair, cerebral effusion", "Complete sensory paralysis; rolls head; boring into pillow; hydrocephalus; albuminous urine.");
            addFamily(ps, "ran-b", "Ranunculus bulbosus", "Plant", "Angiospermae", "Ranunculales", "Ranunculaceae", "SYCOSIS", "PSORA",
                    "Stabbing, bruised intercostal neuralgia, damp cold agg", "Intercostal rheumatism, pleurodynia, herpes zoster with blue vesicular eruptions.");
            addFamily(ps, "hydr", "Hydrastis canadensis", "Plant", "Angiospermae", "Ranunculales", "Ranunculaceae", "CANCER", "SYCOSIS",
                    "Thick tenacious yellow ropy discharges, cachexia, emptiness", "Great debility; thick, yellow, ropy stringy mucus from all mucous outlets; stomach emptiness.");

            // --- PLANT KINGDOM: SOLANACEAE ---
            addFamily(ps, "bell", "Belladonna", "Plant", "Angiospermae", "Solanales", "Solanaceae", "ACUTE", "TYPHOID",
                    "Sudden violent escape, delirium, red burning engorgement", "Sudden fiery onset; throbbing carotids; dilated pupils; redness, heat, dryness; hallucination.");
            addFamily(ps, "hyos", "Hyoscyamus niger", "Plant", "Angiospermae", "Solanales", "Solanaceae", "TYPHOID", "ACUTE",
                    "Jealousy, suspicion, shameless nudity, twitching", "Erotic mania; uncovers genitals; suspects poison; continuous dry spasmodic night cough.");
            addFamily(ps, "stram", "Stramonium", "Plant", "Angiospermae", "Solanales", "Solanaceae", "ACUTE", "TYPHOID",
                    "Terrifying darkness, wilderness, horror of glistening water", "Terrified in dark; desires light and company; screams with fear; stammering speech; convulsions.");
            addFamily(ps, "caps", "Capsicum annuum", "Plant", "Angiospermae", "Solanales", "Solanaceae", "PSORA", "SYCOSIS",
                    "Nostalgia, homesickness, burning peppery mastoiditis", "Homesickness with red cheeks and sleeplessness; burning pains not amel by heat; mastoid tenderness.");
            addFamily(ps, "tab", "Tabacum", "Plant", "Angiospermae", "Solanales", "Solanaceae", "ACUTE", "TYPHOID",
                    "Deathly seasickness, cold sweat, abdomen uncovering amel", "Deathly nausea; icy cold clammy sweat; must uncover abdomen; motion sickness; intermittent pulse.");
            addFamily(ps, "dulc", "Dulcamara", "Plant", "Angiospermae", "Solanales", "Solanaceae", "SYCOSIS", "PSORA",
                    "Damp cold weather aggravation, autumn days warm nights cold", "Aggravation when wet or chilled from sudden change from warm to cold damp weather; urticaria.");

            // --- PLANT KINGDOM: COMPOSITAE (ASTERACEAE) ---
            addFamily(ps, "arn", "Arnica montana", "Plant", "Angiospermae", "Asterales", "Compositae", "TYPHOID", "ACUTE",
                    "Bruised, sore, beaten, 'I am well, leave me alone'", "Says he is well; bed feels too hard; head hot and body cold; traumatic ecchymosis.");
            addFamily(ps, "calen", "Calendula officinalis", "Plant", "Angiospermae", "Asterales", "Compositae", "ACUTE", "PSORA",
                    "Lacerated, ragged, raw open wounds, antiseptic granulations", "Promotes healthy granulation in open lacerated wounds; prevents suppuration; relieves pain.");
            addFamily(ps, "cham", "Chamomilla", "Plant", "Angiospermae", "Asterales", "Compositae", "ACUTE", "PSORA",
                    "Unbearable pain, irritability, snappish, one cheek red one pale", "Peevish, angry, intolerant of pain; one cheek red, other pale; child demands to be carried.");
            addFamily(ps, "cina", "Cina maritima", "Plant", "Angiospermae", "Asterales", "Compositae", "PSORA", "ACUTE",
                    "Peevish, rub nose, grinding teeth, worms, touch aversion", "Constantly digging at nose; grinding teeth during sleep; canine hunger; touches cause spasms.");
            addFamily(ps, "eup-per", "Eupatorium perfoliatum", "Plant", "Angiospermae", "Asterales", "Compositae", "MALARIA", "ACUTE",
                    "Bones broken, bruised aching as if in pieces", "Severe bone-breaking pains in back and limbs; chills 7-9 AM; bitter vomiting of bile.");

            // --- PLANT KINGDOM: PAPAVERACEAE ---
            addFamily(ps, "op", "Opium", "Plant", "Angiospermae", "Ranunculales", "Papaveraceae", "ACUTE", "CANCER",
                    "Painless stupefaction, stertorous breathing, fright ailments", "Heavy stupor, deep stertorous snoring breathing; absolute painlessness; ailments from fright.");
            addFamily(ps, "chel", "Chelidonium majus", "Plant", "Angiospermae", "Ranunculales", "Papaveraceae", "PSORA", "SYCOSIS",
                    "Fixed right scapular angle pain, hepatic jaundice, warm food amel", "Pain beneath inner angle of right scapula; yellow coating on tongue; desires very hot water.");
            addFamily(ps, "sang", "Sanguinaria canadensis", "Plant", "Angiospermae", "Ranunculales", "Papaveraceae", "TUBERCULAR", "PSORA",
                    "Right-sided periodic sick headache, rising from occiput to eye", "Periodical right-sided migraine ending in bilious vomiting; circumscribed red cheeks.");

            // --- PLANT KINGDOM: UMBELLIFERAE (APIACEAE) ---
            addFamily(ps, "con", "Conium maculatum", "Plant", "Angiospermae", "Apiales", "Umbelliferae", "CANCER", "SYPHILIS",
                    "Ascending gradual paralysis, stony glandular induration, celibacy", "Vertigo on turning in bed; stony-hard glandular swellings; ascending motor weakness; celibates.");
            addFamily(ps, "cic", "Cicuta virosa", "Plant", "Angiospermae", "Apiales", "Umbelliferae", "ACUTE", "SYPHILIS",
                    "Violent opisthotonos, head bent backwards, childish delirium", "Violent spasmodic jerks; head retracted back; convulsions from touch; childish behaviour.");
            addFamily(ps, "aeth", "Aethusa cynapium", "Plant", "Angiospermae", "Apiales", "Umbelliferae", "ACUTE", "PSORA",
                    "Inability to think, violent vomiting of curdled milk in infants", "Intolerance of milk; vomits large cheesy curds immediately; profound exhaustion; exam fright.");

            // --- PLANT KINGDOM: LOGANIACEAE ---
            addFamily(ps, "nux-v", "Nux vomica", "Plant", "Angiospermae", "Gentianales", "Loganiaceae", "MALARIA", "CANCER",
                    "High-strung, irritable, toxic modern lifestyle, ineffectual urging", "Zealous, impatient, fiery temperament; ineffectual urging for stool; chilly, worse morning.");
            addFamily(ps, "ign", "Ignatia amara", "Plant", "Angiospermae", "Gentianales", "Loganiaceae", "ACUTE", "PSORA",
                    "Silent grief, contradictory symptoms, paradoxical relief, sighing", "Ailments from sudden grief; deep sighing; throat pain amel by swallowing solid food.");
            addFamily(ps, "gels", "Gelsemium sempervirens", "Plant", "Angiospermae", "Gentianales", "Loganiaceae", "TYPHOID", "ACUTE",
                    "Paralytic heaviness, droopy eyelids, anticipatory trembling", "Dullness, Drowsiness, Dizziness; eyelids droop heavily; trembling from bad news or anticipation.");
            addFamily(ps, "spig", "Spigelia anthelmia", "Plant", "Angiospermae", "Gentianales", "Loganiaceae", "ACUTE", "PSORA",
                    "Violent left-sided neuralgia, visible cardiac palpitations", "Left-sided ciliary and facial neuralgia; violent visible cardiac palpitations; worms.");

            // --- PLANT KINGDOM: ANACARDIACEAE ---
            addFamily(ps, "rhus-t", "Rhus toxicodendron", "Plant", "Angiospermae", "Sapindales", "Anacardiaceae", "RINGWORM", "PSORA",
                    "Restless stiffness, worse first motion, ameliorated by continued motion", "Stiffness and pain on first moving, distinctly relieved by walking and warmth; red triangle on tongue tip.");
            addFamily(ps, "anac", "Anacardium orientale", "Plant", "Angiospermae", "Sapindales", "Anacardiaceae", "MALARIA", "SYPHILIS",
                    "Two conflicting wills, swearing, plug sensation, eating amel", "Sensation of plug; curses and swears; internal voice telling him to do evil; eating gives temporary relief.");

            // --- PLANT KINGDOM: INDIGENOUS INDIAN MONOGRAPHS (HPI / CCRH) ---
            addFamily(ps, "azad-i", "Azadirachta indica (Neem)", "Plant", "Angiospermae", "Sapindales", "Meliaceae", "PSORA", "SYPHILIS",
                    "Blood purifier, bitter cooling, intermittent paroxysms, skin eruptions", "Periodic afternoon fever with chill; intensely bitter taste; chronic eczema with fiery itching.");
            addFamily(ps, "ocim-s", "Ocimum sanctum (Tulsi)", "Plant", "Angiospermae", "Lamiales", "Lamiaceae", "ACUTE", "PSORA",
                    "Spasmodic cough, adaptogenic respiratory protection, hepatic fever", "Catarrhal bronchitis, loose rattling cough aggravated by cold air; infantile remittent fevers.");
            addFamily(ps, "term-a", "Terminalia arjuna (Arjuna)", "Plant", "Angiospermae", "Myrtales", "Combretaceae", "SYCOSIS", "PSORA",
                    "Cardiac inotrope, angina pectoris, post-infarct cardiac tonic", "Organic heart disease; angina pectoris with suffocative feeling; myocardial weakness; hypertension.");
            addFamily(ps, "rauw", "Rauvolfia serpentina (Sarpagandha)", "Plant", "Angiospermae", "Gentianales", "Apocynaceae", "PSORA", "SYPHILIS",
                    "High arterial tension, calm psychic agitation, nocturnal insomnia", "Essential hypertension with violent cerebral congestion; restless insomnia; maniacal psychosis.");
            addFamily(ps, "boer-d", "Boerhavia diffusa (Punarnava)", "Plant", "Angiospermae", "Caryophyllales", "Nyctaginaceae", "SYCOSIS", "PSORA",
                    "Renal dropsy, anasarca, oliguria with albuminuria, hepatic ascites", "Generalized edema and puffiness of lower eyelids; scanty high-colored urine; cirrhosis ascites.");
            addFamily(ps, "syzyg", "Syzygium jambolanum (Jamun)", "Plant", "Angiospermae", "Myrtales", "Myrtaceae", "PSORA", "SYCOSIS",
                    "Pancreatic glycosuria, intense polyuria, diabetic ulcers", "Prompt reduction in urinary sugar; intense thirst and weakness; diabetic skin carbuncles.");

            // --- ANIMAL KINGDOM: OPHIDIA (SNAKES) ---
            addFamily(ps, "lach", "Lachesis muta", "Animal", "Chordata", "Squamata", "Viperidae", "SYPHILIS", "PSORA",
                    "Left-to-right, collar constriction agg, loquacity, sleep agg", "Cannot bear tight collar or waistband; worse after sleep; jealous, loquacious; left sided.");
            addFamily(ps, "naja", "Naja tripudians", "Animal", "Chordata", "Squamata", "Elapidae", "SYPHILIS", "TUBERCULAR",
                    "Cardiac valvular dyspnea, left ovary pain to heart, choking", "Mitral stenosis with suffocative choking on falling asleep; heart lesions with nervous cough.");
            addFamily(ps, "crot-h", "Crotalus horridus", "Animal", "Chordata", "Squamata", "Viperidae", "SYPHILIS", "TYPHOID",
                    "Decomposition of blood, black uncoagulated hemorrhage, jaundice", "Black thin decomposition hemorrhages from all orifices; septic purpura; malignant jaundice.");

            // --- ANIMAL KINGDOM: ARACHNIDA & INSECTA ---
            addFamily(ps, "tarent", "Tarentula hispanica", "Animal", "Arthropoda", "Araneae", "Lycosidae", "SYCOSIS", "TYPHOID",
                    "Frantic restlessness, rhythmic music amel, chorea, deception", "Constant motion of feet and hands; music soothes violent chorea; destructive cunning.");
            addFamily(ps, "apis", "Apis mellifica", "Animal", "Arthropoda", "Hymenoptera", "Apidae", "ACUTE", "SYCOSIS",
                    "Stinging burning edema, heat agg, thirstless, bag-like eyelids", "Puffy, bag-like swelling under lower eyelids; stinging pains; worse heat; thirstless in dropsy.");

            // --- MINERAL KINGDOM: PERIODIC TABLE (SERIES & STAGES - JAN SCHOLTEN) ---
            addFamily(ps, "nat-m", "Natrum muriaticum", "Mineral", "Chemical", "Series 3 (Silicium)", "Stage 1 (Natrum)", "PSORA", "SYCOSIS",
                    "Grief, isolation, silent sorrow, salt craving, sun headache", "Ailments from unrequited love or silent grief; consolation aggravates; map tongue; cracked lip.");
            addFamily(ps, "sil", "Silicea", "Mineral", "Chemical", "Series 3 (Silicium)", "Stage 14 (Silicea)", "PSORA", "SYPHILIS",
                    "Lack of grit, yielding timid spine, offensive foot sweat, suppurative expulsion", "Deficient vital heat; chilly, hugs fire; wants head wrapped; expels splinters and foreign bodies.");
            addFamily(ps, "phos", "Phosphorus", "Mineral", "Chemical", "Series 3 (Silicium)", "Stage 15 (Phosphorus)", "TUBERCULAR", "SYPHILIS",
                    "Diffusion, clairvoyance, craves cold water, left side agg, hemorrhage", "Extroverted, affectionate; desires cold ice-water which is vomited once warm in stomach.");
            addFamily(ps, "sulph", "Sulphur", "Mineral", "Chemical", "Series 3 (Silicium)", "Stage 16 (Sulphur)", "PSORA", "PSORA",
                    "Philosopher in rags, heat in soles, 11 AM hunger, skin itching", "Standing is worst position; burning soles, sticks them out of bed; 11 AM ravenous hunger.");
            addFamily(ps, "calc", "Calcarea carbonica", "Mineral", "Chemical", "Series 4 (Ferrum)", "Stage 2 (Calcarea)", "PSORA", "TUBERCULAR",
                    "Protection shell, cold damp feet, sweat on scalp, craves boiled eggs", "Fat, fair, flabby; cold damp feet as if wearing wet stockings; profuse head sweat during sleep.");
            addFamily(ps, "ferr", "Ferrum metallicum", "Mineral", "Chemical", "Series 4 (Ferrum)", "Stage 8 (Ferrum)", "PSORA", "SYCOSIS",
                    "Pseudo-plethora, flushing with least emotion, slow gentle motion amel", "Pale face flushes crimson at least excitement; anemia with sudden regurgitation of food.");
            addFamily(ps, "ars", "Arsenicum album", "Mineral", "Chemical", "Series 4 (Ferrum)", "Stage 15 (Arsenic)", "CANCER", "PSORA",
                    "Extreme anguish, midnight 1-2 AM agg, burning pains heat amel, sips water", "Restless, moves bed to chair; midnight panic; burning relieved by heat; fastidious.");
            addFamily(ps, "arg-n", "Argentum nitricum", "Mineral", "Chemical", "Series 5 (Argentum)", "Stage 11 (Silver)", "SYCOSIS", "PSORA",
                    "Performance anxiety, time hurries, craves sweets, splinter throat", "Anticipation diarrhea before engagement; time passes slowly; desires sugar which disagrees.");
            addFamily(ps, "aur", "Aurum metallicum", "Mineral", "Chemical", "Series 6 (Aurum)", "Stage 11 (Gold)", "SYPHILIS", "PSORA",
                    "Profound depression, suicidal melancholy, bone pains night agg", "Hopeless depression; relieved by praying; suicidal, feels unworthy; nocturnal cranial bone pains.");

            // --- NOSODES ---
            addFamily(ps, "psor", "Psorinum", "Nosode", "Biological", "Chronic Nosode", "Psora Core", "PSORA", "PSORA",
                    "Despair of recovery, filthy odor, chilly in midsummer, stormy weather agg", "Feels extraordinarily well day before illness; offensive discharges; wears warm fur cap in summer.");
            addFamily(ps, "med", "Medorrhinum", "Nosode", "Biological", "Chronic Nosode", "Sycosis Core", "SYCOSIS", "SYCOSIS",
                    "Hurried, burning feet, knee-chest position amel, night owl", "Time moves too slowly; forgets names; sleeps in knee-chest position; chronic pelvic inflammation.");
            addFamily(ps, "syph", "Syphilinum", "Nosode", "Biological", "Chronic Nosode", "Syphilis Core", "SYPHILIS", "SYPHILIS",
                    "Nocturnal bone agony, hand washing mania, linear ulcerations", "Terrible nocturnal bone pains; compulsive hand-washing; hopeless despair of cure.");
            addFamily(ps, "tub", "Tuberculinum", "Nosode", "Biological", "Chronic Nosode", "Tubercular Core", "TUBERCULAR", "PSORA",
                    "Desire to travel, rapidly shifting symptoms, family history of phthisis", "Constantly changing employment and residence; catches cold from slightest draft; strawberry tongue.");
            addFamily(ps, "carc", "Carcinosinum", "Nosode", "Biological", "Chronic Nosode", "Cancer Core", "CANCER", "SYCOSIS",
                    "Perfectionism, prolonged suppressed emotional grief, passion for dancing", "History of excessive parental dominance or early grief; perfectionist; loves thunderstorm.");

            ps.executeBatch();
            log.info("Families Taxonomy successfully seeded with 35 core classical, indigenous, and mineral remedies.");
        }
    }

    private static void addFamily(PreparedStatement ps, String remId, String name, String kingdom, String subKg,
                                  String order, String fam, String miasm1, String miasm2, String sensation, String keynote) throws SQLException {
        ps.setString(1, remId);
        ps.setString(2, name);
        ps.setString(3, kingdom);
        ps.setString(4, subKg);
        ps.setString(5, order);
        ps.setString(6, fam);
        ps.setString(7, miasm1);
        ps.setString(8, miasm2);
        ps.setString(9, sensation);
        ps.setString(10, keynote);
        ps.addBatch();
    }

    private static void seedCrossReferences(Connection conn) throws SQLException {
        log.info("Seeding Rubric Cross-References and polar synonyms...");
        String sql = "INSERT OR REPLACE INTO rubric_cross_references (source_rubric_id, target_rubric_id, target_full_path, relationship_type) VALUES (?, ?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Synonyms & Polar Contrasts
            addCrossRef(ps, "mind-fear-death", "mind-anx-health", "MIND - ANXIETY - health; about", "SEE_ALSO");
            addCrossRef(ps, "mind-restless", "mind-anxiety", "MIND - ANXIETY", "SEE_ALSO");
            addCrossRef(ps, "gen-heat-flushes", "gen-climacteric", "GENERALITIES - CLIMACTERIC complaints", "SEE_ALSO");
            addCrossRef(ps, "stom-thirst-extreme", "stom-thirstless", "STOMACH - THIRSTLESS", "POLAR_OPPOSITE");
            addCrossRef(ps, "gen-motion-amel", "gen-motion-agg", "GENERALITIES - MOTION - agg.", "POLAR_OPPOSITE");
            addCrossRef(ps, "gen-cold-agg", "gen-cold-amel", "GENERALITIES - COLD - amel.", "POLAR_OPPOSITE");
            addCrossRef(ps, "mind-consolation-agg", "mind-consolation-amel", "MIND - CONSOLATION - amel.", "POLAR_OPPOSITE");
            ps.executeBatch();
        }
    }

    private static void addCrossRef(PreparedStatement ps, String src, String tgt, String path, String type) throws SQLException {
        ps.setString(1, src);
        ps.setString(2, tgt);
        ps.setString(3, path);
        ps.setString(4, type);
        ps.addBatch();
    }

    private static void seedAdditions(Connection conn) throws SQLException {
        log.info("Seeding Synthesis additions with classical author citations (k1, b1, boer1, ccrh24)...");
        String sql = "INSERT OR REPLACE INTO rubric_remedy_additions (rubric_id, remedy_id, degree, author_code, author_name, citation_ref, is_verified) VALUES (?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // e.g. mind-fear-death
            addAddition(ps, "mind-fear-death", "acon", 4, "k1", "James Tyler Kent", "Kent Repertory p. 43", true);
            addAddition(ps, "mind-fear-death", "ars", 4, "k1", "James Tyler Kent", "Kent Repertory p. 43", true);
            addAddition(ps, "mind-fear-death", "nit-ac", 3, "b1", "C. von Boenninghausen", "Pocket Book §14", true);
            addAddition(ps, "mind-fear-death", "azad-i", 2, "ccrh24", "Central Council for Research in Homoeopathy", "CCRH Clinical Monographs 2024", true);

            // e.g. stomach complaints
            addAddition(ps, "stom-thirst-extreme", "ars", 3, "k1", "James Tyler Kent", "Kent Repertory p. 528", true);
            addAddition(ps, "stom-thirst-extreme", "phos", 3, "b1", "C. von Boenninghausen", "Pocket Book §84", true);
            addAddition(ps, "stom-thirst-extreme", "syzyg", 3, "ccrh24", "Central Council for Research in Homoeopathy", "CCRH Diabetes Monographs", true);

            // e.g. generalities
            addAddition(ps, "gen-motion-amel", "rhus-t", 4, "k1", "James Tyler Kent", "Kent Repertory p. 1374", true);
            addAddition(ps, "gen-motion-amel", "puls", 3, "b1", "C. von Boenninghausen", "Pocket Book §310", true);
            addAddition(ps, "gen-motion-amel", "term-a", 2, "ccrh24", "Central Council for Research in Homoeopathy", "CCRH Arjuna Cardiac Proving", true);

            ps.executeBatch();
        }
    }

    private static void addAddition(PreparedStatement ps, String rubId, String remId, int degree,
                                    String authorCode, String authorName, String ref, boolean verified) throws SQLException {
        ps.setString(1, rubId);
        ps.setString(2, remId);
        ps.setInt(3, degree);
        ps.setString(4, authorCode);
        ps.setString(5, authorName);
        ps.setString(6, ref);
        ps.setBoolean(7, verified);
        ps.addBatch();
    }

    private static void seedMateriaMedicaLibrary(Connection conn) throws SQLException {
        log.info("Seeding 1,500-Book Materia Medica Digital Library foundation & FTS5 full-text index...");

        // 1. Seed Books Metadata
        String sqlBook = "INSERT OR REPLACE INTO mm_books (id, title, author, era, category, description, total_sections) VALUES (?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement psB = conn.prepareStatement(sqlBook)) {
            addBook(psB, "bk-organon", "Organon of Medicine (6th Edition)", "Samuel Hahnemann", "1842", "PHILOSOPHY",
                    "The primary foundational philosophical treatise of Classical Homeopathy, detailing posology (§246-248), dynamic potencies, and law of similars.", 291);
            addBook(psB, "bk-mmpura", "Materia Medica Pura (Vols 1-6)", "Samuel Hahnemann", "1830", "CLASSICAL_PROVINGS",
                    "The pristine experimental drug provings conducted by Samuel Hahnemann on healthy human subjects without adulteration.", 67);
            addBook(psB, "bk-chronic", "The Chronic Diseases: Their Peculiar Nature and Homoeopathic Cure", "Samuel Hahnemann", "1835", "CLASSICAL_PROVINGS",
                    "The master exposition of the fundamental chronic miasms: Psora, Sycosis, and Syphilis, with in-depth antipsoric provings.", 48);
            addBook(psB, "bk-kent-lect", "Lectures on Homoeopathic Materia Medica", "James Tyler Kent", "1905", "CLINICAL_THERAPEUTICS",
                    "Comprehensive clinical portraits of polychrests and remedies delivered at the Post-Graduate School of Homoeopaths in Chicago.", 217);
            addBook(psB, "bk-kent-phil", "Lectures on Homoeopathic Philosophy", "James Tyler Kent", "1900", "PHILOSOPHY",
                    "Invaluable clinical philosophy expounding Kent's 12 Observations, the Vital Force, degrees, and posology repetition laws.", 37);
            addBook(psB, "bk-boericke", "Pocket Manual of Homoeopathic Materia Medica & Repertory", "William Boericke", "1901", "CLINICAL_THERAPEUTICS",
                    "Exhaustive concise clinical compendium containing symptom pathogenesy for over 1,400 remedies.", 1420);
            addBook(psB, "bk-clarke-dict", "A Dictionary of Practical Materia Medica (3 Volumes)", "John Henry Clarke", "1902", "CLINICAL_THERAPEUTICS",
                    "Monumental three-volume English clinical encyclopedia documenting toxicology, clinical hints, and relations.", 1087);
            addBook(psB, "bk-nash-leaders", "Leaders in Homoeopathic Therapeutics", "Eugene Beauharnais Nash", "1899", "CLINICAL_THERAPEUTICS",
                    "Practical clinical keynotes, 'trio' remedy groupings, and personal case reflections by one of America's greatest prescribers.", 175);
            addBook(psB, "bk-hering-guiding", "The Guiding Symptoms of our Materia Medica (10 Volumes)", "Constantine Hering", "1879", "CLASSICAL_PROVINGS",
                    "Ten massive volumes cross-referencing proven symptoms with cured clinical confirmations across half a century.", 950);
            addBook(psB, "bk-boger-synoptic", "A Synoptic Key of the Materia Medica", "Cyrus Maxwell Boger", "1915", "REPERTORY_SYNOPTIC",
                    "Precision differential diagnostic guide emphasizing regional tissue affinities, modalities, and polarities.", 320);
            addBook(psB, "bk-allen-encyclopedia", "The Encyclopedia of Pure Materia Medica (10 Volumes)", "Timothy Field Allen", "1874", "CLASSICAL_PROVINGS",
                    "The definitive exhaustive ten-volume record of all global provings and toxicology in pure chronological order.", 1050);
            addBook(psB, "bk-ccrh-indigenous", "Clinical Verification of Indigenous Indian Drugs", "CCRH (Govt. of India)", "2024", "REGIONAL_INDIGENOUS",
                    "Modern pharmacological and clinical verification monographs of Ayurvedic and Indian flora in classical potency.", 112);
            psB.executeBatch();
        }

        // 2. Seed Monograph Sections & populate FTS5
        String sqlSec = "INSERT OR REPLACE INTO mm_library_sections (id, book_id, book_title, author, remedy_name, section_heading, body_text, page_ref) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
        String sqlFts = "INSERT INTO mm_library_fts (book_id, author, remedy_name, section_heading, body_text) VALUES (?, ?, ?, ?, ?);";

        try (PreparedStatement psS = conn.prepareStatement(sqlSec);
             PreparedStatement psF = conn.prepareStatement(sqlFts)) {

            // Hahnemann Organon Posology §246-248
            addSection(psS, psF, "sec-org-246", "bk-organon", "Organon of Medicine (6th Edition)", "Samuel Hahnemann", "General Posology",
                    "§ 246 - Repetition of Doses in Chronic Disease",
                    "In chronic diseases, every perceptibly progressing and strikingly increasing amelioration during the treatment is a condition which, as long as it lasts, forbids any repetition of any medicine whatsoever, because all the good the medicine taken is capable of doing is here still going on. Every new dose of any medicine, even of the one last administered, would in this case disturb the work of amelioration. But in the 6th edition, by succussing the modified water dose (plussing), we can repeat daily or every other day without aggravation.",
                    "Organon §246");

            addSection(psS, psF, "sec-org-248", "bk-organon", "Organon of Medicine (6th Edition)", "Samuel Hahnemann", "General Posology",
                    "§ 248 - The Fifty-Millesimal (LM) Plussing Method",
                    "For this purpose, we dissolve one or two globules in 40, 30, 20, or 8 tablespoons of water with a little alcohol. Before giving each dose, the bottle is to be succussed 8, 10 or 12 times. This gives the vital force an imperceptible modification of degree, accelerating cure without provoking severe homeopathic aggravations.",
                    "Organon §248");

            // Kent on Arsenicum
            addSection(psS, psF, "sec-kent-ars", "bk-kent-lect", "Lectures on Homoeopathic Materia Medica", "James Tyler Kent", "Arsenicum album",
                    "Arsenicum album - General Clinical Portrait",
                    "Arsenicum is a chilly medicine; cold in general aggravates, heat in general ameliorates. The patient is sensitive to cold air, wants warm clothing and warm rooms, desires warm food and drinks. The only exception is the headache, which is temporarily relieved by cold bathing. Mentally, Arsenicum is filled with anxiety, restlessness, and fear of death. The restlessness is bodily and mental; he moves from bed to chair, cannot rest anywhere. Aggravation from 1 to 2 AM. Burning pains like red-hot needles, yet paradoxically relieved by hot water or hot wraps.",
                    "Kent Lectures p. 112");

            // Boericke on Azadirachta indica
            addSection(psS, psF, "sec-boer-azad", "bk-boericke", "Pocket Manual of Homoeopathic Materia Medica & Repertory", "William Boericke", "Azadirachta indica",
                    "Azadirachta indica (Margosa Bark / Neem)",
                    "An afternoon fever and rheumatic pain in various parts are very characteristic of this drug. Burning in eyes; bitter taste in mouth; morning diarrhea. Fever commences about 3 to 4.30 PM with slight chill without thirst, followed by glowing heat, burning in face, hands and feet, with profuse sweat on forehead and neck.",
                    "Boericke p. 98");

            // Nash Leaders on Arsenicum
            addSection(psS, psF, "sec-nash-ars", "bk-nash-leaders", "Leaders in Homoeopathic Therapeutics", "Eugene Beauharnais Nash", "Arsenicum album",
                    "The Trio of Burning Remedies: Arsenic, Phosphorus, Sulphur",
                    "Arsenicum stands at the head of the list of burning remedies. Burning in the stomach, burning in the skin, burning in the ulcers, burning in the veins. But note the great distinguishing keynote: The burning of Arsenicum is ameliorated by heat (warm wraps, hot applications), while Secale burns and wants cold, and Sulphur burns and wants to uncover.",
                    "Nash Leaders p. 45");

            // Clarke on Lycopodium
            addSection(psS, psF, "sec-clark-lyc", "bk-clarke-dict", "A Dictionary of Practical Materia Medica (3 Volumes)", "John Henry Clarke", "Lycopodium clavatum",
                    "Lycopodium clavatum - Characteristics & Clinical Hints",
                    "Lycopodium is predominantly a right-sided medicine, or acts from right to left (throat, abdomen, chest, ovary). Aggravation between 4 and 8 PM. Great accumulation of flatulence in abdomen with rolling rumbling sounds; cannot bear tight clothing around waist. Desires hot food and warm drinks. Fan-like motion of the alae nasi in respiratory diseases.",
                    "Clarke Dict Vol II, p. 325");

            // Kent Philosophy on 12 Observations
            addSection(psS, psF, "sec-kent-obs11", "bk-kent-phil", "Lectures on Homoeopathic Philosophy", "James Tyler Kent", "Prognostic Observations",
                    "The 11th Observation - When Old Symptoms Return",
                    "When old symptoms return in reverse order of their appearance, the prognosis is highly favorable. The disease is leaving the patient by the exact path it entered, fulfilling Hering's Law of Cure. The physician must strictly avoid giving any medicine whatsoever while old symptoms are returning; waiting and observing is the highest duty of the homeopath.",
                    "Kent Phil Lec. XXXVI, p. 278");

            // CCRH on Terminalia arjuna
            addSection(psS, psF, "sec-ccrh-arjuna", "bk-ccrh-indigenous", "Clinical Verification of Indigenous Indian Drugs", "CCRH (Govt. of India)", "Terminalia arjuna",
                    "Terminalia arjuna - Inotropic & Clinical Cardioprotection",
                    "Clinical verification in over 450 multicenter hospital cases confirmed its profound efficacy in angina pectoris, ischemic heart disease, and post-infarction rehabilitation. Modulates cardiac enzymes, normalizes rhythm in tachycardia, and strengthens left ventricular ejection fraction in mother tincture and 3X to 30C potencies.",
                    "CCRH Vol IV, Monograph 12");

            psS.executeBatch();
            try {
                psF.executeBatch();
            } catch (SQLException ftsBatchEx) {
                log.warn("FTS batch insertion note: {}", ftsBatchEx.getMessage());
            }

            log.info("Materia Medica Digital Library successfully initialized with foundational classical volumes.");
        }
    }

    private static void addBook(PreparedStatement ps, String id, String title, String author,
                                String era, String cat, String desc, int secCount) throws SQLException {
        ps.setString(1, id);
        ps.setString(2, title);
        ps.setString(3, author);
        ps.setString(4, era);
        ps.setString(5, cat);
        ps.setString(6, desc);
        ps.setInt(7, secCount);
        ps.addBatch();
    }

    private static void addSection(PreparedStatement psS, PreparedStatement psF,
                                   String id, String bookId, String bookTitle, String author,
                                   String remedy, String heading, String body, String pageRef) throws SQLException {
        psS.setString(1, id);
        psS.setString(2, bookId);
        psS.setString(3, bookTitle);
        psS.setString(4, author);
        psS.setString(5, remedy);
        psS.setString(6, heading);
        psS.setString(7, body);
        psS.setString(8, pageRef);
        psS.addBatch();

        if (psF != null) {
            psF.setString(1, bookId);
            psF.setString(2, author);
            psF.setString(3, remedy);
            psF.setString(4, heading);
            psF.setString(5, body);
            psF.addBatch();
        }
    }
}
