package org.openhomeo.core.pdf;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import org.openhomeo.core.model.Consultation;
import org.openhomeo.core.model.Doctor;
import org.openhomeo.core.model.Patient;
import org.openhomeo.core.model.Prescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Clinical PDF Generation Service for Homeopathic Prescriptions and Patient Case History.
 * Supports structured clinical layout with single-page Auto-Fit option.
 */
public class PrescriptionPdfService {
    private static final Logger log = LoggerFactory.getLogger(PrescriptionPdfService.class);

    private static final Color PRIMARY_COLOR = new Color(2, 132, 199);    // #0284c7 Medical Cyan
    private static final Color DARK_TEXT = new Color(15, 23, 42);         // #0f172a Slate 900
    private static final Color MUTED_TEXT = new Color(100, 116, 139);     // #64748b Slate 500
    private static final Color BG_LIGHT = new Color(248, 250, 252);       // #f8fafc Slate 50
    private static final Color BORDER_COLOR = new Color(226, 232, 240);   // #e2e8f0 Slate 200
    private static final Color RX_BG = new Color(240, 249, 255);          // #f0f9ff Sky 50

    public void generatePrescriptionPdf(Doctor doctor,
                                        Patient patient,
                                        Consultation consultation,
                                        boolean autoFitSinglePage,
                                        File outputFile) throws Exception {
        generatePrescriptionPdf(doctor, patient, consultation, java.util.Collections.emptyList(), autoFitSinglePage, outputFile);
    }

