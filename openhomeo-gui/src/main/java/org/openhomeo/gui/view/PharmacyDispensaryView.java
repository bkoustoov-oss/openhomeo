package org.openhomeo.gui.view;

import atlantafx.base.theme.Styles;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.openhomeo.core.db.ClinicDao;
import org.openhomeo.core.db.MasterKnowledgeDao;
import org.openhomeo.core.materia_medica.HomeopathicClinicalRhymes;
import org.openhomeo.core.model.*;
import org.openhomeo.core.security.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Professional Homeopathic Pharmacy Dispensary, Stock Register,
 * Multi-Medicine Checklist Batch Entry, Internal Doctor-Pharmacist Orders,
 * Volume Reorders, and Billing Management View (Tailored for Indian Practice).
 */
public class PharmacyDispensaryView extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(PharmacyDispensaryView.class);

    private final ClinicDao clinicDao;
    private final MasterKnowledgeDao masterDao;
    private final SecurityService securityService;

    // Observable data lists
    private final ObservableList<MedicineInventoryItem> inventoryList = FXCollections.observableArrayList();
    private final ObservableList<PharmacyOrder> ordersList = FXCollections.observableArrayList();
    private final ObservableList<StockReorderRequest> reordersList = FXCollections.observableArrayList();
    private final ObservableList<PharmacyBill> billsList = FXCollections.observableArrayList();
    private final ObservableList<MedicineConstituent> masterConstituentsList = FXCollections.observableArrayList();

    // UI Components
    private TableView<MedicineInventoryItem> inventoryTable;
    private TableView<PharmacyOrder> ordersTable;
    private TableView<StockReorderRequest> reordersTable;
    private TableView<PharmacyBill> billsTable;
    private TableView<MedicineConstituent> masterConstituentsTable;
    private Label statusLabel;

    public PharmacyDispensaryView(ClinicDao clinicDao, MasterKnowledgeDao masterDao, SecurityService securityService) {
        this.clinicDao = clinicDao;
        this.masterDao = masterDao;
        this.securityService = securityService;

        setPadding(new Insets(12));
        setTop(createTopHeader());

        TabPane subTabs = new TabPane();
        subTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab tabStock = new Tab("📦 Live Stock Register", createStockRegisterPane());
        Tab tabMasterVsStock = new Tab("📖 Master Medicine Directory & Pharmacology", createMasterVsStockPane());
        Tab tabOrders = new Tab("📋 Doctor Orders & Internal Consultation", createOrdersPane());
        Tab tabReorder = new Tab("🔄 Volume Reorder & Approvals", createReorderPane());
        Tab tabBilling = new Tab("💳 Dispensary Billing & Invoicing", createBillingPane());

        subTabs.getTabs().addAll(tabStock, tabMasterVsStock, tabOrders, tabReorder, tabBilling);
        setCenter(subTabs);

        refreshAllData();
    }

    private Node createTopHeader() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 12, 12, 12));

        Label title = new Label("PHARMACY DISPENSARY & INVENTORY WORKSTATION");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("🔄 Refresh Data");
        refreshBtn.setOnAction(e -> refreshAllData());

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add(Styles.TEXT_MUTED);

        bar.getChildren().addAll(title, spacer, statusLabel, refreshBtn);
        return bar;
    }

    // =========================================================================
    // TAB 1: LIVE STOCK REGISTER
    // =========================================================================

    private Node createStockRegisterPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(12));

        // Filter Bar
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        TextField searchField = new TextField();
        searchField.setPromptText("Search medicine name, code, or rack...");
        searchField.setPrefWidth(240);

        ComboBox<String> companyFilter = new ComboBox<>();
        companyFilter.getItems().addAll("ALL COMPANIES", "SBL Pvt. Ltd.", "Dr. Reckeweg (Germany)", "Schwabe India (WSI)",
                "Bakson", "Wheezal", "Medisynth", "Lord's", "Haslab", "In-House Dispensary");
        companyFilter.setValue("ALL COMPANIES");

        ComboBox<String> formFilter = new ComboBox<>();
        formFilter.getItems().addAll("ALL FORMS", "Liquid Dilution", "Mother Tincture Q", "Globules No. 30",
                "Globules No. 40", "Biochemic Tablets", "Bio-Combination", "Ointment/Cream");
        formFilter.setValue("ALL FORMS");

        ComboBox<String> potencyFilter = new ComboBox<>();
        potencyFilter.getItems().addAll("ALL POTENCIES", "Q", "6X", "12X", "30C", "200C", "1M", "10M", "50M", "CM", "External");
        potencyFilter.setValue("ALL POTENCIES");

        Button searchBtn = new Button("Filter Stock");
        searchBtn.getStyleClass().add(Styles.ACCENT);
        searchBtn.setOnAction(e -> doSearchInventory(searchField.getText(), formFilter.getValue(), potencyFilter.getValue(), companyFilter.getValue()));

        Button showLowStockBtn = new Button("⚠️ Low Stock Alert");
        showLowStockBtn.getStyleClass().add(Styles.WARNING);
        showLowStockBtn.setOnAction(e -> doShowLowStock());

        filterBar.getChildren().addAll(
                new Label("Search:"), searchField,
                new Label("Company:"), companyFilter,
                new Label("Form:"), formFilter,
                new Label("Potency:"), potencyFilter,
                searchBtn, showLowStockBtn
        );

        // Table
        inventoryTable = new TableView<>(inventoryList);
        inventoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<MedicineInventoryItem, String> colName = new TableColumn<>("Medicine Name");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
        colName.setPrefWidth(180);

        TableColumn<MedicineInventoryItem, String> colCompany = new TableColumn<>("Manufacturer");
        colCompany.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().companyName()));

        TableColumn<MedicineInventoryItem, String> colForm = new TableColumn<>("Dosage Form");
        colForm.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().form()));

        TableColumn<MedicineInventoryItem, String> colPotency = new TableColumn<>("Potency");
        colPotency.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().potency()));

        TableColumn<MedicineInventoryItem, String> colBatch = new TableColumn<>("Batch No.");
        colBatch.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().batchNumber()));

        TableColumn<MedicineInventoryItem, String> colStock = new TableColumn<>("Current Stock");
        colStock.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().currentStockQty() + (d.getValue().isLowStock() ? " ⚠️ LOW" : "")));

        TableColumn<MedicineInventoryItem, String> colPrice = new TableColumn<>("MRP (₹)");
        colPrice.setCellValueFactory(d -> new SimpleStringProperty("₹" + String.format("%.2f", d.getValue().unitPriceInr())));

        TableColumn<MedicineInventoryItem, String> colRack = new TableColumn<>("Rack Location");
        colRack.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().rackLocation()));

        inventoryTable.getColumns().addAll(colName, colCompany, colForm, colPotency, colBatch, colStock, colPrice, colRack);
        VBox.setVgrow(inventoryTable, Priority.ALWAYS);

        // Action Toolbar
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button addBtn = new Button("+ Add Single Stock Item");
        addBtn.getStyleClass().add(Styles.SUCCESS);
        addBtn.setOnAction(e -> showAddStockDialog());

        Button editQtyBtn = new Button("🔄 Adjust Stock (+/-)");
        editQtyBtn.setOnAction(e -> showAdjustStockDialog());

        Button deleteBtn = new Button("🗑️ Delete Selected");
        deleteBtn.getStyleClass().add(Styles.DANGER);
        deleteBtn.setOnAction(e -> deleteSelectedStockItem());

        actions.getChildren().addAll(addBtn, editQtyBtn, deleteBtn);

        vbox.getChildren().addAll(filterBar, inventoryTable, actions);
        return vbox;
    }

    private void doSearchInventory(String q, String form, String potency, String comp) {
        try {
            List<MedicineInventoryItem> items = clinicDao.searchInventory(q, form, potency, comp);
            inventoryList.setAll(items);
            statusLabel.setText("Found " + items.size() + " stock items.");
        } catch (Exception ex) {
            log.error("Failed to search inventory", ex);
        }
    }

    private void doShowLowStock() {
        try {
            List<MedicineInventoryItem> items = clinicDao.getLowStockItems();
            inventoryList.setAll(items);
            statusLabel.setText("Showing " + items.size() + " low-stock medicines.");
        } catch (Exception ex) {
            log.error("Failed to load low stock items", ex);
        }
    }

    private void showAddStockDialog() {
        Dialog<MedicineInventoryItem> dialog = new Dialog<>();
        dialog.setTitle("Add Medicine to Stock Register");
        dialog.setHeaderText("Enter product details for dispensary inventory");

        ButtonType saveBtn = new ButtonType("Save Stock", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField nameField = new TextField(); nameField.setPromptText("e.g. Arnica montana");
        TextField codeField = new TextField(); codeField.setPromptText("e.g. arn");
        ComboBox<String> compBox = new ComboBox<>();
        compBox.setEditable(true);
        compBox.getItems().addAll("SBL Pvt. Ltd.", "Dr. Reckeweg (Germany)", "Schwabe India (WSI)", "Bakson", "Wheezal", "Medisynth", "In-House Dispensary");
        compBox.setValue("SBL Pvt. Ltd.");

        ComboBox<String> formBox = new ComboBox<>();
        formBox.setEditable(true);
        formBox.getItems().addAll("Liquid Dilution 30ml", "Liquid Dilution 100ml", "Mother Tincture Q 30ml",
                "Mother Tincture Q 100ml", "Globules No. 30 (1/2 oz)", "Globules No. 40 (1 oz)",
                "Biochemic Tablets (25g)", "Ointment/Cream 25g");
        formBox.setValue("Liquid Dilution 30ml");

        ComboBox<String> potencyBox = new ComboBox<>();
        potencyBox.setEditable(true);
        potencyBox.getItems().addAll("Q", "6X", "12X", "30C", "200C", "1M", "10M", "50M", "0/1 LM", "0/3 LM");
        potencyBox.setValue("200C");

        TextField batchField = new TextField("BATCH-" + (System.currentTimeMillis() % 10000));
        Spinner<Integer> qtySpinner = new Spinner<>(1, 1000, 20);
        Spinner<Integer> reorderSpinner = new Spinner<>(1, 100, 5);
        TextField priceField = new TextField("120.00");
        TextField rackField = new TextField("Rack A-1, Shelf 2");

        grid.add(new Label("Medicine Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Remedy Code:"), 0, 1); grid.add(codeField, 1, 1);
        grid.add(new Label("Company:"), 0, 2); grid.add(compBox, 1, 2);
        grid.add(new Label("Dosage Form:"), 0, 3); grid.add(formBox, 1, 3);
        grid.add(new Label("Potency:"), 0, 4); grid.add(potencyBox, 1, 4);
        grid.add(new Label("Batch Number:"), 0, 5); grid.add(batchField, 1, 5);
        grid.add(new Label("Stock Quantity:"), 0, 6); grid.add(qtySpinner, 1, 6);
        grid.add(new Label("Reorder Threshold:"), 0, 7); grid.add(reorderSpinner, 1, 7);
        grid.add(new Label("Unit Price (₹):"), 0, 8); grid.add(priceField, 1, 8);
        grid.add(new Label("Rack Location:"), 0, 9); grid.add(rackField, 1, 9);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(b -> {
            if (b == saveBtn && !nameField.getText().isBlank()) {
                double pr = 100.0;
                try { pr = Double.parseDouble(priceField.getText().trim()); } catch (Exception ignored) {}
                return new MedicineInventoryItem(
                        UUID.randomUUID().toString(),
                        codeField.getText().trim().toLowerCase(),
                        nameField.getText().trim(),
                        compBox.getValue(),
                        formBox.getValue(),
                        potencyBox.getValue(),
                        batchField.getText().trim(),
                        LocalDate.now().plusYears(4),
                        qtySpinner.getValue(),
                        reorderSpinner.getValue(),
                        pr,
                        rackField.getText().trim(),
                        Instant.now()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(item -> {
            try {
                clinicDao.saveInventoryItem(item);
                refreshAllData();
                statusLabel.setText("Added to stock: " + item.medicineName());
            } catch (Exception ex) {
                log.error("Failed to save inventory item", ex);
            }
        });
    }

    private void showAdjustStockDialog() {
        MedicineInventoryItem sel = inventoryTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showAlert("No Selection", "Please select a medicine from the stock table first.");
            return;
        }

        TextInputDialog dlg = new TextInputDialog("10");
        dlg.setTitle("Adjust Stock Quantity");
        dlg.setHeaderText("Adjust stock for: " + sel.medicineName() + " (" + sel.potency() + ")\nCurrent Stock: " + sel.currentStockQty());
        dlg.setContentText("Enter quantity delta (+ to add, - to reduce):");

        dlg.showAndWait().ifPresent(val -> {
            try {
                int delta = Integer.parseInt(val.trim());
                clinicDao.adjustStock(sel.id(), delta);
                refreshAllData();
                statusLabel.setText("Stock adjusted by " + delta + " for " + sel.medicineName());
            } catch (Exception ex) {
                showAlert("Error", "Invalid quantity: " + ex.getMessage());
            }
        });
    }

    private void deleteSelectedStockItem() {
        MedicineInventoryItem sel = inventoryTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            clinicDao.deleteInventoryItem(sel.id());
            inventoryList.remove(sel);
            statusLabel.setText("Removed: " + sel.medicineName());
        } catch (Exception ex) {
            log.error("Failed to delete stock item", ex);
        }
    }

    // =========================================================================
    // TAB 2: DOCTOR ORDERS & INTERNAL CONSULTATIONS
    // =========================================================================

    private Node createOrdersPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(12));

        Label header = new Label("Prescription Orders & Internal Pharmacist-Doctor Consultations");
        header.setFont(Font.font("System", FontWeight.BOLD, 15));

        ordersTable = new TableView<>(ordersList);
        ordersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<PharmacyOrder, String> colNum = new TableColumn<>("Order No.");
        colNum.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().orderNumber()));
        colNum.setPrefWidth(110);

        TableColumn<PharmacyOrder, String> colPatient = new TableColumn<>("Patient");
        colPatient.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().patientName()));

        TableColumn<PharmacyOrder, String> colDoctor = new TableColumn<>("Prescribing Doctor");
        colDoctor.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().doctorName()));

        TableColumn<PharmacyOrder, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status().name()));

        TableColumn<PharmacyOrder, String> colSubNotes = new TableColumn<>("Substitution / Consultation Notes");
        colSubNotes.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().substitutionNotes() != null ? d.getValue().substitutionNotes() : "-"));
        colSubNotes.setPrefWidth(220);

        TableColumn<PharmacyOrder, String> colDate = new TableColumn<>("Order Date");
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().orderDate().toString().substring(0, 10)));

        ordersTable.getColumns().addAll(colNum, colPatient, colDoctor, colStatus, colSubNotes, colDate);
        VBox.setVgrow(ordersTable, Priority.ALWAYS);

        // Actions
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button proposeSubBtn = new Button("🤝 Propose Medicine Substitution (Internal Consultation)");
        proposeSubBtn.getStyleClass().add(Styles.WARNING);
        proposeSubBtn.setOnAction(e -> showProposeSubstitutionDialog());

        Button acceptDoctorSubBtn = new Button("✅ Doctor: Accept Substitution Agreement");
        acceptDoctorSubBtn.getStyleClass().add(Styles.ACCENT);
        acceptDoctorSubBtn.setOnAction(e -> acceptSubstitutionByDoctor());

        Button dispenseBtn = new Button("💊 Dispense Medicine & Deduct Stock");
        dispenseBtn.getStyleClass().add(Styles.SUCCESS);
        dispenseBtn.setOnAction(e -> dispenseSelectedOrder());

        actions.getChildren().addAll(proposeSubBtn, acceptDoctorSubBtn, dispenseBtn);

        vbox.getChildren().addAll(header, ordersTable, actions);
        return vbox;
    }

    private void showProposeSubstitutionDialog() {
        PharmacyOrder sel = ordersTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showAlert("No Order Selected", "Please select a pharmacy order from the table first.");
            return;
        }

        TextInputDialog dlg = new TextInputDialog("Brand/form not in stock. Proposing SBL 200CH medicated globules or Schwabe 30CH liquid.");
        dlg.setTitle("Propose Medicine Substitution");
        dlg.setHeaderText("Order: " + sel.orderNumber() + " | Patient: " + sel.patientName());
        dlg.setContentText("Enter proposed medicine, potency, and justification for doctor's approval:");

        dlg.showAndWait().ifPresent(notes -> {
            try {
                clinicDao.updatePharmacyOrderStatus(
                        sel.id(),
                        PharmacyOrder.OrderStatus.CONSULTATION_REQUESTED,
                        notes,
                        null,
                        securityService.getCurrentSession() != null ? securityService.getCurrentSession().user().fullName() : "Pharmacist"
                );
                refreshAllData();
                statusLabel.setText("Consultation requested for order " + sel.orderNumber());
            } catch (Exception ex) {
                log.error("Failed to propose substitution", ex);
            }
        });
    }

    private void acceptSubstitutionByDoctor() {
        PharmacyOrder sel = ordersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        try {
            clinicDao.updatePharmacyOrderStatus(
                    sel.id(),
                    PharmacyOrder.OrderStatus.AGREED_SUBSTITUTION,
                    sel.substitutionNotes() + " [Approved by Doctor]",
                    securityService.getCurrentSession() != null ? securityService.getCurrentSession().user().fullName() : "Doctor",
                    sel.pharmacistAgreedBy()
            );
            refreshAllData();
            statusLabel.setText("Substitution agreed between Doctor and Pharmacist for " + sel.orderNumber());
        } catch (Exception ex) {
            log.error("Failed to accept substitution", ex);
        }
    }

    private void dispenseSelectedOrder() {
        PharmacyOrder sel = ordersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        try {
            clinicDao.updatePharmacyOrderStatus(
                    sel.id(),
                    PharmacyOrder.OrderStatus.DISPENSED,
                    sel.substitutionNotes(),
                    sel.doctorAgreedBy(),
                    sel.pharmacistAgreedBy()
            );
            refreshAllData();
            showAlert("Dispensation Complete", "Order " + sel.orderNumber() + " dispensed and delivered to patient.");
            statusLabel.setText("Dispensed order: " + sel.orderNumber());
        } catch (Exception ex) {
            log.error("Failed to dispense order", ex);
        }
    }

    // =========================================================================
    // TAB 4: VOLUME REORDER & APPROVALS
    // =========================================================================

    private Node createReorderPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(12));

        Label header = new Label("Pharmacy Volume Reorder Requisitions & Approvals");
        header.setFont(Font.font("System", FontWeight.BOLD, 15));

        reordersTable = new TableView<>(reordersList);
        reordersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<StockReorderRequest, String> colNum = new TableColumn<>("Requisition No.");
        colNum.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().requestNumber()));

        TableColumn<StockReorderRequest, String> colUser = new TableColumn<>("Requested By");
        colUser.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().requestedByUser()));

        TableColumn<StockReorderRequest, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status().name()));

        TableColumn<StockReorderRequest, String> colApp = new TableColumn<>("Approved By");
        colApp.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().approvedByUser() != null ? d.getValue().approvedByUser() : "Pending"));

        TableColumn<StockReorderRequest, String> colCost = new TableColumn<>("Est. Cost (₹)");
        colCost.setCellValueFactory(d -> new SimpleStringProperty("₹" + String.format("%.2f", d.getValue().totalEstimatedInr())));

        TableColumn<StockReorderRequest, String> colNotes = new TableColumn<>("Notes / Volume Specs");
        colNotes.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().notes()));

        reordersTable.getColumns().addAll(colNum, colUser, colStatus, colApp, colCost, colNotes);
        VBox.setVgrow(reordersTable, Priority.ALWAYS);

        // Actions
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button newReorderBtn = new Button("+ Create Volume Reorder Request");
        newReorderBtn.getStyleClass().add(Styles.ACCENT);
        newReorderBtn.setOnAction(e -> showCreateReorderDialog());

        Button approveBtn = new Button("✅ Doctor/Admin: Approve Reorder");
        approveBtn.getStyleClass().add(Styles.SUCCESS);
        approveBtn.setOnAction(e -> approveSelectedReorder(StockReorderRequest.ReorderStatus.APPROVED));

        Button markReceivedBtn = new Button("📦 Consignment Arrived: Mark Received");
        markReceivedBtn.setOnAction(e -> approveSelectedReorder(StockReorderRequest.ReorderStatus.RECEIVED));

        actions.getChildren().addAll(newReorderBtn, approveBtn, markReceivedBtn);

        vbox.getChildren().addAll(header, reordersTable, actions);
        return vbox;
    }

    private void showCreateReorderDialog() {
        Dialog<StockReorderRequest> dlg = new Dialog<>();
        dlg.setTitle("Create Volume Stock Reorder Requisition");
        dlg.setHeaderText("Reorder bulk volumes from manufacturers (SBL, Reckeweg, Schwabe)");

        ButtonType submitBtn = new ButtonType("Submit for Approval", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(submitBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField reqNum = new TextField("REO-" + (System.currentTimeMillis() % 10000));
        TextField costField = new TextField("15000.00");
        TextArea notesArea = new TextArea(
                "1. SBL Syzygium jambolanum Q 450ml - 10 bottles\n" +
                "2. Dr. Reckeweg Arnica 200CH 11ml - 20 phials\n" +
                "3. Schwabe India Five Phos 6X 450g - 5 jars\n" +
                "4. Blank Cane Sugar Globules No. 30 (450g) - 15 bottles"
        );
        notesArea.setPrefRowCount(4);

        grid.add(new Label("Reorder Number:"), 0, 0); grid.add(reqNum, 1, 0);
        grid.add(new Label("Estimated Cost (₹):"), 0, 1); grid.add(costField, 1, 1);
        grid.add(new Label("Volume Item Details:"), 0, 2); grid.add(notesArea, 1, 2);

        dlg.getDialogPane().setContent(grid);

        dlg.setResultConverter(b -> {
            if (b == submitBtn) {
                double c = 0;
                try { c = Double.parseDouble(costField.getText().trim()); } catch (Exception ignored) {}
                String user = securityService.getCurrentSession() != null ? securityService.getCurrentSession().user().fullName() : "Pharmacist";
                return new StockReorderRequest(
                        UUID.randomUUID().toString(),
                        reqNum.getText().trim(),
                        user,
                        StockReorderRequest.ReorderStatus.PENDING_APPROVAL,
                        null,
                        null,
                        c,
                        notesArea.getText().trim(),
                        "[]",
                        Instant.now()
                );
            }
            return null;
        });

        dlg.showAndWait().ifPresent(req -> {
            try {
                clinicDao.createReorderRequest(req);
                refreshAllData();
                statusLabel.setText("Created reorder: " + req.requestNumber());
            } catch (Exception ex) {
                log.error("Failed to create reorder", ex);
            }
        });
    }

    private void approveSelectedReorder(StockReorderRequest.ReorderStatus newStatus) {
        StockReorderRequest sel = reordersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        try {
            String approver = securityService.getCurrentSession() != null ? securityService.getCurrentSession().user().fullName() : "Doctor / Admin";
            clinicDao.updateReorderStatus(sel.id(), newStatus, approver);
            refreshAllData();
            showAlert("Status Updated", "Reorder " + sel.requestNumber() + " status updated to: " + newStatus.name());
        } catch (Exception ex) {
            log.error("Failed to update reorder", ex);
        }
    }

    // =========================================================================
    // TAB 5: DISPENSARY BILLING & INVOICING
    // =========================================================================

    private Node createBillingPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(12));

        Label header = new Label("Dispensary Billing, GST (5%) & Payment Receipts");
        header.setFont(Font.font("System", FontWeight.BOLD, 15));

        billsTable = new TableView<>(billsList);
        billsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<PharmacyBill, String> colNum = new TableColumn<>("Bill / Invoice No.");
        colNum.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().billNumber()));

        TableColumn<PharmacyBill, String> colPatient = new TableColumn<>("Patient Name");
        colPatient.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().patientName()));

        TableColumn<PharmacyBill, String> colDoc = new TableColumn<>("Prescribing Doctor");
        colDoc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().doctorName()));

        TableColumn<PharmacyBill, String> colSub = new TableColumn<>("Subtotal (₹)");
        colSub.setCellValueFactory(d -> new SimpleStringProperty("₹" + String.format("%.2f", d.getValue().subtotalInr())));

        TableColumn<PharmacyBill, String> colGst = new TableColumn<>("GST 5% (₹)");
        colGst.setCellValueFactory(d -> new SimpleStringProperty("₹" + String.format("%.2f", d.getValue().gstAmountInr())));

        TableColumn<PharmacyBill, String> colTotal = new TableColumn<>("Grand Total (₹)");
        colTotal.setCellValueFactory(d -> new SimpleStringProperty("₹" + String.format("%.2f", d.getValue().totalInr())));

        TableColumn<PharmacyBill, String> colMode = new TableColumn<>("Payment Mode");
        colMode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().paymentMode()));

        TableColumn<PharmacyBill, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().paymentStatus()));

        billsTable.getColumns().addAll(colNum, colPatient, colDoc, colSub, colGst, colTotal, colMode, colStatus);
        VBox.setVgrow(billsTable, Priority.ALWAYS);

        // Actions
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button newBillBtn = new Button("💳 Generate New Pharmacy Bill");
        newBillBtn.getStyleClass().add(Styles.SUCCESS);
        newBillBtn.setOnAction(e -> showCreateBillDialog());

        Button viewBillBtn = new Button("📄 View / Print Selected Invoice");
        viewBillBtn.setOnAction(e -> viewSelectedBill());

        actions.getChildren().addAll(newBillBtn, viewBillBtn);

        vbox.getChildren().addAll(header, billsTable, actions);
        return vbox;
    }

    private void showCreateBillDialog() {
        Dialog<PharmacyBill> dlg = new Dialog<>();
        dlg.setTitle("Dispensary Pharmacy Billing (Indian Perspective)");
        dlg.setHeaderText("Create invoice with GST and payment gateway recording");

        ButtonType payBtn = new ButtonType("Accept Payment & Generate Bill", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(payBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField billNum = new TextField("INV-" + LocalDate.now().getYear() + "-" + (System.currentTimeMillis() % 10000));
        TextField patField = new TextField("Walk-In Patient / Case");
        TextField docField = new TextField("Dr. S. K. Mukherjee, M.D. (Hom.)");
        TextField subtotalField = new TextField("450.00");
        TextField discountField = new TextField("0.00");

        Label gstLabel = new Label("₹22.50 (2.5% CGST + 2.5% SGST)");
        Label totalLabel = new Label("₹472.50");
        totalLabel.setFont(Font.font("System", FontWeight.BOLD, 15));

        subtotalField.textProperty().addListener((o, ov, nv) -> {
            try {
                double sub = Double.parseDouble(nv.trim());
                double disc = Double.parseDouble(discountField.getText().trim());
                double taxable = Math.max(0, sub - disc);
                double gst = taxable * 0.05;
                gstLabel.setText("₹" + String.format("%.2f", gst) + " (5% GST)");
                totalLabel.setText("₹" + String.format("%.2f", taxable + gst));
            } catch (Exception ignored) {}
        });

        ComboBox<String> payMode = new ComboBox<>();
        payMode.getItems().addAll("UPI / GPay / PhonePe / Paytm", "CASH", "Credit / Debit Card", "Net Banking");
        payMode.setValue("UPI / GPay / PhonePe / Paytm");

        TextArea itemsArea = new TextArea(
                "1. Arnica montana 200CH Globules (1/2 oz) - ₹60.00\n" +
                "2. SBL Syzygium jambolanum Q 100ml - ₹260.00\n" +
                "3. Schwabe Calcarea fluor 6X (25g) - ₹95.00\n" +
                "4. Dispensing Phial & Service - ₹35.00"
        );
        itemsArea.setPrefRowCount(4);

        grid.add(new Label("Invoice Number:"), 0, 0); grid.add(billNum, 1, 0);
        grid.add(new Label("Patient Name:"), 0, 1); grid.add(patField, 1, 1);
        grid.add(new Label("Doctor Name:"), 0, 2); grid.add(docField, 1, 2);
        grid.add(new Label("Medicine Subtotal (₹):"), 0, 3); grid.add(subtotalField, 1, 3);
        grid.add(new Label("Discount (₹):"), 0, 4); grid.add(discountField, 1, 4);
        grid.add(new Label("GST (5%):"), 0, 5); grid.add(gstLabel, 1, 5);
        grid.add(new Label("Grand Total Payable:"), 0, 6); grid.add(totalLabel, 1, 6);
        grid.add(new Label("Payment Mode:"), 0, 7); grid.add(payMode, 1, 7);
        grid.add(new Label("Billed Line Items:"), 0, 8); grid.add(itemsArea, 1, 8);

        dlg.getDialogPane().setContent(grid);

        dlg.setResultConverter(b -> {
            if (b == payBtn) {
                double sub = 450.0;
                double disc = 0.0;
                try { sub = Double.parseDouble(subtotalField.getText().trim()); } catch (Exception ignored) {}
                try { disc = Double.parseDouble(discountField.getText().trim()); } catch (Exception ignored) {}
                double gst = (sub - disc) * 0.05;
                double grand = (sub - disc) + gst;

                return new PharmacyBill(
                        UUID.randomUUID().toString(),
                        billNum.getText().trim(),
                        null,
                        "PAT-WALKIN",
                        patField.getText().trim(),
                        docField.getText().trim(),
                        Instant.now(),
                        sub,
                        disc,
                        gst,
                        grand,
                        payMode.getValue(),
                        "PAID",
                        itemsArea.getText().trim()
                );
            }
            return null;
        });

        dlg.showAndWait().ifPresent(bill -> {
            try {
                clinicDao.createPharmacyBill(bill);
                refreshAllData();
                showAlert("Payment Received", "Bill " + bill.billNumber() + " generated successfully! Total Paid: ₹" + String.format("%.2f", bill.totalInr()));
            } catch (Exception ex) {
                log.error("Failed to save bill", ex);
            }
        });
    }

    private void viewSelectedBill() {
        PharmacyBill sel = billsTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Pharmacy Dispensary Invoice");
        alert.setHeaderText("Invoice No: " + sel.billNumber() + " | Date: " + sel.billDate().toString().substring(0, 10));
        alert.setContentText(
                "Patient: " + sel.patientName() + "\n" +
                "Doctor: " + sel.doctorName() + "\n\n" +
                "Items:\n" + sel.itemsJson() + "\n\n" +
                "Subtotal: ₹" + String.format("%.2f", sel.subtotalInr()) + "\n" +
                "Discount: ₹" + String.format("%.2f", sel.discountInr()) + "\n" +
                "GST (5%): ₹" + String.format("%.2f", sel.gstAmountInr()) + "\n" +
                "GRAND TOTAL: ₹" + String.format("%.2f", sel.totalInr()) + "\n" +
                "Payment Mode: " + sel.paymentMode() + " (Status: " + sel.paymentStatus() + ")"
        );
        alert.showAndWait();
    }

    // =========================================================================
    // MASTER MEDICINE DIRECTORY & PHARMACOLOGICAL CONSTITUENTS VS STOCK
    // =========================================================================

    private Node createMasterVsStockPane() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(10));

        // Top Search Bar
        HBox topBox = new HBox(12);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(0, 0, 10, 0));

        TextField searchField = new TextField();
        searchField.setPromptText("Search master remedies by Latin, common, or Indian name (e.g. Neem, Arjuna, Calendula, Jamun)...");
        searchField.setPrefWidth(480);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterMasterConstituents(newVal);
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label infoBadge = new Label("READ-ONLY MASTER DIRECTORY • PROPOSALS SUBJECT TO ADMIN APPROVAL");
        infoBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.WARNING);

        topBox.getChildren().addAll(new Label("Filter Medicines:"), searchField, sp, infoBadge);
        pane.setTop(topBox);

        // Split Layout: Left = Table, Right = Scientific Monograph
        SplitPane split = new SplitPane();
        split.setDividerPositions(0.48);

        // Left Table
        masterConstituentsTable = new TableView<>(masterConstituentsList);
        masterConstituentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<MedicineConstituent, String> colCode = new TableColumn<>("Remedy Code");
        colCode.setCellValueFactory(mc -> new SimpleStringProperty(mc.getValue().remedyId()));
        colCode.setPrefWidth(90);

        TableColumn<MedicineConstituent, String> colLatin = new TableColumn<>("Latin Binomial Name");
        colLatin.setCellValueFactory(mc -> new SimpleStringProperty(mc.getValue().remedyName()));
        colLatin.setPrefWidth(200);

        TableColumn<MedicineConstituent, String> colCommon = new TableColumn<>("Common / Indian Name");
        colCommon.setCellValueFactory(mc -> new SimpleStringProperty(mc.getValue().commonAndIndianName()));
        colCommon.setPrefWidth(220);

        TableColumn<MedicineConstituent, String> colStock = new TableColumn<>("Dispensary Stock Presence");
        colStock.setCellValueFactory(mc -> {
            String remId = mc.getValue().remedyId();
            String remName = mc.getValue().remedyName();
            int totalUnits = inventoryList.stream()
                    .filter(i -> (i.remedyId() != null && i.remedyId().equalsIgnoreCase(remId)) ||
                                 (i.medicineName() != null && (i.medicineName().equalsIgnoreCase(remName) || i.medicineName().equalsIgnoreCase(remId))))
                    .mapToInt(MedicineInventoryItem::currentStockQty)
                    .sum();
            if (totalUnits > 0) {
                return new SimpleStringProperty("✓ IN STOCK (" + totalUnits + " units)");
            } else {
                return new SimpleStringProperty("⚠ NOT IN STOCK (0)");
            }
        });
        colStock.setPrefWidth(160);

        masterConstituentsTable.getColumns().addAll(List.of(colCode, colLatin, colCommon, colStock));

        // Right Detail Card
        ScrollPane detailScroll = new ScrollPane();
        detailScroll.setFitToWidth(true);
        VBox detailCard = new VBox(12);
        detailCard.setPadding(new Insets(14));
        detailCard.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 8;");

        Label lblTitle = new Label("Select a medicine on the left to inspect internal constituents...");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lblTitle.getStyleClass().add(Styles.ACCENT);

        Label lblCode = new Label("");
        lblCode.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lblCode.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ACCENT);

        Label lblIndian = new Label("");
        lblIndian.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        lblIndian.getStyleClass().add(Styles.TEXT_MUTED);

        TextArea txtActive = createMonographTextArea();
        TextArea txtPharm = createMonographTextArea();
        TextArea txtOrgan = createMonographTextArea();
        TextArea txtTox = createMonographTextArea();
        TextArea txtRhyme = createMonographTextArea();
        txtRhyme.setPrefRowCount(4);
        TextArea txtAnnex = createMonographTextArea();

        Button btnProposeRemark = new Button("📝 Propose Scientific Remark / Clinical Correction");
        btnProposeRemark.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS);
        btnProposeRemark.setDisable(true);

        detailCard.getChildren().addAll(
                lblTitle, lblCode, lblIndian,
                new Separator(),
                new Label("🎵 Clinical Rhyme & Keynote for Medicine Choosing:"), txtRhyme,
                new Label("🔬 Active Phytochemical, Biochemical & Alkaloid/Glycoside Constituents:"), txtActive,
                new Label("⚙️ Scientific Mode of Action (Cellular & Pharmacological):"), txtPharm,
                new Label("🎯 Organ System & Tissue Affinity:"), txtOrgan,
                new Label("⚠️ Toxicology, Antidotes & Incompatible Principles:"), txtTox,
                new Label("📋 Approved Clinical Remarks (Annexed by Admin):"), txtAnnex,
                new Separator(),
                btnProposeRemark
        );
        detailScroll.setContent(detailCard);

        masterConstituentsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, mc) -> {
            if (mc != null) {
                lblTitle.setText(mc.remedyName());
                lblCode.setText("Remedy Code: [" + mc.remedyId() + "]");
                lblIndian.setText("Common / Indian Name: " + mc.commonAndIndianName());
                txtActive.setText(mc.activeConstituents());
                txtPharm.setText(mc.pharmacologicalAction());
                txtOrgan.setText(mc.organAffinity());
                txtTox.setText(mc.toxicologyAndAntidotes());
                txtAnnex.setText((mc.annexedRemarks() != null && !mc.annexedRemarks().isBlank())
                        ? mc.annexedRemarks()
                        : "No annexed remarks yet. Doctors or pharmacists may submit proposed observations below.");

                String rhyme = HomeopathicClinicalRhymes.findRhyme(mc.remedyId(), mc.remedyName(), mc.commonAndIndianName());
                if (rhyme != null) {
                    txtRhyme.setText(rhyme);
                } else {
                    txtRhyme.setText("No clinical mnemonic rhyme registered in database for this remedy. (See Materia Medica library for full provings).");
                }

                btnProposeRemark.setDisable(false);
            } else {
                lblCode.setText("");
                txtRhyme.setText("");
                btnProposeRemark.setDisable(true);
            }
        });

        btnProposeRemark.setOnAction(e -> {
            MedicineConstituent sel = masterConstituentsTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                showProposeRemarkDialog(sel);
            }
        });

        split.getItems().addAll(masterConstituentsTable, detailScroll);
        pane.setCenter(split);

        return pane;
    }

    private TextArea createMonographTextArea() {
        TextArea ta = new TextArea();
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefRowCount(3);
        ta.setStyle("-fx-font-size: 12px;");
        return ta;
    }

    private void filterMasterConstituents(String query) {
        try {
            if (query == null || query.isBlank()) {
                masterConstituentsList.setAll(clinicDao.getAllMedicineConstituents());
            } else {
                masterConstituentsList.setAll(clinicDao.searchMedicineConstituents(query));
            }
        } catch (SQLException ex) {
            log.error("Failed to filter master constituents", ex);
        }
    }

    private void showProposeRemarkDialog(MedicineConstituent mc) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Propose Scientific Remark / Clinical Correction");
        dialog.setHeaderText("Remedy: " + mc.remedyName() + " (" + mc.commonAndIndianName() + ")\n" +
                "Note: Master data is read-only. Your proposed remark will be queued for Admin approval\n" +
                "and permanently annexed to this monograph with your username and timestamp upon review.");

        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        TextArea txtInput = new TextArea();
        txtInput.setPromptText("Enter validated biochemical finding, clinical observation, or pharmacological correction...");
        txtInput.setWrapText(true);
        txtInput.setPrefRowCount(5);

        box.getChildren().addAll(new Label("Your Clinical Remark / Correction:"), txtInput);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? txtInput.getText().trim() : null);

        dialog.showAndWait().ifPresent(remarkText -> {
            if (!remarkText.isBlank()) {
                try {
                    String userId = "usr-doc";
                    String userName = "Dr. Consulting Prescriber";
                    if (securityService.getCurrentSession() != null) {
                        User u = securityService.getCurrentSession().user();
                        userId = u.id();
                        userName = u.fullName();
                    }

                    MedicineConstituentRemark r = new MedicineConstituentRemark(
                            UUID.randomUUID().toString(),
                            mc.remedyId(),
                            userId,
                            userName,
                            remarkText,
                            MedicineConstituentRemark.Status.PENDING,
                            null,
                            Instant.now(),
                            null
                    );
                    clinicDao.submitConstituentRemark(r);
                    showAlert("Proposal Submitted", "Your proposed remark for " + mc.remedyName() + " has been submitted to the Administrator for approval and annexation.");
                } catch (SQLException ex) {
                    log.error("Failed to submit remark", ex);
                    showAlert("Submission Error", "Failed to submit remark: " + ex.getMessage());
                }
            }
        });
    }

    // =========================================================================
    // GENERAL REFRESH
    // =========================================================================

    public void refreshAllData() {
        try {
            inventoryList.setAll(clinicDao.getAllInventoryItems());
            ordersList.setAll(clinicDao.getAllPharmacyOrders());
            reordersList.setAll(clinicDao.getAllReorderRequests());
            billsList.setAll(clinicDao.getAllPharmacyBills());
            masterConstituentsList.setAll(clinicDao.getAllMedicineConstituents());
            statusLabel.setText("Data refreshed. Total Stock Items: " + inventoryList.size() + " | Master Medicines: " + masterConstituentsList.size());
        } catch (SQLException ex) {
            log.error("Failed to refresh pharmacy data", ex);
        }
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
