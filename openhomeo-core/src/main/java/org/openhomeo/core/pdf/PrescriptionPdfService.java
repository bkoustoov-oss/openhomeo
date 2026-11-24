package org.openhomeo.core.pdf;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import org.openhomeo.core.model.ClinicInstitutionProfile;
import org.openhomeo.core.model.Consultation;
import org.openhomeo.core.model.Doctor;
import org.openhomeo.core.model.PastMedication;
import org.openhomeo.core.model.Patient;
import org.openhomeo.core.model.Prescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Clinical PDF Generation Service for Homeopathic Prescriptions and Patient Case History.
 * Supports structured clinical layout with single-page Auto-Fit option,
 * dynamic clinic institution profile letterheads, and attending doctor credentials.
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
        generatePrescriptionPdf(doctor, null, patient, consultation, Collections.emptyList(), autoFitSinglePage, outputFile);
    }

    public void generatePrescriptionPdf(Doctor doctor,
                                        Patient patient,
                                        Consultation consultation,
                                        List<PastMedication> pastMedications,
                                        boolean autoFitSinglePage,
                                        File outputFile) throws Exception {
        generatePrescriptionPdf(doctor, null, patient, consultation, pastMedications, autoFitSinglePage, outputFile);
    }

    public void generatePrescriptionPdf(Doctor doctor,
                                        ClinicInstitutionProfile institution,
                                        Patient patient,
                                        Consultation consultation,
                                        List<PastMedication> pastMedications,
                                        boolean autoFitSinglePage,
                                        File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            generatePrescriptionPdf(doctor, institution, patient, consultation, pastMedications, autoFitSinglePage, fos);
        }
    }

    public void generatePrescriptionPdf(Doctor doctor,
                                        Patient patient,
                                        Consultation consultation,
                                        boolean autoFitSinglePage,
                                        OutputStream outputStream) throws Exception {
        generatePrescriptionPdf(doctor, null, patient, consultation, Collections.emptyList(), autoFitSinglePage, outputStream);
    }

    public void generatePrescriptionPdf(Doctor doctor,
                                        Patient patient,
                                        Consultation consultation,
                                        List<PastMedication> pastMedications,
                                        boolean autoFitSinglePage,
                                        OutputStream outputStream) throws Exception {
        generatePrescriptionPdf(doctor, null, patient, consultation, pastMedications, autoFitSinglePage, outputStream);
    }

    public void generatePrescriptionPdf(Doctor doctor,
                                        ClinicInstitutionProfile institution,
                                        Patient patient,
                                        Consultation consultation,
                                        List<PastMedication> pastMedications,
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

        String clinicName = (institution != null && institution.institutionName() != null && !institution.institutionName().isBlank())
                ? institution.institutionName()
                : ((doctor != null && doctor.clinicName() != null && !doctor.clinicName().isBlank())
                ? doctor.clinicName() : "HAHNEMANNIAN CLASSICAL HOMEOPATHIC CLINIC");
        leftHeader.addElement(new Paragraph(clinicName.toUpperCase(), fontClinicTitle));

        if (institution != null && institution.tagline() != null && !institution.tagline().isBlank()) {
            leftHeader.addElement(new Paragraph(institution.tagline(), fontDocMeta));
        }

        String docName = (doctor != null && doctor.fullName() != null && !doctor.fullName().isBlank())
                ? doctor.fullName()
                : ((institution != null && institution.chiefConsultantName() != null && !institution.chiefConsultantName().isBlank())
                ? institution.chiefConsultantName() : "Dr. Consulting Physician");
        String docQual = (doctor != null && doctor.qualification() != null && !doctor.qualification().isBlank())
                ? doctor.qualification()
                : ((institution != null && institution.chiefConsultantQualifications() != null) ? institution.chiefConsultantQualifications() : "");
        if (!docQual.isBlank()) {
            docName += ", " + docQual;
        }
        leftHeader.addElement(new Paragraph(docName, fontDocName));

        String desig = (doctor != null && doctor.designation() != null && !doctor.designation().isBlank())
                ? doctor.designation() : "Consulting Homeopathic Physician";
        leftHeader.addElement(new Paragraph(desig, fontDocMeta));

        String regNo = (doctor != null && doctor.registrationNo() != null && !doctor.registrationNo().isBlank())
                ? doctor.registrationNo()
                : ((institution != null && institution.chiefConsultantRegNo() != null) ? institution.chiefConsultantRegNo() : "");
        if (!regNo.isBlank()) {
            leftHeader.addElement(new Paragraph("Reg. No: " + regNo, fontDocMeta));
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

        String addr = (institution != null && institution.address() != null && !institution.address().isBlank())
                ? institution.address() : (doctor != null ? doctor.address() : "");
        if (addr != null && !addr.isBlank()) {
            Paragraph pAddr = new Paragraph(addr, fontDocMeta);
            pAddr.setAlignment(Element.ALIGN_RIGHT);
            rightHeader.addElement(pAddr);
        }
        String phone = (institution != null && institution.phone() != null && !institution.phone().isBlank())
                ? institution.phone() : (doctor != null ? doctor.phone() : "");
        if (phone != null && !phone.isBlank()) {
            Paragraph pPhone = new Paragraph("Tel: " + phone, fontDocMeta);
            pPhone.setAlignment(Element.ALIGN_RIGHT);
            rightHeader.addElement(pPhone);
        }
        String email = (institution != null && institution.email() != null && !institution.email().isBlank())
                ? institution.email() : (doctor != null ? doctor.email() : "");
        if (email != null && !email.isBlank()) {
            Paragraph pEmail = new Paragraph("Email: " + email, fontDocMeta);
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

        String patName = patient != null ? (patient.firstName() + " " + patient.lastName()).trim() : "Unknown Patient";
        int age = 0;
        if (patient != null && patient.dob() != null) {
            age = Period.between(patient.dob(), LocalDate.now()).getYears();
        }
        String gender = patient != null && patient.gender() != null ? patient.gender() : "—";
        String caseNo = patient != null && patient.caseNumber() != null ? patient.caseNumber() : "—";
        String contact = patient != null && patient.phone() != null ? patient.phone() : "—";

        addInfoCell(patientTable, "Patient Name:", patName, fontLabel, fontValue);
        addInfoCell(patientTable, "Case No:", caseNo, fontLabel, fontValue);
        addInfoCell(patientTable, "Age / Gender:", age + " yrs / " + gender, fontLabel, fontValue);
        addInfoCell(patientTable, "Phone:", contact, fontLabel, fontValue);

        document.add(patientTable);

        // 3. Clinical Presentation / Diagnosis Section
        if (consultation != null && consultation.chiefComplaint() != null && !consultation.chiefComplaint().isBlank()) {
            PdfPTable diagTable = new PdfPTable(1);
            diagTable.setWidthPercentage(100);
            diagTable.setSpacingAfter(autoFitSinglePage ? 6f : 10f);

            PdfPCell diagCell = new PdfPCell();
            diagCell.setBackgroundColor(BG_LIGHT);
            diagCell.setBorderColor(BORDER_COLOR);
            diagCell.setPadding(6f);

            Paragraph pDiag = new Paragraph();
            pDiag.add(new Chunk("Clinical Presentation / Diagnosis: ", fontLabel));
            pDiag.add(new Chunk(consultation.chiefComplaint(), fontValue));
            diagCell.addElement(pDiag);

            diagTable.addCell(diagCell);
            document.add(diagTable);
        }

        // 3b. Past Medications & Suppression Warnings (if any)
        if (pastMedications != null && !pastMedications.isEmpty()) {
            PdfPTable pastMedTable = new PdfPTable(1);
            pastMedTable.setWidthPercentage(100);
            pastMedTable.setSpacingAfter(autoFitSinglePage ? 6f : 10f);

            PdfPCell pastMedCell = new PdfPCell();
            pastMedCell.setBackgroundColor(new Color(254, 242, 242)); // Red-50
            pastMedCell.setBorderColor(new Color(254, 202, 202));     // Red-200
            pastMedCell.setPadding(6f);

            Paragraph pHeader = new Paragraph("Recorded Drug Suppressions & Allopathic History:", fontLabel);
            pastMedCell.addElement(pHeader);

            StringBuilder sb = new StringBuilder();
            for (PastMedication pm : pastMedications) {
                sb.append("• ").append(pm.drugName());
                if (pm.category() != null && !pm.category().isBlank()) {
                    sb.append(" (").append(pm.category()).append(")");
                }
                if (pm.suppressedSymptoms() != null && !pm.suppressedSymptoms().isBlank()) {
                    sb.append(" — Suppression noted: ").append(pm.suppressedSymptoms());
                }
                sb.append("\n");
            }
            Paragraph pList = new Paragraph(sb.toString().trim(), fontRemedyMeta);
            pastMedCell.addElement(pList);

            pastMedTable.addCell(pastMedCell);
            document.add(pastMedTable);
        }

        // 4. Rx Symbol & Prescriptions Table
        PdfPTable rxTable = new PdfPTable(1);
        rxTable.setWidthPercentage(100);
        rxTable.setSpacingAfter(autoFitSinglePage ? 10f : 14f);

        PdfPCell rxCell = new PdfPCell();
        rxCell.setBackgroundColor(RX_BG);
        rxCell.setBorderColor(PRIMARY_COLOR);
        rxCell.setBorderWidth(1.2f);
        rxCell.setPadding(autoFitSinglePage ? 8f : 12f);

        Paragraph pRx = new Paragraph("℞", fontRxSymbol);
        pRx.setSpacingAfter(4f);
        rxCell.addElement(pRx);

        if (consultation != null && consultation.prescriptions() != null && !consultation.prescriptions().isEmpty()) {
            int itemNo = 1;
            for (Prescription p : consultation.prescriptions()) {
                String remedyHeader = String.format("%d. %s  —  Potency: %s (%s)",
                        itemNo++, p.remedyName(), p.potency(), p.scale());
                Paragraph pRemedy = new Paragraph(remedyHeader, fontRemedyName);
                rxCell.addElement(pRemedy);

                String posology = "   Posology: " + (p.posologyInstructions() != null ? p.posologyInstructions() : "As directed");
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
        String regimenStr = (institution != null && institution.defaultRegimenInstructions() != null && !institution.defaultRegimenInstructions().isBlank())
                ? institution.defaultRegimenInstructions()
                : "Regimen: Avoid strong coffee, raw onion, garlic, menthol, camphor. Store medicine away from strong odors and direct sunlight.";
        Paragraph pDiet = new Paragraph(regimenStr, fontMuted);
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

        String signDoc = (doctor != null && doctor.fullName() != null && !doctor.fullName().isBlank())
                ? doctor.fullName()
                : ((institution != null && institution.chiefConsultantName() != null && !institution.chiefConsultantName().isBlank())
                ? institution.chiefConsultantName() : "Attending Physician");
        String signQual = (doctor != null && doctor.qualification() != null && !doctor.qualification().isBlank())
                ? doctor.qualification()
                : ((institution != null && institution.chiefConsultantQualifications() != null) ? institution.chiefConsultantQualifications() : "");
        if (!signQual.isBlank()) {
            signDoc += ", " + signQual;
        }
        Paragraph pDocSign = new Paragraph(signDoc, fontDocName);
        pDocSign.setAlignment(Element.ALIGN_RIGHT);
        rightFoot.addElement(pDocSign);

        String sigReg = (doctor != null && doctor.registrationNo() != null && !doctor.registrationNo().isBlank())
                ? doctor.registrationNo()
                : ((institution != null && institution.chiefConsultantRegNo() != null) ? institution.chiefConsultantRegNo() : "");
        if (!sigReg.isBlank()) {
            Paragraph pReg = new Paragraph("Reg. No: " + sigReg, fontDocMeta);
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
        cell.setPadding(5f);

        Paragraph p = new Paragraph();
        p.add(new Chunk(label + " ", fontLabel));
        p.add(new Chunk(value != null ? value : "—", fontValue));
        cell.addElement(p);

        table.addCell(cell);
    }
}
