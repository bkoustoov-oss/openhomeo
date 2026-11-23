package org.openhomeo.core.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openhomeo.core.model.Consultation;
import org.openhomeo.core.model.Doctor;
import org.openhomeo.core.model.Patient;
import org.openhomeo.core.model.Prescription;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PrescriptionPdfServiceTest {

    @Test
    public void testGeneratePrescriptionPdfBothModes(@TempDir Path tempDir) throws Exception {
        PrescriptionPdfService pdfService = new PrescriptionPdfService();

        Doctor doctor = new Doctor(
                "doc-1",
                "Dr. S. K. Mukherjee",
                "WBH-14285 / CCH-2018",
                "B.H.M.S., M.D. (Homoeopathy)",
                "Senior Consulting Homeopath",
                "Hahnemannian Classical Healing Clinic",
                "12/A College Street, Kolkata, West Bengal - 700073",
                "+91 98300 12345",
                "clinic@openhomeo.org",
                "Consulting Physician",
                true,
                Instant.now()
        );

        Patient patient = new Patient(
                "pat-1",
                "CASE-2026-0042",
                "Ananya",
                "Banerjee",
                LocalDate.of(1994, 5, 14),
                "Female",
                "Software Architect",
                "+91 98765 43210",
                "ananya@example.com",
                "Salt Lake Sector V, Kolkata",
                "O+",
                "Phosphorus",
                "Tubercular",
                Instant.now(),
                Instant.now()
        );

        Consultation.SelectedRubric rub1 = new Consultation.SelectedRubric("mind_anxiety", "MIND - ANXIETY", 1, 3.0, false);
        Consultation.SelectedRubric rub2 = new Consultation.SelectedRubric("gen_cold_agg", "GENERALITIES - COLD - agg.", 1, 4.0, true);

        Prescription presc = new Prescription(
                "rx-1",
                "cons-1",
                "ars",
                "Arsenicum album",
                "200CH",
                Prescription.PotencyScale.CENTESIMAL,
                "4 pills in 1 dram vial, 1 dose stat at bedtime, followed by Sac Lac",
                "Once daily at bedtime for 14 days",
                LocalDate.now()
        );

        Consultation consultation = new Consultation(
                "cons-1",
                patient.id(),
                doctor.id(),
                Instant.now(),
                "Chronic nocturnal anxiety with burning gastralgia and dry cough",
                "Epigastrium and retrosternal region, radiating to back",
                "Burning heat like hot coals, ameliorated by sipping hot water",
                "Aggravation: 1 AM to 2 AM, cold air, solitary confinement; Amelioration: heat, warm drinks",
                "Extreme thirst for small sips of warm water, midnight restlessness",
                "Meticulous, fastidious, fear of suffocation and death, anxiety about health",
                "Chilly patient, desires warm food, aversion to fat and meat",
                "Symptoms moving from deeper vital organs towards superficial skin manifestation",
                "Classic Arsenicum picture with psoro-syphilitic diathesis",
                List.of(rub1, rub2),
                List.of(presc)
        );

        // Test 1: Auto-Fit Single Page
        File autoFitPdf = tempDir.resolve("prescription_autofit.pdf").toFile();
        pdfService.generatePrescriptionPdf(doctor, patient, consultation, true, autoFitPdf);
        assertTrue(autoFitPdf.exists());
        assertTrue(autoFitPdf.length() > 2000, "PDF should have valid content");

        // Test 2: Multi-Page Standard Clinical Format
        File standardPdf = tempDir.resolve("prescription_detailed.pdf").toFile();
        pdfService.generatePrescriptionPdf(doctor, patient, consultation, false, standardPdf);
        assertTrue(standardPdf.exists());
        assertTrue(standardPdf.length() > 2000, "Detailed PDF should have valid content");
    }
}
