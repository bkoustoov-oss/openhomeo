package org.openhomeo.core.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openhomeo.core.model.*;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PharmacyAndInventoryTest {

    private DatabaseManager dbManager;
    private ClinicDao clinicDao;

    @BeforeEach
    public void setup(@TempDir Path tempDir) throws Exception {
        dbManager = new DatabaseManager(tempDir.toString());
        dbManager.initializeClinicSchema();
        clinicDao = new ClinicDao(dbManager);
    }

    @Test
    public void testInventorySeedingAndSearch() throws Exception {
        List<MedicineInventoryItem> allItems = clinicDao.getAllInventoryItems();
        assertFalse(allItems.isEmpty(), "Inventory should have seeded hundreds of items");
        assertTrue(allItems.size() >= 50, "Should have extensive inventory items, got: " + allItems.size());

        // Test Search by Remedy
        List<MedicineInventoryItem> arnicaItems = clinicDao.searchInventory("arn", "ALL FORMS", "ALL POTENCIES", "ALL COMPANIES");
        assertFalse(arnicaItems.isEmpty(), "Should find Arnica in stock");

        // Test Filter by Indian Manufacturer
        List<MedicineInventoryItem> sblItems = clinicDao.searchInventory("", "ALL FORMS", "ALL POTENCIES", "SBL Pvt. Ltd.");
        assertFalse(sblItems.isEmpty(), "Should find SBL items in stock");

        // Test Filter by Form
        List<MedicineInventoryItem> mtItems = clinicDao.searchInventory("", "Mother Tincture", "ALL POTENCIES", "ALL COMPANIES");
        assertFalse(mtItems.isEmpty(), "Should find Mother Tinctures in stock");
    }

    @Test
    public void testInventoryBatchInsertionAndStockAdjustment() throws Exception {
        String testId = UUID.randomUUID().toString();
        MedicineInventoryItem newItem = new MedicineInventoryItem(
                testId, "test-rem", "Test Remedy Extract", "SBL Pvt. Ltd.",
                "Liquid Dilution 30ml", "200C", "BATCH-999", LocalDate.now().plusYears(3),
                15, 5, 110.0, "Rack Test-1", Instant.now()
        );
        clinicDao.saveInventoryItem(newItem);

        // Adjust stock (+10)
        clinicDao.adjustStock(testId, 10);
        List<MedicineInventoryItem> found = clinicDao.searchInventory("Test Remedy", "ALL FORMS", "ALL POTENCIES", "ALL COMPANIES");
        assertEquals(1, found.size());
        assertEquals(25, found.get(0).currentStockQty());

        // Adjust stock (-20)
        clinicDao.adjustStock(testId, -20);
        found = clinicDao.searchInventory("Test Remedy", "ALL FORMS", "ALL POTENCIES", "ALL COMPANIES");
        assertEquals(5, found.get(0).currentStockQty());
        assertTrue(found.get(0).isLowStock());
    }

    @Test
    public void testPharmacyOrderAndInternalSubstitutionWorkflow() throws Exception {
        String orderId = UUID.randomUUID().toString();
        PharmacyOrderItem item = new PharmacyOrderItem(
                UUID.randomUUID().toString(), orderId, "arn",
                "Arnica montana", "200CH", "Liquid Dilution 30ml",
                "4 drops TDS", null, null, null, "Dr. Reckeweg", 1, 310.0, false
        );

        PharmacyOrder order = new PharmacyOrder(
                orderId, "ORD-TEST-001", "CONS-1", "PAT-1", "John Doe",
                "DOC-1", "Dr. S. K. Mukherjee",
                PharmacyOrder.OrderStatus.ORDERED, null, null, null,
                Instant.now(), null, List.of(item)
        );

        clinicDao.createPharmacyOrder(order);

        Optional<PharmacyOrder> fetched = clinicDao.getPharmacyOrderById(orderId);
        assertTrue(fetched.isPresent());
        assertEquals(PharmacyOrder.OrderStatus.ORDERED, fetched.get().status());

        // Pharmacist proposes substitution
        clinicDao.updatePharmacyOrderStatus(
                orderId, PharmacyOrder.OrderStatus.CONSULTATION_REQUESTED,
                "Reckeweg out of stock. Proposing SBL 200CH medicated globules.",
                null, "A. K. Sharma (Pharmacist)"
        );

        fetched = clinicDao.getPharmacyOrderById(orderId);
        assertEquals(PharmacyOrder.OrderStatus.CONSULTATION_REQUESTED, fetched.get().status());
        assertTrue(fetched.get().substitutionNotes().contains("Proposing SBL"));

        // Doctor accepts substitution
        clinicDao.updatePharmacyOrderStatus(
                orderId, PharmacyOrder.OrderStatus.AGREED_SUBSTITUTION,
                fetched.get().substitutionNotes() + " [Approved by Dr. Mukherjee]",
                "Dr. S. K. Mukherjee", "A. K. Sharma"
        );

        fetched = clinicDao.getPharmacyOrderById(orderId);
        assertEquals(PharmacyOrder.OrderStatus.AGREED_SUBSTITUTION, fetched.get().status());
        assertNotNull(fetched.get().doctorAgreedBy());
    }

    @Test
    public void testStockReorderApprovalWorkflow() throws Exception {
        String reqId = UUID.randomUUID().toString();
        StockReorderRequest req = new StockReorderRequest(
                reqId, "REO-TEST-001", "pharmacist_user",
                StockReorderRequest.ReorderStatus.PENDING_APPROVAL,
                null, null, 12500.0, "Bulk Mother Tinctures requisition",
                "[]", Instant.now()
        );
        clinicDao.createReorderRequest(req);

        List<StockReorderRequest> reqs = clinicDao.getAllReorderRequests();
        assertFalse(reqs.isEmpty());

        // Doctor approves
        clinicDao.updateReorderStatus(reqId, StockReorderRequest.ReorderStatus.APPROVED, "Dr. S. K. Mukherjee");
        StockReorderRequest updated = clinicDao.getAllReorderRequests().stream()
                .filter(r -> r.id().equals(reqId))
                .findFirst().orElseThrow();
        assertEquals(StockReorderRequest.ReorderStatus.APPROVED, updated.status());
        assertEquals("Dr. S. K. Mukherjee", updated.approvedByUser());
    }

    @Test
    public void testPharmacyBillingAndGst() throws Exception {
        PharmacyBill bill = new PharmacyBill(
                UUID.randomUUID().toString(), "INV-TEST-001", "ORD-1",
                "PAT-1", "Anita Roy", "Dr. S. K. Mukherjee",
                Instant.now(), 500.0, 50.0, 22.50, 472.50,
                "UPI / GPay / PhonePe", "PAID", "Arnica 200C, Syzygium Q"
        );
        clinicDao.createPharmacyBill(bill);

        List<PharmacyBill> bills = clinicDao.getAllPharmacyBills();
        assertFalse(bills.isEmpty());
        PharmacyBill found = bills.stream().filter(b -> b.billNumber().equals("INV-TEST-001")).findFirst().orElseThrow();
        assertEquals(472.50, found.totalInr());
        assertEquals("UPI / GPay / PhonePe", found.paymentMode());
    }

    @Test
    public void testPastMedicationAndRemedySupersedeAudit() throws Exception {
        // 0. Create patient
        Patient pat = clinicDao.createPatient(
                "CASE-TEST-PM", "Test", "Patient",
                LocalDate.of(1990, 1, 1), "Female", "Teacher",
                "+91 98765 43210", "test@openhomeo.org", "Kolkata",
                "B+", "Phosphoric", "PSORA"
        );

        // 1. Patient Past Medication
        PastMedication pm = new PastMedication(
                UUID.randomUUID().toString(), "CONS-1", pat.id(),
                "Prednisolone 10mg", "Allopathic / Steroid", "6 months", "10mg daily",
                "Suppressed eczema leading to asthma", "Nux vomica, Sulphur", Instant.now()
        );
        clinicDao.savePastMedication(pm);

        List<PastMedication> pastMeds = clinicDao.getPastMedicationsByPatient(pat.id());
        assertEquals(1, pastMeds.size());
        assertEquals("Prednisolone 10mg", pastMeds.get(0).drugName());

        // 2. Doctor Supersede Audit
        RemedySupersedeAudit audit = new RemedySupersedeAudit(
                UUID.randomUUID().toString(), "CONS-1", pat.id(), "DOC-1",
                "bry", "Bryonia alba", "rhus-t", "Rhus toxicodendron",
                "Restless pacing at midnight superseded motion aggravation",
                "Case exhibited classic keynote restlessness", "Keynote Dominance", Instant.now()
        );
        clinicDao.saveSupersedeAudit(audit);

        List<RemedySupersedeAudit> audits = clinicDao.getAllSupersedeAudits();
        assertFalse(audits.isEmpty());
        RemedySupersedeAudit foundAudit = audits.get(0);
        assertEquals("bry", foundAudit.suggestedRemedyId());
        assertEquals("rhus-t", foundAudit.prescribedRemedyId());
        assertEquals("Keynote Dominance", foundAudit.caseStudyTag());
    }
}
