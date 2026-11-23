package org.openhomeo.core.db;

import org.openhomeo.core.model.MedicineConstituent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
public class MedicineConstituentSeeder {
    private static final Logger log = LoggerFactory.getLogger(MedicineConstituentSeeder.class);

    public static void seedConstituentsIfEmpty(Connection conn) {
        try {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM medicine_constituents")) {
                if (rs.next() && rs.getInt(1) > 30) {
                    return; // Already populated
                }
            }
        } catch (SQLException e) {
            log.warn("Could not check medicine_constituents count: {}", e.getMessage());
        }

        log.info("Seeding comprehensive master medicine constituent database with Indian and International remedies...");
        List<MedicineConstituent> list = buildMasterConstituents();

        String sql = """
            INSERT OR REPLACE INTO medicine_constituents (
                remedy_id, remedy_name, common_and_indian_name, active_constituents,
                pharmacological_action, organ_affinity, toxicology_and_antidotes, annexed_remarks
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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
                ps.setString(8, mc.annexedRemarks() != null ? mc.annexedRemarks() : "");
                ps.addBatch();
            }
            ps.executeBatch();
            log.info("Successfully seeded {} medicine constituent scientific records.", list.size());
        } catch (SQLException e) {
            log.error("Failed to seed medicine constituents", e);
        }
    }

    public static List<MedicineConstituent> buildMasterConstituents() {
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
                "rauw", "Rauwolfia serpentina", "Sarpagandha / Indian Snakeroot / Chandrabhaga",
                "Reserpine, ajmaline, rescinnamine, deserpidine, ajmalicine, serpentine",
                "Irreversibly inhibits vesicular monoamine transporter 2 (VMAT-2), depleting presynaptic stores of norepinephrine, dopamine, and serotonin in central and peripheral sympathetic terminals. Induces generalized arteriolar vasodilation, reduces vascular resistance, and exerts central sedative antihypertensive action.",
                "Sympathetic vasomotor centers, peripheral arterioles, central limbic system, myocardium",
                "Antidotes: Camphora, Nux vomica; physiological antagonists: Ephedrine"
        ));

        list.add(MedicineConstituent.of(
                "arjun", "Terminalia arjuna", "Arjuna / Indian White Murdah / Kahu",
                "Arjunic acid, arjunolic acid, coenzyme Q10, terminic acid, arjunglucoside I & II, bioflavonoids",
                "Positive inotropic action, endothelial cytoprotection, and myocardial antioxidant preservation. Enhances left ventricular ejection fraction, protects against ischemia-reperfusion myocardial injury, and stabilizes coronary endothelial nitric oxide release.",
                "Myocardium, coronary arteries, cardiac conduction tissue, vascular endothelium",
                "Non-toxic botanical; antidoted by Crataegus, Digitalis in overdosage"
        ));

        list.add(MedicineConstituent.of(
                "azad", "Azadirachta indica", "Neem / Margosa Tree / Nimba",
                "Azadirachtin, nimbin, nimbidin, salannin, gedunin, meliacin, quercetin",
                "Broad-spectrum antimicrobial, antiparasitic, and immunomodulatory activity. Suppresses pro-inflammatory cytokines (IL-1, TNF-α) and inhibits bacterial cell wall synthesis. Powerful antipyretic in intermittent malarial fevers and regenerative in chronic eczematous, leprous, and suppurative dermatoses.",
                "Reticuloendothelial system, skin, sebaceous glands, liver parenchyma, mucous membranes",
                "Antidoted by Coffea, Carbo vegetabilis"
        ));

        list.add(MedicineConstituent.of(
                "syzyg", "Syzygium jambolanum", "Jamun / Black Plum / Jambul",
                "Jamboline, ellagic acid, gallic acid, myricetin, anthocyanins",
                "Blocks conversion of amylaceous substances into glucose by inhibiting intestinal alpha-amylase and alpha-glucosidase. Activates glycolytic enzymes and stimulates pancreatic beta-cell functional reserves, rapidly resolving excessive glycosuria, polydipsia, and diabetic pruritus.",
                "Pancreatic beta-islet cells, renal tubules, hepatic glycogen synthesis pathways",
                "Antidoted by Nux vomica"
        ));

        list.add(MedicineConstituent.of(
                "acon", "Aconitum napellus", "Monk's Hood / Wolfsbane / Mitha Zahar",
                "Aconitine, mesaconitine, hypaconitine, napelline, aconitic acid",
                "High affinity binding to neurotoxin site 2 of voltage-gated sodium channels, preventing their inactivation and causing sustained cellular depolarization. Produces intense arterial tension, acute vascular engorgement, cardiac tachyarrhythmia, and overwhelming fear of death with restive panic.",
                "Cerebrovascular circulation, cardiac conduction system, sensory nerve terminals, serous membranes",
                "Antidotes: Coffea, Belladonna, Camphora; physiological antidote: Atropine"
        ));

        list.add(MedicineConstituent.of(
                "bell", "Atropa belladonna", "Deadly Nightshade / Sag-Angur",
                "Atropine, l-hyoscyamine, scopolamine (hyoscine), belladonnine",
                "Competitive antagonist of postganglionic muscarinic acetylcholine receptors. Blocks parasympathetic pupillary, glandular, and smooth muscle tone, causing active cerebral hyperemia, dilated pupils, throbbing carotids, intense febrile heat, and violent, furious delirium.",
                "Cerebral cortex, meninges, vascular autonomic ganglia, eye (ciliary muscle), skin capillaries",
                "Antidotes: Coffea cruda, Hepar sulph, Opium, Physostigmine"
        ));

        list.add(MedicineConstituent.of(
                "nux-v", "Strychnos nux-vomica", "Poison Nut / Kuchla / Visamusti",
                "Strychnine, brucine, vomicine, colubrine, loganin",
                "Competitive antagonist at strychnine-sensitive glycine receptors in motor neurons of spinal cord and brainstem. Removes postsynaptic inhibitory modulation, producing widespread muscular hypertonia, hypersensitivity to sensory stimuli, portal congestion, and toxic irritability.",
                "Spinal cord anterior horn cells, portal venous circulation, gastrointestinal tract, autonomic plexus",
                "Antidotes: Coffea, Chamomilla, Cocculus; physiological: Barbiturates, Diazepam"
        ));

        list.add(MedicineConstituent.of(
                "bry", "Bryonia alba", "White Bryony / Wild Hop",
                "Bryonin, bryonidin, cucurbitacins B, D, E, I, triterpenoid glycosides",
                "Selective inflammatory irritation of serous and synovial membranes, suppressing natural lubrication and producing extreme dryness. Characteristic excruciating stitching pains exacerbated by slightest physical movement and relieved by firm counter-pressure and complete immobility.",
                "Pleura, peritoneum, pericardium, synovial joints, meninges, respiratory mucous membranes",
                "Antidotes: Aconitum, Chamomilla, Nux vomica, Rhus tox"
        ));

        list.add(MedicineConstituent.of(
                "rhus-t", "Toxicodendron pubescens", "Poison Ivy / Poison Oak",
                "Toxicodendric acid, urushiol, fisetin, gallic acid, rhamnose",
                "Selective affinity for fibrous connective tissue, ligaments, aponeuroses, and tendons. Induces serofibrinous infiltration causing initial stiffness and tearing pain on beginning movement, which is ameliorated by continued motion, warm applications, and dry sunny weather.",
                "Fibrous tissue, tendons, periosteum, skin (vesicular eruptions), peripheral motor nerves",
                "Antidotes: Anacardium, Bryonia, Croton tiglium, Sulphur, Camphora; incompatible: Apis mellifica"
        ));

        list.add(MedicineConstituent.of(
                "ars", "Arsenicum album", "White Arsenic / Sankhiya / Hartal",
                "Arsenic trioxide (As2O3)",
                "Uncouples mitochondrial oxidative phosphorylation by substituting for inorganic phosphate, and binds sulfhydryl groups in pyruvate dehydrogenase. Results in cellular ATP depletion, intense tissue necrosis, burning pains paradoxically relieved by radiant heat, extreme restlessness, and midnight anxiety.",
                "Gastrointestinal mucosa, vascular endothelium, myocardium, kidneys, skin, central nervous system",
                "Antidotes: Hydrated ferric oxide, Dimercaprol (BAL), Camphora, China, Nux vomica, Carbo veg"
        ));

        list.add(MedicineConstituent.of(
                "phos", "Phosphorus", "Elemental Yellow/White Phosphorus",
                "Tetratomic elemental phosphorus (P4)",
                "Disrupts fatty acid beta-oxidation and microvascular wall integrity. Causes widespread vascular fragility, profuse painless hemorrhages, severe fatty degeneration of liver, myocardium, and kidneys, along with nerve tissue hyperesthesia and longing for cold drinks.",
                "Capillary blood vessels, liver parenchyma, lungs (lower lobes), kidneys, nervous system, bones",
                "Antidotes: Nux vomica, Coffea, Terebinthina, Camphora; incompatible: Causticum"
        ));

        list.add(MedicineConstituent.of(
                "lyc", "Lycopodium clavatum", "Club Moss / Wolf's Claw",
                "Lycopodine, annotine, lycoclavine, polyphenols, sporopollenin",
                "Acts predominantly on the portal venous system, liver transaminases, and urinary uric acid metabolism. Corrects functional hepatic insufficiency, biliary dyskinesia, 4-8 PM aggravation, flatulent lower abdominal distension, and right-to-left symptom progression.",
                "Liver, gallbladder, urinary tract, right kidney, gastrointestinal tract, lungs",
                "Antidotes: Camphora, Pulsatilla, Causticum; complementary: Chelidonium, Lachesis"
        ));

        list.add(MedicineConstituent.of(
                "thuj", "Thuja occidentalis", "Arbor Vitae / Tree of Life / Morpankhi",
                "Thujone (alpha & beta), bornyl acetate, pinene, polysaccharides, flavonoid glycosides",
                "Potent epithelial modulating and antiproliferative action on human papillomavirus (HPV) induced mucocutaneous proliferations. Resolves fig-warts, condylomata, sycotic dyscrasias, and post-vaccination chronic states with damp foul sweat and brittle nails.",
                "Epithelial tissues, genitourinary mucous membranes, skin appendages, lymphatic glands",
                "Antidotes: Camphora, Chamomilla, Mercurius, Pulsatilla"
        ));

        list.add(MedicineConstituent.of(
                "sil", "Silicea", "Pure Silica / Quartz / Flints",
                "Silicon dioxide (SiO2)",
                "Indispensable structural mineral for glycosaminoglycan synthesis and collagen cross-linking. Enhances macrophage phagocytosis, promotes resolution and cellular suppuration, and expedites the cellular demarcation and expulsion of foreign bodies, fistulae, and necrotic bone.",
                "Connective tissue, periosteum, bones, lymph nodes, nails, hair, teeth, fibrous cicatrices",
                "Antidotes: Camphora, Fluoric acid, Hepar sulph; incompatible: Mercurius solubilis"
        ));

        list.add(MedicineConstituent.of(
                "puls", "Pulsatilla pratensis", "Wind Flower / Pasque Flower",
                "Anemonin, protoanemonin, ranunculin, triterpenoid saponins",
                "Modulates venous vascular tone and pituitary-gonadal endocrine balance. Corrects venous stasis, wandering erratic pains, bland thick yellowish-green catarrhal secretions, thirstlessness, tearful disposition, and desire for open fresh air.",
                "Venous circulation, mucous membranes, synovial membranes, reproductive endocrine axis",
                "Antidotes: Chamomilla, Coffea, Ignatia, Nux vomica"
        ));

        list.add(MedicineConstituent.of(
                "sep", "Sepia officinalis", "Inky Juice of Cuttlefish",
                "Melanin, sepia-pterin, calcium carbonate, magnesium carbonate, trace marine oligoelements",
                "Regulates venous stasis in the portal and pelvic circulations, restoring pelvic ligamentous tone. Resolves uterine and visceral prolapse (bearing-down sensation), hormonally triggered chloasma/melasma, portal headaches, and emotional indifference to loved ones.",
                "Pelvic venous plexus, uterus and adnexa, portal circulation, autonomic nervous system",
                "Antidotes: Aconitum, Smilax (Sarsaparilla), Sulphur; complementary: Natrum mur"
        ));

        list.add(MedicineConstituent.of(
                "sulph", "Sulphur", "Sublimed Flowers of Sulphur / Gandhak",
                "Elemental Sublimed Sulphur (S8)",
                "Fundamental cellular sulfhydryl (-SH) catalyst essential for glutathione transferase, protein synthesis, and transsulfuration pathways. Powerful reactive antipsoric remedy, activating sluggish vitality, resolving suppressed eruptions, burning heat in orifices, and morning ravenous hunger.",
                "Skin, venous portal system, mucous membrane orifices, lymphatic system, nervous system",
                "Antidotes: Aconitum, Camphora, Chamomilla, Mercury, Nux vomica, Pulsatilla"
        ));

        list.add(MedicineConstituent.of(
                "ocim", "Ocimum sanctum", "Tulsi / Holy Basil",
                "Eugenol, ursolic acid, rosmarinic acid, carvacrol, linalool, beta-caryophyllene",
                "Inhibits COX-2, modulates mucosal cellular immunity, and exerts anti-spasmodic bronchial relaxation. Adaptogenic, antipyretic in viral influenza, and promotes renal tubular urate clearance.",
                "Respiratory mucous membranes, bronchial tree, renal parenchyma, immune system",
                "Antidoted by Camphora"
        ));

        list.add(MedicineConstituent.of(
                "holar", "Holarrhena antidysenterica", "Kurchi / Kutaj / Indrajau",
                "Conessine, kurchine, holarrhenine, conessimine, kurchicine",
                "Specific direct amoebicidal and spasmolytic action against *Entamoeba histolytica* trophozoites and inflammatory colonic hypermotility. Resolves acute and chronic amoebic dysentery with bloody mucous stools and griping tenesmus.",
                "Colon, large intestinal mucosa, mesenteric lymphatics, portal circulation",
                "Antidoted by Nux vomica"
        ));

        list.add(MedicineConstituent.of(
                "boerh", "Boerhavia diffusa", "Punarnava / Spreading Hogweed",
                "Punarnavine, boeravinones A-F, potassium nitrate, ursolic acid, sitosterol",
                "Activates renal tubular sodium-potassium ATPase, significantly increasing glomerulo-tubular filtration and diuretic fluid excretion. Corrects generalized anasarca, ascites, nephrotic syndrome, and hepatic fluid retention.",
                "Renal glomeruli and collecting tubules, liver parenchyma, cardiovascular system",
                "Antidoted by Camphora"
        ));

        list.add(MedicineConstituent.of(
                "gymn", "Gymnema sylvestre", "Gurmar / Madhunashini / Cowplant",
                "Gymnemic acids I-XVIII, gurmarin, gymnemasaponins, stigmasterol",
                "Selective reversible blockade of sweet taste receptors on tongue papillae and suppression of intestinal SGLT1 sodium-dependent glucose transporters. Stimulates pancreatic insulin secretion and regenerates beta-islet tissue.",
                "Pancreatic beta cells, intestinal brush border membrane, gustatory taste receptors",
                "Antidoted by Nux vomica"
        ));

        list.add(MedicineConstituent.of(
                "andro", "Andrographis paniculata", "Kalmegh / King of Bitters / Mahatita",
                "Andrographolide, neoandrographolide, 14-deoxyandrographolide, flavonoids",
                "Suppresses NF-κB nuclear translocation, enhances biliary choleretic flow, protects hepatocytes from chemical necrosis, and stimulates lymphocyte proliferation. Rapidly resolves infective jaundice, sluggish liver, and malarial cachexia.",
                "Hepatobiliary system, reticuloendothelial cells, spleen, gastrointestinal mucosa",
                "Antidoted by Carbo veg, Nux vomica"
        ));

        list.add(MedicineConstituent.of(
                "ficus-r", "Ficus religiosa", "Ashwatha / Peepal / Sacred Fig",
                "Beta-sitosterol, stigmasterol, tannins, quercetin, kaempferol, bergapten",
                "Potent microvascular vasoconstrictor and hemostatic astringent. Stimulates coagulation cascade at capillary bed, rapidly arresting hematemesis, hematuria, menorrhagia, and dysenteric bloody stool without secondary thrombosis.",
                "Capillary vascular walls, mucous membranes, uterine vascular bed, kidneys",
                "Antidoted by Camphora"
        ));

        list.add(MedicineConstituent.of(
                "chin", "Cinchona officinalis", "Peruvian Bark / China / Quinquina",
                "Quinine, quinidine, cinchonine, cinchonidine, quinic acid, cinchotannic acid",
                "Inhibits hemozoin crystallization in erythrocytes and modulates myocardial cardiac membrane channels. Indispensable for severe exhaustion, cachexia, and nervous erethism following loss of vital bodily fluids (blood, semen, diarrhea, prolonged nursing).",
                "Red blood cells, portal circulation, auditory nerve, spleen, gastrointestinal tract",
                "Antidotes: Arnica, Arsenicum, Carbo veg, Ferrum, Lachesis; incompatible: Digitalis"
        ));

        list.add(MedicineConstituent.of(
                "coloc", "Citrullus colocynthis", "Bitter Apple / Colocynth / Indrayan",
                "Colocynthin, colocynthein, cucurbitacin E (elaterin), cucurbitacin L glycosides",
                "Blocks visceral neuromuscular acetylcholine release and smooth muscle spasms. Instantly relieves excruciating cramping neuralgias, renal colic, and dysmenorrhea; characteristically forced to double up and apply hard firm pressure.",
                "Abdominal autonomic nerves, mesenteric plexus, colon, ureters, trigeminal and sciatic nerves",
                "Antidotes: Camphora, Causticum, Chamomilla, Staphysagria"
        ));

        list.add(MedicineConstituent.of(
                "cham", "Matricaria chamomilla", "German Chamomile / Babuna",
                "Chamazulene, alpha-bisabolol, apigenin, matricin, flavonoids",
                "Apigenin acts as a ligand for central GABA-A benzodiazepine receptors, while chamazulene inhibits 5-lipoxygenase and leukotriene B4. Resolves hyperesthetic intolerance to pain, uncivil irritability, teething convulsions, and emotional tantrums in children.",
                "Central nervous system, cranial sensory nerves, gastrointestinal tract, auditory nerves",
                "Antidotes: Coffea cruda, Ignatia, Pulsatilla, Nux vomica"
        ));

        list.add(MedicineConstituent.of(
                "gels", "Gelsemium sempervirens", "Yellow Jasmine",
                "Gelsemine, sempervirine, gelseminine, gelsemic acid",
                "Selective central motor depressant, blocking nerve impulse transmission at spinal anterior horns. Induces motor paralysis, profound muscular heaviness, drooping of eyelids (ptosis), complete lack of thirst, and trembling from anticipatory fright.",
                "Spinal cord motor neurons, ocular motor nerves, heart, vasomotor nerves",
                "Antidotes: Coffea, Digitalis, China, Atropine"
        ));

        list.add(MedicineConstituent.of(
                "ign", "Strychnos ignatii", "St. Ignatius Bean",
                "Strychnine, brucine, loganic acid",
                "Acts upon the limbic system and emotional-motor coordinators. Produces rapid contradictory, paradoxical somatic symptoms, globus hystericus, spasms of laughing and weeping, and neurovegetative consequences of acute silent grief or disappointed love.",
                "Limbic emotional system, medulla oblongata, pharyngeal and esophageal nerves, spinal cord",
                "Antidotes: Pulsatilla, Chamomilla, Zincum; incompatible: Coffea"
        ));

        list.add(MedicineConstituent.of(
                "ip", "Carapichea ipecacuanha", "Ipecacuanha / Ipecac",
                "Emetine, cephaeline, psychotrine, emetamine, ipecacuanhic acid",
                "Potent agonist of central dopamine D2 receptors in the chemoreceptor trigger zone (CTZ) and local irritant of gastric sensory vagal afferents. Produces unyielding, continuous nausea not relieved by vomiting, with clean tongue and spasmodic dry suffocative cough.",
                "Pneumogastric (vagus) nerve, vomiting center in medulla, bronchial mucous membranes",
                "Antidotes: Arsenicum, Arnica, Nux vomica, Tabacum"
        ));

        list.add(MedicineConstituent.of(
                "led", "Ledum palustre", "Marsh Tea / Wild Rosemary",
                "Ledol, palustrol, valeric acid, arbutin, flavonoids",
                "Reduces capillary hyperpermeability and local neurogenic extravasation in puncture wounds (nails, rat/insect bites, animal scratches). The affected wounded part feels objectively cold to touch and radiates numbness, yet is remarkably ameliorated by ice-cold water plunge.",
                "Capillaries, sensory nerve terminals, small joint periosteum (especially ankles and heels)",
                "Antidotes: Camphora, Rhus tox"
        ));

        list.add(MedicineConstituent.of(
                "ruta", "Ruta graveolens", "Bitter Herb / Rue / Sadab",
                "Rutin, graveoline, skimmianine, arborinine, coumarins (bergapten, psoralen)",
                "High-affinity periosteal stimulant and vascular bioflavonoid strengthener. Accelerates osteoblastic and cartilaginous repair in deep periosteal trauma, flexor tendon sprains, ganglion of wrist, and chronic ciliary eyestrain from fine needlework or digital screens.",
                "Periosteum, cartilages, tendons, ciliary muscle of eye, lower colon and rectum",
                "Antidotes: Camphora; complementary: Calcarea phosphorica"
        ));

        list.add(MedicineConstituent.of(
                "hepar", "Hepar sulphuris calcareum", "Hahnemann's Calcium Sulphide",
                "Impure calcium sulphide (CaS) prepared from calcined oyster shells and washed flowers of sulphur",
                "Enhances chemotactic polymorphonuclear leukocyte infiltration and suppurative tissue demarcation. Promotes rapid ripening or abortive resolution of acute abscesses, with extreme splinter-like stitching pains and violent hypersensitivity to cold air and touch.",
                "Lymphatic nodes, skin glands, mucous membranes, cellular connective tissue",
                "Antidotes: Belladonna, Chamomilla, Silicea; incompatible: Nitric acid"
        ));

        list.add(MedicineConstituent.of(
                "merc", "Mercurius solubilis", "Hahnemann's Soluble Mercury",
                "Precipitated ammonio-nitrate of sub-oxide of mercury (Hg2O + NH4NO3)",
                "Binds active protein sulfhydryl enzymes, producing profound glandular enlargement, profuse nocturnal salivation, foul fœtid breath, night sweats that afford no relief, and tremors; worse at night and from damp heat.",
                "Salivary glands, buccal mucosa, lymphatic system, liver, bones, mucous membranes",
                "Antidotes: Hepar sulph, Aurum, Kali iodatum, Nitric acid; incompatible: Silicea"
        ));

        list.add(MedicineConstituent.of(
                "calc", "Calcarea carbonica", "Middle Layer of Oyster Shell",
                "Calcium carbonate (CaCO3) with trace organic conchiolin and mineral salts",
                "Regulates cellular calcium flux, skeletal osteogenesis, and mesenteric lymphatic absorption. Addresses the chilly, flabby, leuco-phlegmatic constitution with profuse sour head sweat during sleep, cold clammy feet, and delayed fontanelle closure.",
                "Bones, mesenteric glands, thyroid, skin, blood-forming organs",
                "Antidotes: Camphora, Ipecacuanha, Nitric acid, Sulphur"
        ));

        list.add(MedicineConstituent.of(
                "nat-m", "Natrum muriaticum", "Pure Sodium Chloride",
                "Sodium chloride (NaCl)",
                "Primary regulator of physiological extracellular osmotic equilibrium and water distribution. Restores systemic fluid homeostasis in chronic emaciation, mapped tongue, anemic throbbing burst headaches worse 10 AM, craving for salt, and worsening from emotional consolation.",
                "Extracellular fluid compartment, nervous system, blood plasma, mucous membranes, skin",
                "Antidotes: Arsenicum, Phosphorus, Spir. aeth. nitr.; complementary: Sepia, Ignatia"
        ));

        list.add(MedicineConstituent.of(
                "ferr-p", "Ferrum phosphoricum", "Ferroso-ferric Phosphate",
                "Iron phosphate (Fe3(PO4)2 · 8H2O)",
                "Essential catalyst in mitochondrial cytochrome c oxidase and hemoglobin cellular oxygen transport. Chief biochemic remedy for the initial stage of acute inflammatory and febrile congestion before exudative suppuration.",
                "Capillary vascular walls, erythrocytes, lungs, ears, pharynx",
                "Antidotes: Pulsatilla, Arsenicum"
        ));

        list.add(MedicineConstituent.of(
                "mag-p", "Magnesia phosphorica", "Magnesium Phosphate",
                "Magnesium phosphate (MgHPO4 · 7H2O)",
                "Essential intracellular cofactor for ATP-dependent neuromuscular relaxation and nerve fiber transmission. Great homeopathic antispasmodic for lightning-like darting neuralgias, abdominal cramps, and dysmenorrhea rapidly relieved by radiant heat and hard pressure.",
                "Spinal motor nerves, involuntary smooth muscle fibers, cardiac muscle",
                "Antidotes: Belladonna, Gelsemium, Camphora"
        ));

        return list;
    }
}