    public void generatePrescriptionPdf(Doctor doctor,
                                        Patient patient,
                                        Consultation consultation,
                                        java.util.List<org.openhomeo.core.model.PastMedication> pastMedications,
                                        boolean autoFitSinglePage,
                                        File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            generatePrescriptionPdf(doctor, patient, consultation, pastMedications, autoFitSinglePage, fos);
        }
    }

    public void generatePrescriptionPdf(Doctor doctor,
                                        Patient patient,
                                        Consultation consultation,
                                        boolean autoFitSinglePage,
                                        OutputStream outputStream) throws Exception {
        generatePrescriptionPdf(doctor, patient, consultation, java.util.Collections.emptyList(), autoFitSinglePage, outputStream);
    }

    public void generatePrescriptionPdf(Doctor doctor,
                                        Patient patient,
                                        Consultation consultation,
                                        java.util.List<org.openhomeo.core.model.PastMedication> pastMedications,
                                        boolean autoFitSinglePage,
                                        OutputStream outputStream) throws Exception {

        float margin = autoFitSinglePage ? 24f : 36f;
        Document document = new Document(PageSize.A4, margin, margin, margin, margin);
        PdfWriter writer = PdfWriter.getInstance(document, outputStream);

        document.open();

        // Font scaling based on autofit
        float titleSize = autoFitSinglePage ? 14f : 17f;
        float headerSize = autoFitSinglePage ? 9.5f : 11f;
        float bodySize = autoFitSinglePage ? 8f : 9.5f;
        float smallSize = autoFitSinglePage ? 7f : 8f;
        float rxSize = autoFitSinglePage ? 10.5f : 12f;

        Font fontClinicTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, titleSize, PRIMARY_COLOR);
        Font fontDocName = FontFactory.getFont(FontFactory.HELVETICA_BOLD, headerSize, DARK_TEXT);
        Font fontDocMeta = FontFactory.getFont(FontFactory.HELVETICA, smallSize, MUTED_TEXT);
        Font fontSectionHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, headerSize, PRIMARY_COLOR);
        Font fontLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, bodySize, DARK_TEXT);
        Font fontValue = FontFactory.getFont(FontFactory.HELVETICA, bodySize, DARK_TEXT);
        Font fontMuted = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, smallSize, MUTED_TEXT);
        Font fontRxSymbol = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f, PRIMARY_COLOR);
        Font fontRemedyName = FontFactory.getFont(FontFactory.HELVETICA_BOLD, rxSize, DARK_TEXT);
        Font fontRemedyMeta = FontFactory.getFont(FontFactory.HELVETICA, bodySize, DARK_TEXT);

        // 1. Clinic & Doctor Header Table
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{65f, 35f});
        headerTable.setSpacingAfter(autoFitSinglePage ? 6f : 12f);

        // Left Header: Clinic Name & Doctor Credentials
        PdfPCell leftHeader = new PdfPCell();
        leftHeader.setBorder(Rectangle.NO_BORDER);

        String clinicName = (doctor != null && doctor.clinicName() != null && !doctor.clinicName().isBlank())
                ? doctor.clinicName() : "OPENHOMEO CLINICAL CENTRE";
        leftHeader.addElement(new Paragraph(clinicName.toUpperCase(), fontClinicTitle));

        String docName = (doctor != null && doctor.fullName() != null)
                ? doctor.fullName() : "Dr. Practitioner";
        if (doctor != null && doctor.qualification() != null && !doctor.qualification().isBlank()) {
            docName += ", " + doctor.qualification();
        }
        leftHeader.addElement(new Paragraph(docName, fontDocName));

        if (doctor != null && doctor.designation() != null && !doctor.designation().isBlank()) {
            leftHeader.addElement(new Paragraph(doctor.designation(), fontDocMeta));
        }
        if (doctor != null && doctor.registrationNo() != null && !doctor.registrationNo().isBlank()) {
            leftHeader.addElement(new Paragraph("Reg. No: " + doctor.registrationNo(), fontDocMeta));
        }
        headerTable.addCell(leftHeader);

        // Right Header: Clinic Contact & Date
        PdfPCell rightHeader = new PdfPCell();
        rightHeader.setBorder(Rectangle.NO_BORDER);
        rightHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        String consultDateStr = consultation != null && consultation.consultDate() != null
                ? dtf.format(LocalDate.ofInstant(consultation.consultDate(), ZoneId.systemDefault()))
                : dtf.format(LocalDate.now());

        Paragraph pDate = new Paragraph("Date: " + consultDateStr, fontLabel);
        pDate.setAlignment(Element.ALIGN_RIGHT);
        rightHeader.addElement(pDate);

        if (doctor != null && doctor.address() != null && !doctor.address().isBlank()) {
            Paragraph pAddr = new Paragraph(doctor.address(), fontDocMeta);
            pAddr.setAlignment(Element.ALIGN_RIGHT);
            rightHeader.addElement(pAddr);
        }
        if (doctor != null && doctor.phone() != null && !doctor.phone().isBlank()) {
            Paragraph pPhone = new Paragraph("Tel: " + doctor.phone(), fontDocMeta);
            pPhone.setAlignment(Element.ALIGN_RIGHT);
            rightHeader.addElement(pPhone);
        }
        if (doctor != null && doctor.email() != null && !doctor.email().isBlank()) {
            Paragraph pEmail = new Paragraph("Email: " + doctor.email(), fontDocMeta);
            pEmail.setAlignment(Element.ALIGN_RIGHT);
            rightHeader.addElement(pEmail);
        }
        headerTable.addCell(rightHeader);
        document.add(headerTable);

        // Header Accent Divider Line
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        PdfPCell divCell = new PdfPCell();
        divCell.setBackgroundColor(PRIMARY_COLOR);
        divCell.setFixedHeight(2f);
        divCell.setBorder(Rectangle.NO_BORDER);
        divider.addCell(divCell);
        divider.setSpacingAfter(autoFitSinglePage ? 6f : 10f);
        document.add(divider);

        // 2. Patient Demographics Strip
        PdfPTable patientTable = new PdfPTable(4);
        patientTable.setWidthPercentage(100);
        patientTable.setWidths(new float[]{30f, 25f, 25f, 20f});
        patientTable.setSpacingAfter(autoFitSinglePage ? 8f : 12f);

        String patName = patient != null ? (patient.firstName() + " " + patient.lastName()).trim() : "Walk-in Patient";
        String caseNo = patient != null ? patient.caseNumber() : "CASE-000";
        String ageGender = (patient != null && patient.gender() != null ? patient.gender() : "-");
        if (patient != null && patient.dob() != null) {
            int age = java.time.Period.between(patient.dob(), LocalDate.now()).getYears();
            ageGender = age + " Yrs / " + ageGender;
        }
        String phone = patient != null && patient.phone() != null ? patient.phone() : "-";

        addInfoCell(patientTable, "Patient Name:", patName, fontLabel, fontValue);
        addInfoCell(patientTable, "Case No:", caseNo, fontLabel, fontValue);
        addInfoCell(patientTable, "Age / Gender:", ageGender, fontLabel, fontValue);
        addInfoCell(patientTable, "Phone:", phone, fontLabel, fontValue);

        if (patient != null && patient.constitutionalType() != null && !patient.constitutionalType().isBlank()) {
            addInfoCell(patientTable, "Constitution:", patient.constitutionalType(), fontLabel, fontValue);
            addInfoCell(patientTable, "Miasm:", patient.miasmaticBackground() != null ? patient.miasmaticBackground() : "PSORA", fontLabel, fontValue);
        }

        document.add(patientTable);

        // 3. Clinical Case-Taking & Symptoms Summary
        if (consultation != null) {
            Paragraph pClinicalHead = new Paragraph("CLINICAL EVALUATION & CASE ANALYSIS (L-S-M-C)", fontSectionHeader);
            pClinicalHead.setSpacingAfter(autoFitSinglePage ? 3f : 6f);
            document.add(pClinicalHead);

            PdfPTable caseTable = new PdfPTable(2);
            caseTable.setWidthPercentage(100);
            caseTable.setWidths(new float[]{50f, 50f});
            caseTable.setSpacingAfter(autoFitSinglePage ? 8f : 12f);

            // Chief Complaint
            String cc = consultation.chiefComplaint() != null && !consultation.chiefComplaint().isBlank()
                    ? consultation.chiefComplaint() : "Not recorded";
            addFullWidthRow(caseTable, "Chief Complaint:", cc, fontLabel, fontValue);

            // Location & Sensation
            String loc = consultation.locationNotes() != null && !consultation.locationNotes().isBlank() ? consultation.locationNotes() : "-";
            String sens = consultation.sensationNotes() != null && !consultation.sensationNotes().isBlank() ? consultation.sensationNotes() : "-";
            addInfoCell(caseTable, "Location (L):", loc, fontLabel, fontValue);
            addInfoCell(caseTable, "Sensation (S):", sens, fontLabel, fontValue);

            // Modalities & Concomitants
            String mod = consultation.modalityNotes() != null && !consultation.modalityNotes().isBlank() ? consultation.modalityNotes() : "-";
            String conc = consultation.concomitantNotes() != null && !consultation.concomitantNotes().isBlank() ? consultation.concomitantNotes() : "-";
            addInfoCell(caseTable, "Modalities (< / >):", mod, fontLabel, fontValue);
            addInfoCell(caseTable, "Concomitants (C):", conc, fontLabel, fontValue);

            // Mind & Physical Generals
            String mind = consultation.mindDisposition() != null && !consultation.mindDisposition().isBlank() ? consultation.mindDisposition() : "-";
            String phys = consultation.physicalGenerals() != null && !consultation.physicalGenerals().isBlank() ? consultation.physicalGenerals() : "-";
            addInfoCell(caseTable, "Mind & Dispositions:", mind, fontLabel, fontValue);
            addInfoCell(caseTable, "Physical Generals:", phys, fontLabel, fontValue);

            // Earlier Medications & Suppressions if present
            if (pastMedications != null && !pastMedications.isEmpty()) {
                StringBuilder pastMedStr = new StringBuilder();
                for (org.openhomeo.core.model.PastMedication pm : pastMedications) {
                    if (!pastMedStr.isEmpty()) pastMedStr.append(" | ");
                    pastMedStr.append(pm.drugName()).append(" (").append(pm.category()).append(", ").append(pm.duration()).append(")");
                    if (pm.suppressedSymptoms() != null && !pm.suppressedSymptoms().isBlank()) {
                        pastMedStr.append(" -> Suppressed: ").append(pm.suppressedSymptoms());
                    }
                }
                addFullWidthRow(caseTable, "Earlier Medications & Suppressions:", pastMedStr.toString(), fontLabel, fontValue);
            }

            // Analyzed Rubrics if present
            if (consultation.selectedRubrics() != null && !consultation.selectedRubrics().isEmpty()) {
                StringBuilder rubricsStr = new StringBuilder();
                for (Consultation.SelectedRubric sr : consultation.selectedRubrics()) {
                    if (!rubricsStr.isEmpty()) rubricsStr.append("; ");
                    rubricsStr.append(sr.fullPath());
                    if (sr.isEliminative()) rubricsStr.append(" [ELIMINATIVE]");
                }
                addFullWidthRow(caseTable, "Analyzed Rubrics:", rubricsStr.toString(), fontLabel, fontValue);
            }

            document.add(caseTable);
        }

        // 4. Rx Prescription Box
        Paragraph pRxHead = new Paragraph("PRESCRIPTION (℞)", fontSectionHeader);
        pRxHead.setSpacingAfter(autoFitSinglePage ? 4f : 8f);
        document.add(pRxHead);

        PdfPTable rxTable = new PdfPTable(1);
        rxTable.setWidthPercentage(100);
        rxTable.setSpacingAfter(autoFitSinglePage ? 8f : 14f);

        PdfPCell rxCell = new PdfPCell();
        rxCell.setBackgroundColor(RX_BG);
        rxCell.setBorderColor(PRIMARY_COLOR);
        rxCell.setBorderWidth(1.2f);
        rxCell.setPadding(autoFitSinglePage ? 6f : 10f);

        Paragraph pRxSymbol = new Paragraph("℞", fontRxSymbol);
        pRxSymbol.setSpacingAfter(autoFitSinglePage ? 2f : 4f);
        rxCell.addElement(pRxSymbol);

        if (consultation != null && consultation.prescriptions() != null && !consultation.prescriptions().isEmpty()) {
            for (Prescription p : consultation.prescriptions()) {
                String line = String.format("%s  %s (%s)", p.remedyName(), p.potency(), p.scale().name());
                Paragraph pRem = new Paragraph(line, fontRemedyName);
                rxCell.addElement(pRem);

                String posology = "Directions: " + p.posologyInstructions();
                if (p.repetitionFrequency() != null && !p.repetitionFrequency().isBlank()) {
                    posology += " | Frequency: " + p.repetitionFrequency();
                }
                Paragraph pPos = new Paragraph(posology, fontRemedyMeta);
                pPos.setSpacingAfter(autoFitSinglePage ? 4f : 8f);
                rxCell.addElement(pPos);
            }
        } else {
            Paragraph pEmpty = new Paragraph("Sac. Lac. (Placebo) / Phytum — 4 pills twice daily", fontRemedyName);
            rxCell.addElement(pEmpty);
        }

        // Dietary & regimen instructions
        Paragraph pDiet = new Paragraph("Regimen: Avoid strong coffee, raw onion, garlic, menthol, camphor. Store medicine away from strong odors and direct sunlight.", fontMuted);
        rxCell.addElement(pDiet);

        rxTable.addCell(rxCell);
        document.add(rxTable);

        // 5. Follow-up and Doctor Signature Block
        PdfPTable footerTable = new PdfPTable(2);
        footerTable.setWidthPercentage(100);
        footerTable.setWidths(new float[]{55f, 45f});
        footerTable.setKeepTogether(true);

        PdfPCell leftFoot = new PdfPCell();
        leftFoot.setBorder(Rectangle.NO_BORDER);
        leftFoot.addElement(new Paragraph("Next Review / Follow-up:", fontLabel));
        leftFoot.addElement(new Paragraph("After 2 to 3 weeks or earlier in case of aggravation.", fontValue));

        if (consultation != null && consultation.heringsLawAnalysis() != null && !consultation.heringsLawAnalysis().isBlank()) {
            leftFoot.addElement(new Paragraph("Follow-up Note: " + consultation.heringsLawAnalysis(), fontMuted));
        }
        footerTable.addCell(leftFoot);

        PdfPCell rightFoot = new PdfPCell();
        rightFoot.setBorder(Rectangle.NO_BORDER);
        rightFoot.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph pSigSpace = new Paragraph("\n\n");
        rightFoot.addElement(pSigSpace);

        Paragraph pSigLine = new Paragraph("____________________________________", fontDocMeta);
        pSigLine.setAlignment(Element.ALIGN_RIGHT);
        rightFoot.addElement(pSigLine);

        String signDoc = (doctor != null && doctor.fullName() != null) ? doctor.fullName() : "Attending Physician";
        Paragraph pDocSign = new Paragraph(signDoc, fontDocName);
        pDocSign.setAlignment(Element.ALIGN_RIGHT);
        rightFoot.addElement(pDocSign);

        if (doctor != null && doctor.registrationNo() != null && !doctor.registrationNo().isBlank()) {
            Paragraph pReg = new Paragraph("Reg. No: " + doctor.registrationNo(), fontDocMeta);
            pReg.setAlignment(Element.ALIGN_RIGHT);
            rightFoot.addElement(pReg);
        }

        Paragraph pVerified = new Paragraph("Authenticated Classical Homeopathic Prescription", fontMuted);
        pVerified.setAlignment(Element.ALIGN_RIGHT);
        rightFoot.addElement(pVerified);

        footerTable.addCell(rightFoot);
        document.add(footerTable);

        document.close();
        log.info("Clinical PDF generated successfully: autoFit={}, patient={}", autoFitSinglePage, patName);
    }

    private void addInfoCell(PdfPTable table, String label, String value, Font fontLabel, Font fontValue) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BG_LIGHT);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(4f);

        Paragraph p = new Paragraph();
        p.add(new Chunk(label + " ", fontLabel));
        p.add(new Chunk(value, fontValue));
        cell.addElement(p);
        table.addCell(cell);
    }

    private void addFullWidthRow(PdfPTable table, String label, String value, Font fontLabel, Font fontValue) {
        PdfPCell cell = new PdfPCell();
        cell.setColspan(table.getNumberOfColumns());
        cell.setBackgroundColor(BG_LIGHT);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(4f);

        Paragraph p = new Paragraph();
        p.add(new Chunk(label + " ", fontLabel));
        p.add(new Chunk(value, fontValue));
        cell.addElement(p);
        table.addCell(cell);
    }
}
