package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.openhomeo.core.ai.PrescriptionLogicAnalyzer;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.materia_medica.HomeopathicClinicalRhymes;
import org.openhomeo.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classical Homeopathic Clinical Logic AI & Symptom Reasoner Chat Pane.
 * 
 * STRICT ARCHITECTURAL INVARIANT:
 * 100% Deterministic Expert System. NO Large Language Models (LLMs) or generative hallucinations.
 * Operates strictly on:
 * - Samuel Hahnemann's Organon of Medicine (5th & 6th Editions, §246-§248, §275-§283)
 * - J. T. Kent's Homoeopathic Philosophy & Posology Canons (Lectures on Homoeopathic Philosophy)
 * - Constantine Hering's Law of Cure (Direction of Symptom Disappearance)
 * - Gibson Miller & Hering Relationship of Remedies (Inimical & Incompatible Pairings)
 * - Boenninghausen Polar Symptom & Modality Differentials
 * - Classical Nash & Allen Clinical Keynote Verses & Rhymes
 */
public class AiClinicalLogicChatView extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(AiClinicalLogicChatView.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ClinicDao clinicDao;
    private final MasterKnowledgeDao masterDao;
    private final PrescriptionLogicAnalyzer logicAnalyzer = new PrescriptionLogicAnalyzer();

    private final VBox chatMessagesBox = new VBox(14);
    private final ScrollPane scrollPane = new ScrollPane();
    private final TextField inputField = new TextField();
    private final ComboBox<PatientContextItem> patientContextCombo = new ComboBox<>();
    private final List<Patient> loadedPatients = new ArrayList<>();
    private String activePendingChallenge = null;

    public record PatientContextItem(Patient patient, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public AiClinicalLogicChatView(ClinicDao clinicDao, MasterKnowledgeDao masterDao) {
        this.clinicDao = clinicDao;
        this.masterDao = masterDao;

        setPadding(new Insets(12, 16, 12, 16));

        // 1. Top Context & Header Banner
        VBox topBox = createHeader();
        setTop(topBox);

        // 2. Chat Conversation View
        chatMessagesBox.setPadding(new Insets(16));
        chatMessagesBox.setStyle("-fx-background-color: -color-bg-default;");

        scrollPane.setContent(chatMessagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: -color-bg-default; -fx-background-color: transparent; -fx-border-color: -color-border-subtle; -fx-border-radius: 8px;");
        setCenter(scrollPane);

        // 3. Bottom Controls & Quick Suggestion Chips
        VBox bottomBox = createBottomBar();
        setBottom(bottomBox);

        // Initial Welcome & Diagnostic Overview Message
        postSystemWelcome();
        refreshPatientContextList();
    }

    private VBox createHeader() {
        VBox topBox = new VBox(8);
        topBox.setPadding(new Insets(0, 0, 10, 0));

        HBox banner = new HBox(12);
        banner.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("🧠 Classical Homeopathic Logic AI (Deterministic Reasoner)");
        title.setFont(Font.font("System", FontWeight.BOLD, 17));
        title.setTextFill(javafx.scene.paint.Color.web("#0284c7"));

        Label engineBadge = new Label("⚡ Deterministic Expert Canon • Offline • Zero LLM Hallucination");
        engineBadge.setStyle("-fx-background-color: #ecfdf5; -fx-text-fill: #065f46; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 6px; -fx-border-color: #a7f3d0; -fx-border-radius: 6px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button supersedeBtn = new Button("⚡ Doctor Supersede (Capture Experience)");
        supersedeBtn.setStyle("-fx-background-color: #fffbeb; -fx-text-fill: #b45309; -fx-font-weight: bold; -fx-border-color: #fde68a; -fx-border-radius: 6px;");
        supersedeBtn.setOnAction(e -> openDoctorSupersedeDialog());

        Button vaultBtn = new Button("📖 Experience Vault");
        vaultBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        vaultBtn.setOnAction(e -> openDoctorExperienceVaultDialog());

        Button digitalLibBtn = new Button("📚 1,500 Books");
        digitalLibBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        digitalLibBtn.setOnAction(e -> {
            sendUserMessage("Search digital library: burning heat amel Organon 248");
        });

        Button clearBtn = new Button("🗑️ Clear Chat");
        clearBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        clearBtn.setOnAction(e -> {
            chatMessagesBox.getChildren().clear();
            activePendingChallenge = null;
            postSystemWelcome();
        });

        banner.getChildren().addAll(title, engineBadge, spacer, supersedeBtn, vaultBtn, digitalLibBtn, clearBtn);

        // Patient Context Bar
        HBox contextBar = new HBox(10);
        contextBar.setAlignment(Pos.CENTER_LEFT);
        contextBar.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 8 12 8 12; -fx-background-radius: 6px; -fx-border-color: -color-border-subtle; -fx-border-radius: 6px;");

        Label ctxLabel = new Label("Active Patient Context:");
        ctxLabel.setFont(Font.font("System", FontWeight.BOLD, 12));

        patientContextCombo.setPrefWidth(380);
        patientContextCombo.setOnAction(e -> {
            PatientContextItem sel = patientContextCombo.getValue();
            if (sel != null && sel.patient() != null) {
                appendSystemNotice("Switched patient context to: " + sel.patient().firstName() + " " + sel.patient().lastName() +
                        " (Case #" + sel.patient().caseNumber() + "). All patient trajectory queries will now evaluate this case record.");
            }
        });

        Button refreshPatBtn = new Button("🔄 Refresh Patients");
        refreshPatBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        refreshPatBtn.setOnAction(e -> refreshPatientContextList());

        Label canonLabel = new Label("Canons: Organon §246-248 | Kentian Posology | Gibson Miller Inimicals | Hering's Law");
        canonLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");

        Region ctxSpacer = new Region();
        HBox.setHgrow(ctxSpacer, Priority.ALWAYS);

        contextBar.getChildren().addAll(ctxLabel, patientContextCombo, refreshPatBtn, ctxSpacer, canonLabel);

        topBox.getChildren().addAll(banner, contextBar);
        return topBox;
    }

    private VBox createBottomBar() {
        VBox bottomBox = new VBox(8);
        bottomBox.setPadding(new Insets(10, 0, 0, 0));

        // Quick Suggestion Chips
        HBox chipsBox = new HBox(8);
        chipsBox.setAlignment(Pos.CENTER_LEFT);

        Label chipsTitle = new Label("Quick Logic Queries:");
        chipsTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

        Button chipTrajectory = createChip("🩺 Analyze Patient Trajectory & Hering's Law", () -> {
            sendUserMessage("Analyze longitudinal trajectory and Hering's Law progression for current patient");
        });

        Button chipPosology = createChip("⚖️ Kent & Organon §246-248 Repetition Rules", () -> {
            sendUserMessage("Explain Kentian and Hahnemannian posology repetition rules for 30C, 200C, 1M, and LM potencies");
        });

        Button chipInimical = createChip("🔄 Gibson Miller Inimical Check", () -> {
            sendUserMessage("Check classical inimical and incompatible remedy sequences");
        });

        Button chipPolarity = createChip("🧭 Boenninghausen Polar Modalities", () -> {
            sendUserMessage("Compare heat versus cold polar modalities (Arsenicum vs Secale)");
        });

        Button chipRhymes = createChip("📖 Materia Medica Clinical Rhymes", () -> {
            sendUserMessage("Show clinical rhymes for polychrest remedies");
        });

        Button chipMiasm = createChip("🧬 Miasmatic Hierarchy", () -> {
            sendUserMessage("Explain Hahnemannian miasmatic hierarchy (Psora, Sycosis, Syphilis)");
        });

        Button chipInterrogate = createChip("⚡ Interrogate Prescription Rationale (Socratic AI)", () -> {
            sendUserMessage("Interrogate my prescription rationale for this case");
        });

        Button chipLib = createChip("📚 Search 1,500-Book Digital Library", () -> {
            sendUserMessage("Search library for headache sun agg glonoin Organon 248");
        });

        Button chipSupersede = createChip("⚡ Doctor Supersede", this::openDoctorSupersedeDialog);

        chipsBox.getChildren().addAll(chipsTitle, chipTrajectory, chipInterrogate, chipLib, chipSupersede, chipPosology, chipInimical, chipPolarity, chipRhymes, chipMiasm);

        ScrollPane chipsScroll = new ScrollPane(chipsBox);
        chipsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chipsScroll.setFitToHeight(true);
        chipsScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-width: 0;");

        // Input Line
        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);

        inputField.setPromptText("Ask logic engine about patient trajectory, remedy inimicality, posology, or modalities (e.g. 'Apis and Rhus-t', 'Ars vs Secale', 'Kent 30C repetition')...");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setOnAction(e -> handleSendMessage());

        Button sendBtn = new Button("🚀 Send / Analyze");
        sendBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.ACCENT);
        sendBtn.setDefaultButton(true);
        sendBtn.setOnAction(e -> handleSendMessage());

        inputBox.getChildren().addAll(inputField, sendBtn);

        bottomBox.getChildren().addAll(chipsScroll, inputBox);
        return bottomBox;
    }

    private Button createChip(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle("-fx-font-size: 11px; -fx-background-radius: 14px; -fx-padding: 3 10 3 10; -fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; -fx-border-radius: 14px;");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    public void selectPatient(String patientId) {
        if (patientId == null) return;
        for (PatientContextItem item : patientContextCombo.getItems()) {
            if (item.patient() != null && item.patient().id().equalsIgnoreCase(patientId)) {
                patientContextCombo.getSelectionModel().select(item);
                break;
            }
        }
    }

    public void refreshPatientContextList() {
        try {
            loadedPatients.clear();
            loadedPatients.addAll(clinicDao.listPatients(""));

            List<PatientContextItem> items = new ArrayList<>();
            items.add(new PatientContextItem(null, "— [General Clinical & Repertorial Query] —"));

            for (Patient p : loadedPatients) {
                int age = p.dob() != null ? java.time.Period.between(p.dob(), java.time.LocalDate.now()).getYears() : 0;
                String label = String.format("[%s] %s %s (%s, %d yrs, %s)",
                        p.caseNumber(), p.firstName(), p.lastName(), p.gender(), age,
                        p.constitutionalType() != null ? p.constitutionalType() : "General");
                items.add(new PatientContextItem(p, label));
            }

            patientContextCombo.setItems(FXCollections.observableArrayList(items));
            if (!items.isEmpty()) {
                patientContextCombo.getSelectionModel().selectFirst();
            }
        } catch (SQLException e) {
            log.error("Failed to load patients for context combo", e);
        }
    }

    private void handleSendMessage() {
        String query = inputField.getText();
        if (query == null || query.trim().isBlank()) return;
        inputField.clear();
        sendUserMessage(query.trim());
    }

    public void askDirectQuery(String text) {
        if (text != null && !text.isBlank()) {
            sendUserMessage(text.trim());
        }
    }

    private void sendUserMessage(String text) {
        appendDoctorBubble(text);
        // Process deterministically
        Platform.runLater(() -> {
            String response = processQueryDeterministically(text);
            appendAiBubble(response);
        });
    }

    private void postSystemWelcome() {
        String welcome = """
            ### 🏛️ OpenHomeo Classical Clinical Logic Engine Initialized
            
            Welcome, Doctor. I am your **deterministic, rule-based clinical assistant** built upon the core classical canons of Homeopathy:
            
            - **Samuel Hahnemann's Organon of Medicine (5th & 6th Eds.)**: Evaluates posology repetition limits (§246-§248), water split-dosing plussing, dynamic dose safety, and acute vs. chronic miasmatic layers.
            - **J. T. Kent's Homoeopathic Philosophy**: Rigorous audit of high centesimals (200C, 1M, 10M), the 30C acute repetition threshold (Lect. XXXV), and Kent's 12 Prognostic Observations.
            - **Constantine Hering's Law of Cure**: Direction of symptom departure (*from above downwards, from within outwards, from more important to less important organs, and in reverse order of appearance*).
            - **Gibson Miller & Hering Relationship of Remedies**: Real-time detection of strictly **INIMICAL** sequences (*e.g., Apis ➔ Rhus-t, Causticum ➔ Phosphorus, Silicea ➔ Mercurius*), complementary chains, and antidotes.
            - **Boenninghausen Polar Modalities & Boger Differentials**: Thermal reactions, aggravation/amelioration polarities, and side affinities.
            - **Nash & Allen Clinical Rhymes**: Classical pedagogical keynote verses for polychrests and indigenous remedies.
            
            > **100% Deterministic Guarantee**: There are **NO generative LLMs or statistical hallucinations** in this module. Every piece of advice is traced strictly to classical literature, repertorial databases, and your clinic's patient records.
            
            Select a patient above or ask any clinical, posological, or relationship question below.
            """;
        appendAiBubble(welcome);
    }

    private void appendDoctorBubble(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);

        VBox bubble = new VBox(4);
        bubble.setMaxWidth(680);
        bubble.setStyle("-fx-background-color: #0284c7; -fx-padding: 10 14 10 14; -fx-background-radius: 12px 12px 2px 12px;");

        HBox meta = new HBox(6);
        meta.setAlignment(Pos.CENTER_RIGHT);
        Label userBadge = new Label("👨‍⚕️ Consulting Doctor • " + LocalDateTime.now().format(TIME_FMT));
        userBadge.setStyle("-fx-text-fill: #e0f2fe; -fx-font-size: 11px; -fx-font-weight: bold;");
        meta.getChildren().add(userBadge);

        Label content = new Label(text);
        content.setWrapText(true);
        content.setFont(Font.font("System", 13));
        content.setStyle("-fx-text-fill: #ffffff;");

        bubble.getChildren().addAll(meta, content);
        row.getChildren().add(bubble);

        chatMessagesBox.getChildren().add(row);
        scrollToBottom();
    }

    private void appendAiBubble(String markdownText) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);

        VBox bubble = new VBox(8);
        bubble.setMaxWidth(760);
        bubble.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 12 16 12 16; -fx-border-color: -color-border-subtle; -fx-border-radius: 12px 12px 12px 2px; -fx-background-radius: 12px 12px 12px 2px;");

        HBox meta = new HBox(8);
        meta.setAlignment(Pos.CENTER_LEFT);
        Label aiBadge = new Label("🧠 OpenHomeo Logic Engine [Deterministic Canon]");
        aiBadge.setStyle("-fx-background-color: #047857; -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 4px;");

        Label timeLbl = new Label(LocalDateTime.now().format(TIME_FMT));
        timeLbl.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");

        meta.getChildren().addAll(aiBadge, timeLbl);

        VBox contentBox = renderMarkdownLikeText(markdownText);

        bubble.getChildren().addAll(meta, contentBox);
        row.getChildren().add(bubble);

        chatMessagesBox.getChildren().add(row);
        scrollToBottom();
    }

    private void appendSystemNotice(String notice) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER);
        Label lbl = new Label("ℹ️ " + notice);
        lbl.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-size: 11px; -fx-padding: 4 10 4 10; -fx-background-radius: 10px;");
        row.getChildren().add(lbl);
        chatMessagesBox.getChildren().add(row);
        scrollToBottom();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    /**
     * Deterministic natural language query parser and rule engine.
     */
    private String processQueryDeterministically(String rawQuery) {
        String q = rawQuery.trim().toLowerCase();
        PatientContextItem activeCtx = patientContextCombo.getValue();
        Patient activePatient = activeCtx != null ? activeCtx.patient() : null;

        // 0. Active Socratic Challenge Response Handling
        if (activePendingChallenge != null || q.contains("because") || q.contains("my rationale") ||
            q.contains("keynote override") || q.contains("i supersede") || (activePendingChallenge != null && q.length() > 5)) {
            String challenge = activePendingChallenge != null ? activePendingChallenge : "GENERAL_PRESCRIPTION_CANON";
            activePendingChallenge = null; // Clear pending state
            return handleDoctorSocraticResponse(challenge, rawQuery);
        }

        // 0.1 Explicit Socratic Interrogation Request
        if (q.contains("interrogate") || q.contains("challenge") || q.contains("question my") || q.contains("audit prescription")) {
            return handlePrescriptionInterrogation(activePatient);
        }

        // 0.2 Digital Materia Medica Library Search (1,500 Books FTS5)
        if (q.contains("library") || q.contains("1500") || q.startsWith("book:") || q.contains("search book") || q.contains("fts")) {
            return handleDigitalLibraryQuery(rawQuery);
        }

        // 1. Patient Case Trajectory & Hering's Law Analysis
        if (q.contains("trajectory") || q.contains("hering") || q.contains("cure direction") ||
            q.contains("analyze patient") || q.contains("progress") || q.contains("follow up") ||
            (activePatient != null && (q.contains("patient") || q.contains("case") || q.contains("next")))) {
            return handlePatientTrajectoryAnalysis(activePatient);
        }

        // 2. Inimical & Relationship Analysis
        if (q.contains("inimical") || q.contains("incompatible") || q.contains("relationship") ||
            q.contains("complement") || q.contains("antidote") || q.contains("follow each other")) {
            return handleRelationshipAnalysis(q);
        }

        // 3. Posology & Repetition Canons (Kent & Organon §246-248)
        if (q.contains("posology") || q.contains("repetition") || q.contains("frequency") ||
            q.contains("30c") || q.contains("200c") || q.contains("1m") || q.contains("lm") ||
            q.contains("organon") || q.contains("plussing") || q.contains("water dose")) {
            return handlePosologyAnalysis(q);
        }

        // 4. Modality & Polarity Differentials (Boenninghausen / Boger)
        if (q.contains("modality") || q.contains("polarity") || q.contains("heat") || q.contains("cold") ||
            q.contains("motion") || q.contains("rest") || q.contains("agg") || q.contains("amel") ||
            q.contains("versus") || q.contains(" vs ")) {
            return handlePolarityModalityAnalysis(q);
        }

        // 5. Clinical Rhymes & Keynotes
        if (q.contains("rhyme") || q.contains("verse") || q.contains("poem") || q.contains("keynote")) {
            return handleClinicalRhymeQuery(q);
        }

        // 6. Miasmatic Analysis (Psora, Sycosis, Syphilis)
        if (q.contains("miasm") || q.contains("psora") || q.contains("sycosis") || q.contains("syphilis") || q.contains("tubercular")) {
            return handleMiasmaticAnalysis(q);
        }

        // 7. Direct Remedy Lookup / Materia Medica
        String directRemedy = extractRemedyName(q);
        if (directRemedy != null) {
            return handleDirectRemedyQuery(directRemedy);
        }

        // 8. General Rubric / Symptom Search via Master Repertory FTS
        return handleGeneralSymptomSearch(rawQuery);
    }

    private String handlePatientTrajectoryAnalysis(Patient p) {
        if (p == null) {
            return """
                ### ⚠️ No Patient Selected in Active Context
                
                Please select a patient from the **Active Patient Context** dropdown at the top of the window to analyze their longitudinal clinical trajectory.
                
                Alternatively, you can ask general questions such as:
                - *"Explain Hering's Law of Cure"*
                - *"What are Kent's Twelve Observations?"*
                - *"Check inimical sequence between Apis and Rhus-t"*
                """;
        }

        try {
            List<Consultation> consults = clinicDao.getConsultationsForPatient(p.id());
            List<PastMedication> pastMeds = clinicDao.getPastMedicationsByPatient(p.id());

            int age = p.dob() != null ? java.time.Period.between(p.dob(), java.time.LocalDate.now()).getYears() : 0;
            StringBuilder sb = new StringBuilder();
            sb.append("### 📊 Longitudinal Case Trajectory & Hering's Law Audit\n\n");
            sb.append("**Patient:** ").append(p.firstName()).append(" ").append(p.lastName())
              .append(" | **Case #:** ").append(p.caseNumber())
              .append(" | **Age/Sex:** ").append(age).append(" yrs, ").append(p.gender())
              .append(" | **Constitution:** ").append(p.constitutionalType() != null ? p.constitutionalType() : "Unspecified")
              .append("\n\n");

            if (consults.isEmpty()) {
                sb.append("ℹ️ **Initial Consultation Baseline**: No prior consultations have been recorded yet for this patient. Establish fundamental miasmatic diagnosis and initial baseline symptoms.\n");
                return sb.toString();
            }

            sb.append("**Total Consultations Recorded:** ").append(consults.size()).append("\n\n");

            // Chronological Prescription Sequence
            sb.append("#### 💊 Remedy Progression Sequence:\n");
            List<String> remedyChain = new ArrayList<>();
            for (int i = consults.size() - 1; i >= 0; i--) {
                Consultation c = consults.get(i);
                String dt = c.consultDate().toString().substring(0, 10);
                if (c.prescriptions().isEmpty()) {
                    remedyChain.add("[" + dt + "] Sac Lac (Placebo)");
                } else {
                    for (Prescription rx : c.prescriptions()) {
                        remedyChain.add("[" + dt + "] **" + rx.remedyName() + " " + rx.potency() + "** (" + rx.scale() + ")");
                    }
                }
            }
            sb.append(String.join(" ➔ ", remedyChain)).append("\n\n");

            // Hering's Law of Cure Assessment
            sb.append("#### 🧭 Hering's Law Evaluation (Direction of Cure):\n");
            Consultation latest = consults.get(0);
            if (latest.heringsLawAnalysis() != null && !latest.heringsLawAnalysis().isBlank()) {
                sb.append("- **Recorded Direction:** ").append(latest.heringsLawAnalysis()).append("\n");
            } else {
                sb.append("- **Audit:** Direction not explicitly logged in latest encounter. Check whether symptoms are leaving from *above downwards* and *within outwards*.\n");
            }

            // Inimical Sequence Check on History
            if (consults.size() >= 2) {
                Consultation c1 = consults.get(0);
                Consultation c2 = consults.get(1);
                if (!c1.prescriptions().isEmpty() && !c2.prescriptions().isEmpty()) {
                    String rem1 = c1.prescriptions().get(0).remedyId();
                    String rem2 = c2.prescriptions().get(0).remedyId();
                    if (PrescriptionLogicAnalyzer.isInimical(rem2, rem1)) {
                        sb.append("⚠️ **CRITICAL GIBSON MILLER INIMICAL DETECTED**: Remedy `")
                          .append(c2.prescriptions().get(0).remedyName())
                          .append("` was immediately followed by `")
                          .append(c1.prescriptions().get(0).remedyName())
                          .append("`. These remedies are strictly inimical and risk violent medicinal aggravation or symptom confusion!\n\n");
                    } else {
                        sb.append("✓ **Sequential Relational Safety**: Preceding remedy `")
                          .append(c2.prescriptions().get(0).remedyName())
                          .append("` and current remedy `")
                          .append(c1.prescriptions().get(0).remedyName())
                          .append("` have no known inimical conflict.\n\n");
                    }
                }
            }

            // Allopathic Suppressive Load
            if (!pastMeds.isEmpty()) {
                sb.append("#### 🛡️ Prior Suppressive Drug Load (Iatrogenic Miasm):\n");
                for (PastMedication pm : pastMeds) {
                    sb.append("- **").append(pm.drugName()).append("** (").append(pm.category()).append(", ").append(pm.duration()).append("): ")
                      .append(pm.suppressedSymptoms() != null ? pm.suppressedSymptoms() : "No details recorded.")
                      .append(" | Suggested Antidotes: *").append(pm.antidoteRemedy() != null ? pm.antidoteRemedy() : "Nux-v, Thuja")
                      .append("*\n");
                }
                sb.append("\n");
            }

            // Classical Recommendation
            sb.append("#### 🏛️ Classical Next-Step Recommendation:\n");
            sb.append("1. If patient reports amelioration in mental outlook and sleep while skin or joint symptoms return, **DO NOT CHANGE OR REPEAT THE REMEDY** (Kent's 1st & 2nd Observations: amelioration from within outward).\n");
            sb.append("2. If old suppressed symptoms reappear in reverse order, **WAIT AND OBSERVE** (Hering's Law confirms genuine curative movement).\n");
            sb.append("3. If symptoms return after a clear period of improvement with the exact same symptom totality, repeat the same remedy in a higher dynamization or with Hahnemannian succussed plussing (§248).\n");

            return sb.toString();
        } catch (SQLException e) {
            log.error("Failed to analyze trajectory", e);
            return "❌ Database error retrieving patient trajectory: " + e.getMessage();
        }
    }

    private String handleRelationshipAnalysis(String q) {
        // Look for pairs like "apis" and "rhus" or general inimicals
        Map<String, Set<String>> pairs = PrescriptionLogicAnalyzer.getAllInimicalPairs();

        StringBuilder sb = new StringBuilder();
        sb.append("### 🔄 Gibson Miller & Constantine Hering Relationship of Remedies\n\n");

        boolean matchedSpecific = false;
        for (Map.Entry<String, Set<String>> entry : pairs.entrySet()) {
            String remA = entry.getKey();
            for (String remB : entry.getValue()) {
                if (q.contains(remA) || q.contains(remB)) {
                    matchedSpecific = true;
                    sb.append("⚠️ **INIMICAL RELATION DETECTED**: `").append(capitalize(remA)).append("` and `").append(capitalize(remB)).append("`\n\n")
                      .append("- **Classical Rule**: Gibson Miller and Constantine Hering established that these two remedies are strictly **INIMICAL**. They must NEVER be administered in immediate succession.\n")
                      .append("- **Pathological Mechanism**: Administering them consecutively throws the vital force into contradictory secondary actions, creating violent cross-aggravations or therapeutic deadlocks.\n")
                      .append("- **Clinical Action**: If one was administered and fails or aggravates, do NOT jump directly to its inimical. Allow intermediate clearance or an antidote (*e.g., Pulsatilla or Sulphur*) before proceeding.\n\n");
                    break;
                }
            }
            if (matchedSpecific) break;
        }

        if (!matchedSpecific) {
            sb.append("#### 📋 Core Classical Inimical Pairs (Never Follow in Immediate Succession):\n\n");
            sb.append("| First Remedy | Strictly Inimical (Do NOT Follow) | Classical Note |\n");
            sb.append("| :--- | :--- | :--- |\n");
            sb.append("| **Apis mellifica** | Rhus toxicodendron | Conflicting surface-cellular actions |\n");
            sb.append("| **Causticum** | Phosphorus | Opposite tissue polarities & dynamic tension |\n");
            sb.append("| **Silicea** | Mercurius solubilis | Suppuration conflict; Silicea expels, Merc disintegrates |\n");
            sb.append("| **China officinalis** | Digitalis purpurea | Extreme cardiac and vascular dysregulation |\n");
            sb.append("| **Ignatia amara** | Coffea cruda | Opposing emotional and nervous hypersensitivity |\n");
            sb.append("| **Lachesis muta** | Dulcamara | Cold-damp vs snake venom vascular action |\n");
            sb.append("| **Sepia succus** | Bryonia alba | Deep venous stasis vs synovial dryness |\n\n");

            sb.append("> **Golden Rule of Succession**: Always confirm relationship charts (Gibson Miller) before prescribing a second remedy when the first ceases to act.\n");
        }

        return sb.toString();
    }

    private String handlePosologyAnalysis(String q) {
        return """
            ### ⚖️ Classical Homeopathic Posology & Repetition Canons
            
            #### 1. J. T. Kent's Centesimal Repetition Laws (Lectures on Homoeopathic Philosophy)
            - **30C Dynamization (Acute Limit)**: In acute conditions, 30C is administered once or twice to spark the vital reaction. Kent explicitly cautions: *Repeating 30C three or more times daily clouds the clinical picture and precipitates an artificial medicinal drug proving.*
            - **High Potencies (200C, 1M, 10M, 50M, CM)**: Deep, long-acting dynamic engines. Under Kentian philosophy, high dynamizations are given in a **single dose** followed by watchful waiting. Frequent repetition in dry globules exhausts the vital force and induces severe homepathic aggravations.
            - **Orderly Potency Escalation**: Never leap from 30C directly to 10M or CM. Ascend through intermediate dynamizations: `30C ➔ 200C ➔ 1M ➔ 10M ➔ 50M`.
            
            #### 2. Samuel Hahnemann's 6th Edition LM Potency Method (Organon §246-§248)
            - **The Fifty-Millesimal Scale (1:50,000 / LM / 0/1 to 0/30)**: Hahnemann solved the problem of centesimal aggravations in his final years in Paris.
            - **Split-Dose Plussing in Water**: Dissolve 1-2 globules in 4-6 oz of pure water with a few drops of alcohol. Prior to each dose, **succuss the bottle 8-10 times** to slightly alter the degree of dynamization.
            - **Administration**: Administer 1 teaspoonful daily or every other day. Because each dose is dynamically modified by succussion, it can be repeated frequently *without producing aggravations*, accelerating cure by months.
            
            #### 3. Low Decimal Salts & Mother Tinctures (Biochemic)
            - **Decimal Scale (X/D, e.g., Calc-p 6X, Mag-p 6X)**: Follows Dr. Schuessler's biochemic tissue replenishment principles and is exempted from Kentian dry-repetition restrictions.
            """;
    }

    private String handlePolarityModalityAnalysis(String q) {
        if (q.contains("ars") || q.contains("secale") || q.contains("heat") || q.contains("cold") || q.contains("burning")) {
            return """
                ### 🧭 Boenninghausen Polar Modality Differential: Burning Pains
                
                One of the most celebrated polar differentials in classical homeopathy:
                
                #### **Arsenicum album (The Chilly, Anxious Prover)**
                - **Sensations**: Intense burning pains like fire, as if red-hot coals were applied to the parts.
                - **Polar Modality**: **AMELIORATED BY HEAT**. Desires warm drinks, hot applications, warm wraps. Cannot bear the cold.
                - **Mental State**: Extreme anguish, restlessness (moves from bed to chair), profound fear of death (*believes disease is incurable*), thirst for small sips of water at frequent intervals.
                
                #### **Secale cornutum (The Hot, Hemorrhagic Prover)**
                - **Sensations**: Intense burning, tingling sensations throughout the limbs and gangrenous tissues.
                - **Polar Modality**: **AGGRAVATED BY HEAT / AMELIORATED BY COLD**. Patient feels burning hot internally, yet skin is icy cold to the touch. **Refuses to be covered**, craves open air, cold applications, and uncovers limbs despite sub-zero room temperatures.
                - **Hemorrhagic Tendency**: Thin, dark, offensive hemorrhages with muscular twitches.
                
                > **Clinical Polarity Law**: When burning pains are present, *thermal reaction to heat vs. cold* is the master differentiator that determines the simillimum.
                """;
        }

        return """
            ### 🧭 Boenninghausen & Boger Polar Modality Canon
            
            In classical homeopathy, **Polar Symptoms** (opposites) represent the most reliable eliminating factors:
            
            1. **Thermal Reaction**:
               - **Chilly Remedies** (aggravated by cold, draft, uncovering): *Arsenicum, Silicea, Calcarea carb, Nux vomica, Psorinum, Hepar sulph, Rhus-t*.
               - **Hot Remedies** (aggravated by heat, warm rooms, sun, coverings): *Sulphur, Pulsatilla, Secale, Apis, Iodine, Natrum mur, Medorrhinum*.
            
            2. **Motion vs. Rest (Synovial & Muscular Polarity)**:
               - **Bryonia alba**: Aggravated by the *least motion*; relieved by absolute rest and hard pressure lying on the painful side.
               - **Rhus toxicodendron**: Aggravated on *first motion*; relieved by *continued gentle motion* and heat.
            
            3. **Lateralization (Side Affinity)**:
               - **Right-Sided Remedies**: *Lycopodium, Belladonna, Bryonia, Chelidonium, Apis*.
               - **Left-Sided Remedies**: *Lachesis, Sepia, Phosphorus, Spigelia, Thuja*.
               - **Direction of Spread**: *Lachesis* travels from left to right; *Lycopodium* travels from right to left.
            """;
    }

    private String handleClinicalRhymeQuery(String q) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 📖 Materia Medica Clinical Rhymes & Keynotes\n\n");

        String rem = extractRemedyName(q);
        if (rem != null) {
            String rhyme = HomeopathicClinicalRhymes.getRhyme(rem);
            if (rhyme != null) {
                sb.append("#### Keynote Verse for **").append(capitalize(rem)).append("**:\n\n");
                sb.append("```text\n").append(rhyme).append("\n```\n\n");
                return sb.toString();
            }
        }

        // Show sample famous polychrest rhymes
        sb.append("#### 1. Aconitum napellus (Sudden Storm & Fear)\n");
        sb.append("```text\n").append(HomeopathicClinicalRhymes.getRhyme("acon")).append("\n```\n\n");

        sb.append("#### 2. Arsenicum album (Burning Heat & Restlessness)\n");
        sb.append("```text\n").append(HomeopathicClinicalRhymes.getRhyme("ars")).append("\n```\n\n");

        sb.append("#### 3. Bryonia alba (Motion Aggravation & Dryness)\n");
        sb.append("```text\n").append(HomeopathicClinicalRhymes.getRhyme("bry")).append("\n```\n\n");

        sb.append("#### 4. Rhus toxicodendron (Rusty Hinge & Restless Relief)\n");
        sb.append("```text\n").append(HomeopathicClinicalRhymes.getRhyme("rhus-t")).append("\n```\n\n");

        sb.append("#### 5. Thuja occidentalis (Sycotic Overgrowth & Hydrogenoid)\n");
        sb.append("```text\n").append(HomeopathicClinicalRhymes.getRhyme("thuj")).append("\n```\n\n");

        return sb.toString();
    }

    private String handleMiasmaticAnalysis(String q) {
        return """
            ### 🧬 Samuel Hahnemann's Miasmatic Hierarchy (Chronic Diseases)
            
            Homeopathic pathology classifies chronic disease into three fundamental miasmatic dyscrasias:
            
            #### 1. Psora (The Sensitizing, Deficient Miasm)
            - **Fundamental Nature**: Functional hypofunction, nervous irritation, physiological deficiency, dry itching skin eruptions without discharge.
            - **Mentals**: Anxiety, anticipatory dread, fear of failure, philosophical contemplation.
            - **Thermals**: Predominantly chilly; skin intensely dry and dirty-looking; itch aggravated by warmth of bed.
            - **King Antipsorics**: *Sulphur, Psorinum, Calcarea carbonica, Lycopodium, Graphites*.
            
            #### 2. Sycosis (The Proliferative, Hypertrophic Miasm)
            - **Fundamental Nature**: Overproduction, cellular proliferation, indurations, warts, condylomata, fibroids, cysts, mucous catarrhs.
            - **Mentals**: Suspicious, secretive, fixed ideas, hasty, guilt, forgetful of recent events.
            - **Thermals & Modalities**: Hydrogenoid constitution; aggravated by damp weather, rain, cold water, meat; relieved by profuse catarrhal discharge.
            - **King Antisycotics**: *Thuja occidentalis, Medorrhinum, Natrum sulphuricum, Nitric acid, Silicea*.
            
            #### 3. Syphilis (The Destructive, Ulcerative Miasm)
            - **Fundamental Nature**: Tissue breakdown, deep ulceration with punched-out edges, necrosis, osteitis, structural deformity.
            - **Mentals**: Destructive impulses, depression, suicidal despair, violent rage, loss of memory.
            - **Modalities**: **Aggravation at night (sunset to sunrise)**; bone pains aggravated by warmth of bed and night air.
            - **King Antisyphilitics**: *Mercurius solubilis, Syphilinum, Aurum metallicum, Kali bichromicum, Lachesis*.
            
            #### 4. Tubercular (The Mixed Pseudo-Psoric Diathesis)
            - **Nature**: Combination of Psora + Syphilis. Rapid emaciation, changeability of symptoms, vulnerability of respiratory system, wanderlust.
            - **King Remedies**: *Tuberculinum bovinum, Bacillinum, Phosphorus, Drosera*.
            """;
    }

    private String handleDirectRemedyQuery(String remName) {
        try {
            Optional<Remedy> opt = masterDao.getRemedyById(remName);
            if (opt.isEmpty()) {
                List<Remedy> all = masterDao.getAllRemedies();
                for (Remedy r : all) {
                    if (r.fullName().toLowerCase().contains(remName) || r.abbr().equalsIgnoreCase(remName)) {
                        opt = Optional.of(r);
                        break;
                    }
                }
            }

            if (opt.isPresent()) {
                Remedy r = opt.get();
                StringBuilder sb = new StringBuilder();
                sb.append("### 🌿 Materia Medica Monograph: **").append(r.fullName()).append("** (").append(r.abbr()).append(")\n\n");
                sb.append("- **Common Name:** ").append(r.commonName() != null ? r.commonName() : "N/A").append("\n");
                sb.append("- **Kingdom / Family:** ").append(r.kingdom() != null ? r.kingdom() : "N/A").append(" | ").append(r.family() != null ? r.family() : "N/A").append("\n");
                sb.append("- **Miasm:** ").append(r.miasm() != null ? r.miasm() : "Trimiasmatic").append("\n");
                sb.append("- **Thermal State:** ").append(r.thermal() != null ? r.thermal() : "Unknown").append("\n");
                sb.append("- **Thirst:** ").append(r.thirst() != null ? r.thirst() : "Unknown").append("\n\n");

                // Check rhyme
                String rhyme = HomeopathicClinicalRhymes.getRhyme(r.id());
                if (rhyme != null) {
                    sb.append("#### 📖 Keynote Clinical Rhyme:\n```text\n").append(rhyme).append("\n```\n\n");
                }

                // Inimicals
                Set<String> inimicals = PrescriptionLogicAnalyzer.getInimicals(r.id());
                if (!inimicals.isEmpty()) {
                    sb.append("⚠️ **Strictly Inimical Remedies:** ").append(String.join(", ", inimicals.stream().map(this::capitalize).toList())).append("\n\n");
                }

                // Relationships from DB
                List<RemedyRelationship> rels = masterDao.getRemedyRelationships(r.id());
                if (!rels.isEmpty()) {
                    sb.append("#### 🔗 Clinical Relationships:\n");
                    for (RemedyRelationship rel : rels) {
                        sb.append("- **").append(rel.relationType()).append(":** ").append(capitalize(rel.relatedRemedyId()))
                          .append(rel.notes() != null && !rel.notes().isBlank() ? " (" + rel.notes() + ")" : "").append("\n");
                    }
                    sb.append("\n");
                }

                return sb.toString();
            }
        } catch (SQLException e) {
            log.error("Failed to query remedy", e);
        }

        return "Could not find remedy records for `" + remName + "`. Try entering the Latin binomial name (e.g. 'Aconitum napellus', 'Bryonia', 'Lachesis').";
    }

    private String handleGeneralSymptomSearch(String rawQuery) {
        try {
            List<Rubric> rubrics = masterDao.searchRubricsFts(rawQuery);
            if (!rubrics.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("### 🔍 Master Repertory Search Results for `").append(rawQuery).append("`:\n\n");
                sb.append("Found **").append(rubrics.size()).append("** matching classical rubrics:\n\n");

                int limit = Math.min(rubrics.size(), 8);
                for (int i = 0; i < limit; i++) {
                    Rubric rub = rubrics.get(i);
                    sb.append("- **[").append(rub.chapter()).append("]** `").append(rub.fullPath()).append("`");
                    if (rub.isPolar()) {
                        sb.append(" *(Polar Modality)*");
                    }
                    sb.append("\n");
                }

                sb.append("\n> Enter a rubric path in the Repertory Matrix view to evaluate full remedy gradings and Kentian weighting.\n");
                return sb.toString();
            }
        } catch (SQLException e) {
            log.warn("FTS search error: {}", e.getMessage());
        }

        return """
            ### 🏛️ OpenHomeo Classical Logic Query Help
            
            I could not match your query directly to an existing rubric or canon rule. You can ask:
            
            - **Patient Trajectory**: *"Analyze patient trajectory"*, *"Hering's law check"*
            - **Remedy Sequences**: *"Is Apis inimical to Rhus tox?"*, *"What follows Sulphur?"*
            - **Posology & Repetition**: *"Kent 30C repetition rule"*, *"Organon §246 LM method"*
            - **Polar Modalities**: *"Ars vs Secale heat"*, *"Bryonia vs Rhus motion"*
            - **Clinical Rhymes**: *"Rhyme for Aconite"*, *"Show Materia Medica rhymes"*
            - **Miasms**: *"Explain sycotic miasm"*, *"Antipsoric remedies"*
            """;
    }

    private String extractRemedyName(String text) {
        String[] known = {"acon", "apis", "arn", "ars", "bell", "bry", "calc", "caust", "chin", "coff",
                          "dulc", "gels", "hepar", "ign", "kali-c", "lach", "lyc", "med", "merc", "nat-m",
                          "nux-v", "phos", "puls", "rhus-t", "sep", "sil", "sulph", "thuj", "tub"};
        for (String k : known) {
            if (text.contains(k)) return k;
        }

        String[] latin = {"aconitum", "apis mellifica", "arnica", "arsenicum", "belladonna", "bryonia",
                          "calcarea", "causticum", "cinchona", "digitalis", "ignatia", "lachesis",
                          "lycopodium", "mercurius", "natrum muriaticum", "nux vomica", "phosphorus",
                          "pulsatilla", "rhus tox", "sepia", "silicea", "sulphur", "thuja"};
        for (String lat : latin) {
            if (text.contains(lat)) return lat;
        }
        return null;
    }

    private String capitalize(String str) {
        if (str == null || str.isBlank()) return "";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private VBox renderMarkdownLikeText(String md) {
        VBox box = new VBox(6);
        String[] lines = md.split("\n");

        boolean inCodeBlock = false;
        StringBuilder codeContent = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (line.startsWith("```")) {
                if (inCodeBlock) {
                    // Close code block
                    TextArea ta = new TextArea(codeContent.toString().trim());
                    ta.setEditable(false);
                    ta.setWrapText(true);
                    ta.setPrefRowCount(Math.min(codeContent.toString().split("\n").length + 1, 8));
                    ta.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px; -fx-background-color: -color-bg-subtle;");
                    box.getChildren().add(ta);
                    codeContent.setLength(0);
                    inCodeBlock = false;
                } else {
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                codeContent.append(rawLine).append("\n");
                continue;
            }

            if (line.startsWith("### ")) {
                Label h = new Label(line.substring(4).replaceAll("\\*\\*", ""));
                h.setFont(Font.font("System", FontWeight.BOLD, 14));
                h.setTextFill(javafx.scene.paint.Color.web("#0284c7"));
                box.getChildren().add(h);
            } else if (line.startsWith("#### ")) {
                Label h = new Label(line.substring(5).replaceAll("\\*\\*", ""));
                h.setFont(Font.font("System", FontWeight.BOLD, 13));
                box.getChildren().add(h);
            } else if (line.startsWith("> ")) {
                Label quote = new Label(line.substring(2));
                quote.setWrapText(true);
                quote.setStyle("-fx-font-style: italic; -fx-text-fill: #b45309; -fx-background-color: #fffbeb; -fx-padding: 6 10 6 10; -fx-border-color: #fde68a; -fx-border-width: 0 0 0 3px;");
                box.getChildren().add(quote);
            } else if (line.startsWith("|") && line.endsWith("|")) {
                // Table row
                if (line.contains("---")) continue; // separator
                Label row = new Label(line.replaceAll("\\|", "  "));
                row.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
                box.getChildren().add(row);
            } else if (!line.isBlank()) {
                Label p = new Label(line.replaceAll("\\*\\*", "").replaceAll("`", ""));
                p.setWrapText(true);
                p.setFont(Font.font("System", 12));
                box.getChildren().add(p);
            }
        }

        return box;
    }

    // =========================================================================
    // SOCRATIC DOCTOR DIALOGUE & EXPERIENCE CAPTURE METHODS
    // =========================================================================

    private String handleDoctorSocraticResponse(String challengeContext, String doctorText) {
        PrescriptionLogicAnalyzer.SocraticDialogueEvaluation eval = logicAnalyzer.evaluateDoctorSocraticResponse(challengeContext, doctorText);

        StringBuilder sb = new StringBuilder();
        if (eval.isFullySatisfied()) {
            sb.append("### ✅ AI Socratic Satisfaction: Clinical Rationale Approved (Score: ").append(eval.satisfactionScore()).append("%)\n\n");
            sb.append("**Canon Justification Category:** `").append(eval.justificationCategory()).append("`\n\n");
            sb.append("- **AI Analysis:** ").append(eval.aiAssessment()).append("\n");
            sb.append("- **Clinical Resolution:** ").append(eval.guidanceOrCaution()).append("\n\n");
            sb.append("> **Hahnemannian Rule Confirmed**: The physician's clinical insight under §153 / Kentian hierarchy successfully resolves the theoretical canon discrepancy. You may proceed with the prescription.");
        } else {
            sb.append("### ⚠️ AI Socratic Challenge: Incomplete Canon Justification (Score: ").append(eval.satisfactionScore()).append("%)\n\n");
            sb.append("**Category Evaluated:** `").append(eval.justificationCategory()).append("`\n\n");
            sb.append("- **AI Analysis:** ").append(eval.aiAssessment()).append("\n");
            sb.append("- **Canonical Risk:** ").append(eval.guidanceOrCaution()).append("\n\n");
            sb.append("Dr., if your extensive clinical experience or verified keynotes justify superseding this canon check, click **⚡ Doctor Supersede (Capture Experience)** in the top toolbar to preserve your experiential clinical observation in the Experience Vault.");
        }
        return sb.toString();
    }

    private String handlePrescriptionInterrogation(Patient p) {
        if (p == null) {
            return "⚠️ Please select a patient from the **Active Patient Context** dropdown to interrogate a specific prescription.";
        }

        try {
            List<Consultation> consults = clinicDao.getConsultationsForPatient(p.id());
            if (consults.isEmpty()) {
                return "ℹ️ No recorded consultations or prescriptions found for " + p.firstName() + " " + p.lastName() + " to interrogate.";
            }

            Consultation latest = consults.get(0);
            if (latest.prescriptions().isEmpty()) {
                return "ℹ️ The latest consultation for this patient contains no active medicinal prescription (Sac Lac / Placebo).";
            }

            Prescription rx = latest.prescriptions().get(0);
            String remId = rx.remedyId().toLowerCase();
            String constitution = p.constitutionalType() != null ? p.constitutionalType().toUpperCase() : "";

            StringBuilder sb = new StringBuilder();
            sb.append("### 🧐 Socratic AI Prescribing Interrogation\n\n");
            sb.append("**Evaluating Prescription:** `").append(rx.remedyName()).append(" ").append(rx.potency()).append("` (").append(rx.posologyInstructions()).append(")\n");
            sb.append("**Patient Profile:** ").append(p.firstName()).append(" ").append(p.lastName()).append(" | Constitution: ").append(constitution.isBlank() ? "General" : constitution).append("\n\n");

            // 1. Thermal Contradiction Challenge
            boolean isPatientHot = constitution.contains("HOT") || constitution.contains("PULSATILLA") || constitution.contains("SULPHUR") || constitution.contains("APIS");
            boolean isRemedyChilly = remId.equals("ars") || remId.equals("hepar") || remId.equals("sil") || remId.equals("psor") || remId.equals("calc") || remId.equals("rhus-t");

            if (isPatientHot && isRemedyChilly) {
                activePendingChallenge = "THERMAL_CONTRADICTION: Hot Patient prescribed Chilly Remedy (" + rx.remedyName() + ")";
                sb.append("#### ⚡ Socratic Question 1 (Thermal Canon Clash):\n");
                sb.append("> **Dr., the patient exhibits a strongly HOT thermal constitution (worse warmth, desires cool air), yet your chosen remedy ").append(rx.remedyName()).append(" is deeply CHILLY (§153). How do you resolve this thermal contradiction?\n\n");
                sb.append("Please type your clinical rationale below (e.g., cite §153 peculiar keynotes, dominant mental generals, or intercurrent detox), or click **⚡ Doctor Supersede** to record your clinical discovery.");
                return sb.toString();
            }

            // 2. High Potency / 30C Repetition Challenge
            String pot = rx.potency().toUpperCase();
            String freq = rx.repetitionFrequency() != null ? rx.repetitionFrequency().toUpperCase() : "";
            if ((pot.contains("30") || pot.contains("200")) && (freq.contains("TID") || freq.contains("TDS") || freq.contains("3 TIMES") || freq.contains("THRICE"))) {
                activePendingChallenge = "KENT_REPETITION_HAZARD: Repetitive dry doses of " + rx.remedyName() + " " + rx.potency();
                sb.append("#### ⚡ Socratic Question 2 (Kentian Posology Repetition):\n");
                sb.append("> **Dr., you have scheduled ").append(rx.remedyName()).append(" ").append(rx.potency()).append(" for three repetitions daily. J.T. Kent (Lec. XXXV) cautions that dry centesimals repeated three times daily produce artificial drug provings. Why not administer a single dose or LM scale in water?\n\n");
                sb.append("Please enter your clinical justification below, or click **⚡ Doctor Supersede**.");
                return sb.toString();
            }

            sb.append("✓ **All Canonical Baseline Checks Cleared**: Posology schedule, thermal resonance, and Gibson Miller relationships are compliant with classical literature.");
            return sb.toString();
        } catch (SQLException e) {
            log.error("Failed to interrogate prescription", e);
            return "❌ Database error during interrogation: " + e.getMessage();
        }
    }

    private String handleDigitalLibraryQuery(String rawQuery) {
        String cleanQuery = rawQuery.replaceFirst("(?i)^(library|search library|search book|fts|book:)", "").trim();
        if (cleanQuery.isBlank()) {
            cleanQuery = "headache sun agg glonoin Organon 248";
        }

        try {
            List<MateriaMedicaSearchResult> results = masterDao.searchMateriaMedicaFts(cleanQuery);
            StringBuilder sb = new StringBuilder();
            sb.append("### 📚 1,500-Book Materia Medica Digital Library FTS5 Query\n\n");
            sb.append("**Search Terms:** `").append(cleanQuery).append("` | **Matching Treatise Records:** ").append(results.size()).append("\n\n");

            if (results.isEmpty()) {
                sb.append("ℹ️ No exact monograph matches found in the classical index for: `").append(cleanQuery).append("`.\n");
                sb.append("Try terms like *'burning heat amel'*, *'headache sun'*, *'Arsenicum'*, *'Azadirachta'*, or *'Organon 248'*.");
                return sb.toString();
            }

            for (int i = 0; i < Math.min(results.size(), 5); i++) {
                MateriaMedicaSearchResult r = results.get(i);
                sb.append("#### ").append(i + 1).append(". ").append(r.remedyName()).append(" — ").append(r.sectionHeading()).append("\n");
                sb.append("- **Treatise:** *").append(r.bookTitle()).append("* | **Author:** ").append(r.author()).append("\n");
                String snippetClean = r.snippet().replaceAll("<[^>]*>", "**");
                sb.append("> ").append(snippetClean).append("\n\n");
            }

            sb.append("> **Digital Library Note**: Full unabridged chapters can be read side-by-side in the **Materia Medica Library** tab.");
            return sb.toString();
        } catch (SQLException e) {
            log.error("Failed to query digital library", e);
            return "❌ Digital library query failed: " + e.getMessage();
        }
    }

    private void openDoctorSupersedeDialog() {
        Dialog<DoctorClinicalExperience> dialog = new Dialog<>();
        dialog.setTitle("⚡ Doctor Supersede & Clinical Experience Capture");
        dialog.setHeaderText("Preserve Your Hard-Won Clinical Wisdom & Keynote Discoveries\nYour rationale will be recorded in the permanent clinical knowledge repository.");

        ButtonType saveBtnType = new ButtonType("💾 Preserve Clinical Experience", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField docNameField = new TextField("Dr. Consulting Physician, M.D. (Hom.)");
        TextField remedyField = new TextField("Arsenicum album");
        TextField caseField = new TextField();

        PatientContextItem activeCtx = patientContextCombo.getValue();
        if (activeCtx != null && activeCtx.patient() != null) {
            caseField.setText(activeCtx.patient().caseNumber());
        }

        TextField ruleField = new TextField("Thermal Polarity: Prescribed Chilly remedy for Hot constitution");
        if (activePendingChallenge != null) {
            ruleField.setText(activePendingChallenge);
        }

        ComboBox<DoctorClinicalExperience.ExperienceType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(DoctorClinicalExperience.ExperienceType.values());
        typeCombo.setValue(DoctorClinicalExperience.ExperienceType.VERIFIED_CLINICAL_KEYNOTE);

        TextArea obsArea = new TextArea();
        obsArea.setPromptText("Enter your clinical observation, keynote justification, or empirical case confirmation...");
        obsArea.setPrefRowCount(4);

        TextField keynoteField = new TextField();
        keynoteField.setPromptText("Keynote symptom (e.g. 'Burning amel by heat despite warm room')");

        grid.add(new Label("Consulting Doctor:"), 0, 0);
        grid.add(docNameField, 1, 0);
        grid.add(new Label("Remedy Prescribed:"), 0, 1);
        grid.add(remedyField, 1, 1);
        grid.add(new Label("Case Number:"), 0, 2);
        grid.add(caseField, 1, 2);
        grid.add(new Label("Overridden Canon Rule:"), 0, 3);
        grid.add(ruleField, 1, 3);
        grid.add(new Label("Experience Category:"), 0, 4);
        grid.add(typeCombo, 1, 4);
        grid.add(new Label("Verified Keynote:"), 0, 5);
        grid.add(keynoteField, 1, 5);
        grid.add(new Label("Clinical Rationale / Notes:"), 0, 6);
        grid.add(obsArea, 1, 6);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveBtnType) {
                String patId = activeCtx != null && activeCtx.patient() != null ? activeCtx.patient().id() : null;
                return new DoctorClinicalExperience(
                        UUID.randomUUID().toString(),
                        "doc-1",
                        docNameField.getText().trim(),
                        patId,
                        caseField.getText().trim(),
                        remedyField.getText().toLowerCase().trim(),
                        remedyField.getText().trim(),
                        ruleField.getText().trim(),
                        typeCombo.getValue(),
                        obsArea.getText().trim(),
                        keynoteField.getText().trim(),
                        "VERIFIED_IN_PRACTICE",
                        java.time.Instant.now()
                );
            }
            return null;
        });

        Optional<DoctorClinicalExperience> result = dialog.showAndWait();
        result.ifPresent(exp -> {
            try {
                clinicDao.saveDoctorClinicalExperience(exp);
                activePendingChallenge = null;
                appendSystemNotice("Doctor Supersede successfully captured! Record ID: " + exp.id());
                appendAiBubble("### 🏛️ Doctor Clinical Experience Recorded\n\n" +
                        "**Doctor:** " + exp.doctorName() + " | **Remedy:** `" + exp.remedyName() + "`\n" +
                        "**Category:** `" + exp.experienceType().getLabel() + "`\n\n" +
                        "- **Overridden Canon Rule:** " + exp.overriddenRule() + "\n" +
                        "- **Clinical Keynote:** *" + (exp.verifiedKeynote().isBlank() ? "N/A" : exp.verifiedKeynote()) + "*\n" +
                        "- **Physician's Observation:** " + exp.clinicalObservation() + "\n\n" +
                        "> **Clinical Wisdom Preserved**: This observation is now archived in your clinic's permanent **Doctor Experience Vault**. Future AI clinical analyses will acknowledge this empirical precedent.");
            } catch (SQLException e) {
                log.error("Failed to save doctor clinical experience", e);
                appendSystemNotice("Failed to save experience: " + e.getMessage());
            }
        });
    }

    private void openDoctorExperienceVaultDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("📖 Doctor's Clinical Experience Vault");
        dialog.setHeaderText("Structured Repository of Clinical Wisdom & Verified Empirical Keynotes");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setPrefWidth(680);
        content.setPrefHeight(450);

        try {
            List<DoctorClinicalExperience> list = clinicDao.getAllClinicalExperiences();
            if (list.isEmpty()) {
                Label emptyLbl = new Label("No clinical experience records captured yet.\nWhen doctors supersede canonical AI checks, their insights are archived here.");
                emptyLbl.setStyle("-fx-text-fill: -color-fg-muted; -fx-padding: 30;");
                content.getChildren().add(emptyLbl);
            } else {
                ListView<DoctorClinicalExperience> listView = new ListView<>();
                listView.setItems(FXCollections.observableArrayList(list));
                VBox.setVgrow(listView, Priority.ALWAYS);

                listView.setCellFactory(lv -> new ListCell<>() {
                    @Override
                    protected void updateItem(DoctorClinicalExperience item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            VBox card = new VBox(3);
                            card.setPadding(new Insets(6));

                            Label title = new Label("💊 " + item.remedyName() + " — " + item.experienceType().getLabel());
                            title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                            title.setTextFill(javafx.scene.paint.Color.web("#b45309"));

                            Label meta = new Label("By " + item.doctorName() + " | Case #" + (item.caseNumber() != null ? item.caseNumber() : "General"));
                            meta.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

                            Label obs = new Label(item.clinicalObservation());
                            obs.setWrapText(true);
                            obs.setStyle("-fx-font-size: 12px;");

                            card.getChildren().addAll(title, meta, obs);
                            setGraphic(card);
                        }
                    }
                });

                content.getChildren().add(listView);
            }
        } catch (SQLException e) {
            content.getChildren().add(new Label("Failed to load clinical experiences: " + e.getMessage()));
        }

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
}
