package laundrysystem;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;

/**
 * Main application window for the Simple Laundry System Dashboard.
 *
 * This class is structured as a NetBeans "JFrame Form": the outer frame
 * (this class) has a matching MainDashboard.form file, so it opens in the
 * NetBeans GUI Designer (Design view) as well as the Source view.
 *
 * The 4 dashboard tabs are populated in buildTabs() with plain Java code
 * rather than the drag-and-drop designer, because their content is dynamic
 * (live JTables, computed stats, listeners) — the kind of thing Matisse's
 * static designer can't represent. This is the normal approach even in
 * hand-built NetBeans projects for data-driven screens.
 */
public class MainDashboard extends javax.swing.JFrame {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final Color PRIMARY = new Color(41, 128, 185);
    private static final Color BG = new Color(245, 247, 250);

    // Dashboard tab widgets
    private JLabel totalOrdersValue, pendingValue, inProgressValue, revenueValue;
    private DefaultTableModel recentModel;

    // Orders tab widgets
    private DefaultTableModel ordersModel;

    // Customers tab widgets
    private DefaultTableModel customersModel;

    // Claim tab widgets
    private DefaultTableModel claimsModel;
    private JLabel claimStatusLabel;

    // New Order tab widgets
    private JComboBox<Customer> customerCombo;
    private JComboBox<String> serviceCombo;
    private JSpinner quantitySpinner;
    private JLabel quantityHintLabel;
    private JLabel priceEstimateLabel;

    /**
     * Creates new form MainDashboard
     */
    public MainDashboard() {
       initComponents();

        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        jTabbedPane1.setFont(new Font("SansSerif", Font.PLAIN, 14));

        DatabaseManager.loadOrdersToDataStore();

        buildTabs();

        refreshDashboard();

        // Powers the "scan QR -> opens a status webpage" flow, and the
        // "scan QR -> claims the order" flow. If it fails to bind (e.g.
        // port 8080 already in use), the rest of the app still works --
        // just without the website features.
        StatusServer.start();

        // When a customer claims an order by scanning with their own phone
        // (via /claim), add it to the Claims table AND refresh the Orders
        // tab / Dashboard right away, same as a staff-initiated claim does.
        StatusServer.setOnClaimCallback(record -> {
            addClaimRow(record, "Claimed (phone)");
            refreshOrdersTable();
            refreshDashboard();
        });
    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 287, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to default look and feel
        }

        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new MainDashboard().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTabbedPane jTabbedPane1;
    // End of variables declaration//GEN-END:variables

    // ---------------------------------------------------------------
    // TAB CONSTRUCTION (hand-coded — dynamic content, not in the form)
    // ---------------------------------------------------------------
    private void buildTabs() {
        jTabbedPane1.addTab("Dashboard", createDashboardPanel());
        jTabbedPane1.addTab("New Order", createNewOrderPanel());
        jTabbedPane1.addTab("Orders", createOrdersPanel());
        jTabbedPane1.addTab("Customers", createCustomersPanel());
        jTabbedPane1.addTab("Claim", createClaimPanel());

        jTabbedPane1.addChangeListener(e -> {
            refreshDashboard();
            refreshCustomerCombo();
        });
    }

    // ---------------------------------------------------------------
    // DASHBOARD TAB
    // ---------------------------------------------------------------
    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(BG);

        JLabel header = new JLabel("Laundry Shop Overview");
        header.setFont(new Font("SansSerif", Font.BOLD, 22));
        panel.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(15, 15));
        center.setBackground(BG);

        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 15, 15));
        statsPanel.setBackground(BG);

        totalOrdersValue = new JLabel("0");
        pendingValue = new JLabel("0");
        inProgressValue = new JLabel("0");
        revenueValue = new JLabel("\u20B10.00");

        statsPanel.add(createStatCard("Total Orders", totalOrdersValue, new Color(52, 152, 219)));
        statsPanel.add(createStatCard("Pending", pendingValue, new Color(230, 126, 34)));
        statsPanel.add(createStatCard("In Progress", inProgressValue, new Color(155, 89, 182)));
        statsPanel.add(createStatCard("Total Revenue", revenueValue, new Color(39, 174, 96)));

        center.add(statsPanel, BorderLayout.NORTH);

        JPanel middleRow = new JPanel(new BorderLayout(15, 15));
        middleRow.setBackground(BG);
        middleRow.add(createQrGeneratorCard(), BorderLayout.NORTH);
        center.add(middleRow, BorderLayout.CENTER);

        JPanel recentPanel = new JPanel(new BorderLayout(5, 5));
        recentPanel.setBackground(Color.WHITE);
        recentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(10, 10, 10, 10)));

        JLabel recentLabel = new JLabel("Recent Orders");
        recentLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        recentPanel.add(recentLabel, BorderLayout.NORTH);

        recentModel = new DefaultTableModel(
                new Object[]{"ID", "Customer", "Service", "Status", "Price"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable recentTable = new JTable(recentModel);
        recentTable.setRowHeight(26);
        recentPanel.add(new JScrollPane(recentTable), BorderLayout.CENTER);

        middleRow.add(recentPanel, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshDashboard());
        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topRight.setBackground(BG);
        topRight.add(refreshBtn);
        panel.add(topRight, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Small card on the Dashboard tab: staff type an order ID, click
     * Generate, and get that order's claim QR code -- useful for reprinting
     * a lost QR without having to recreate the order.
     */
    private JPanel createQrGeneratorCard() {
        JPanel card = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(5, 10, 5, 10)));

        JLabel label = new JLabel("Generate / Reprint QR Code -- Order ID:");
        label.setFont(new Font("SansSerif", Font.PLAIN, 14));
        card.add(label);

        JTextField orderIdField = new JTextField(6);
        card.add(orderIdField);

        JButton generateBtn = new JButton("Generate QR");
        generateBtn.setBackground(PRIMARY);
        generateBtn.setForeground(Color.WHITE);
        generateBtn.setFocusPainted(false);
        generateBtn.addActionListener(e -> {
            String text = orderIdField.getText().trim();
            if (text.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter an order ID first.");
                return;
            }
            int orderId;
            try {
                orderId = Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Order ID must be a number.");
                return;
            }
            DatabaseManager.OrderRecord record = DatabaseManager.findById(orderId);
            if (record == null) {
                JOptionPane.showMessageDialog(this,
                        "No order #" + orderId + " found in the database.",
                        "Not Found", JOptionPane.WARNING_MESSAGE);
                return;
            }
            showQrDialog(StatusServer.buildStatusUrl(record.claimToken), record.id, record.customerName);
        });
        card.add(generateBtn);

        return card;
    }

    /**
     * Renders and displays the QR code for the given content (the order's
     * status-page URL), with an option to save it as a PNG for printing.
     * Shared by order creation and the Dashboard's Generate/Reprint tool.
     */
    private void showQrDialog(String qrContent, int orderId, String customerName) {
        try {
            java.awt.image.BufferedImage qrImage = QRCodeUtil.generate(qrContent);

            JPanel panel = new JPanel(new BorderLayout(10, 10));
            JLabel info = new JLabel("<html>Order #" + orderId + " for " + customerName
                    + "<br>Scanning this QR code opens the order's status page.<br>"
                    + "Give it to the customer, or use it in the Claim tab at pickup.</html>");
            panel.add(info, BorderLayout.NORTH);
            panel.add(new JLabel(new ImageIcon(qrImage)), BorderLayout.CENTER);

            int result = JOptionPane.showOptionDialog(this, panel, "QR Code",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                    new Object[]{"Save as PNG", "Close"}, "Close");

            if (result == 0) {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new java.io.File("order-" + orderId + "-qr.png"));
                if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    QRCodeUtil.generateToFile(qrContent, chooser.getSelectedFile());
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Couldn't generate the QR code: " + ex.getMessage(),
                    "QR Generation Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    private JPanel createStatCard(String title, JLabel valueLabel, Color color) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 4, 0, color),
                new EmptyBorder(15, 15, 15, 15)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        titleLabel.setForeground(new Color(120, 120, 120));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        valueLabel.setForeground(color);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(valueLabel);
        return card;
    }

    private void refreshDashboard() {
        List<Order> orders = DataStore.getOrders();
        int total = orders.size();
        int pending = 0, inProgress = 0;
        double revenue = 0;

        for (Order o : orders) {
            if (o.getStatus().equals("Pending")) pending++;
            if (o.isInProgress()) inProgress++;
            if (o.isCompleted()) revenue += o.getPrice();
        }

        totalOrdersValue.setText(String.valueOf(total));
        pendingValue.setText(String.valueOf(pending));
        inProgressValue.setText(String.valueOf(inProgress));
        revenueValue.setText(String.format("\u20B1%.2f", revenue));

        recentModel.setRowCount(0);
        int start = Math.max(0, orders.size() - 6);
        for (int i = orders.size() - 1; i >= start; i--) {
            Order o = orders.get(i);
            recentModel.addRow(new Object[]{
                    o.getDisplayId(), o.getCustomer().getName(), o.getServiceType(),
                    o.getStatus(), String.format("\u20B1%.2f", o.getPrice())
            });
        }
    }

    // ---------------------------------------------------------------
    // NEW ORDER TAB
    // ---------------------------------------------------------------
    private JPanel createNewOrderPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BG);
        outer.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel header = new JLabel("Create a New Order");
        header.setFont(new Font("SansSerif", Font.BOLD, 22));
        outer.add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(25, 25, 25, 25)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        Font labelFont = new Font("SansSerif", Font.PLAIN, 14);

        // Customer
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel custLbl = new JLabel("Customer:");
        custLbl.setFont(labelFont);
        form.add(custLbl, gbc);

        customerCombo = new JComboBox<>();
        refreshCustomerCombo();
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1;
        form.add(customerCombo, gbc);

        JButton newCustBtn = new JButton("+ New Customer");
        newCustBtn.addActionListener(e -> {
            jTabbedPane1.setSelectedIndex(3);
        });
        gbc.gridx = 2; gbc.weightx = 0;
        form.add(newCustBtn, gbc);

        // Service type
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel svcLbl = new JLabel("Service Type:");
        svcLbl.setFont(labelFont);
        form.add(svcLbl, gbc);

        serviceCombo = new JComboBox<>(Order.SERVICE_TYPES);
        serviceCombo.addActionListener(e -> updatePriceEstimate());
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 2;
        form.add(serviceCombo, gbc);
        gbc.gridwidth = 1;

        // Quantity
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel qtyLbl = new JLabel("Quantity:");
        qtyLbl.setFont(labelFont);
        form.add(qtyLbl, gbc);

        quantitySpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.5, 100.0, 0.5));
        quantitySpinner.addChangeListener(e -> updatePriceEstimate());
        gbc.gridx = 1; gbc.gridy = 2;
        form.add(quantitySpinner, gbc);

        quantityHintLabel = new JLabel("(kilos)");
        quantityHintLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        quantityHintLabel.setForeground(Color.GRAY);
        gbc.gridx = 2; gbc.gridy = 2;
        form.add(quantityHintLabel, gbc);

        // Drop-off date (today, fixed)
        gbc.gridx = 0; gbc.gridy = 3;
        JLabel dateLbl = new JLabel("Drop-off Date:");
        dateLbl.setFont(labelFont);
        form.add(dateLbl, gbc);

        JLabel todayLabel = new JLabel(LocalDate.now().format(DATE_FMT));
        todayLabel.setFont(labelFont);
        gbc.gridx = 1; gbc.gridy = 3;
        form.add(todayLabel, gbc);

        // Price estimate
        gbc.gridx = 0; gbc.gridy = 4;
        JLabel priceLbl = new JLabel("Estimated Price:");
        priceLbl.setFont(labelFont);
        form.add(priceLbl, gbc);

        priceEstimateLabel = new JLabel();
        priceEstimateLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        priceEstimateLabel.setForeground(new Color(39, 174, 96));
        gbc.gridx = 1; gbc.gridy = 4;
        form.add(priceEstimateLabel, gbc);

        // Submit
        JButton submitBtn = new JButton("Create Order");
        submitBtn.setBackground(PRIMARY);
        submitBtn.setForeground(Color.WHITE);
        submitBtn.setFocusPainted(false);
        submitBtn.addActionListener(this::onCreateOrder);
        gbc.gridx = 1; gbc.gridy = 5; gbc.anchor = GridBagConstraints.EAST;
        form.add(submitBtn, gbc);

        updatePriceEstimate();

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wrapper.setBackground(BG);
        wrapper.add(form);
        outer.add(wrapper, BorderLayout.CENTER);

        return outer;
    }

    private void updatePriceEstimate() {
        if (priceEstimateLabel == null) return;
        String service = (String) serviceCombo.getSelectedItem();
        double qty = ((Number) quantitySpinner.getValue()).doubleValue();
        double rate;
        String unit;
        switch (service) {
            case "Wash & Fold": rate = 50.0; unit = "kilos"; break;
            case "Dry Clean": rate = 150.0; unit = "items"; break;
            default: rate = 30.0; unit = "items"; break;
        }
        quantityHintLabel.setText("(" + unit + ")");
        priceEstimateLabel.setText(String.format("\u20B1%.2f", qty * rate));
    }

    private void onCreateOrder(ActionEvent e) {
        Customer selected = (Customer) customerCombo.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                    "Please add a customer first (use the Customers tab).",
                    "No Customer", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String service = (String) serviceCombo.getSelectedItem();
        double qty = ((Number) quantitySpinner.getValue()).doubleValue();

        // In-memory order (drives the Dashboard/Orders tabs as before)
        Order order = DataStore.addOrder(selected, service, qty, LocalDate.now());

        // Persist to MySQL with a fresh claim token, then show the QR code
        // for that token so it can be given to the customer at drop-off.
        String claimToken = QRCodeUtil.newClaimToken();
        int dbId = DatabaseManager.insertOrder(selected.getName(), service, qty, order.getPrice(), claimToken);

        if (dbId == -1) {
            JOptionPane.showMessageDialog(this,
                    "Order #" + order.getId() + " was created, but saving it to the database failed.\n"
                            + "Check the console for details -- the order won't be claimable until this is fixed.",
                    "Database Error", JOptionPane.WARNING_MESSAGE);
        } else {
            order.setDbId(dbId); // keeps the displayed/QR'd ID matching the real MySQL row
            showQrDialog(StatusServer.buildStatusUrl(claimToken), order.getDisplayId(), selected.getName());
        }

        refreshOrdersTable();
        refreshDashboard();
        jTabbedPane1.setSelectedIndex(2);
    }

    private void refreshCustomerCombo() {
        if (customerCombo == null) return;
        Customer previouslySelected = (Customer) customerCombo.getSelectedItem();
        customerCombo.removeAllItems();
        for (Customer c : DataStore.getCustomers()) {
            customerCombo.addItem(c);
        }
        if (previouslySelected != null && DataStore.getCustomers().contains(previouslySelected)) {
            customerCombo.setSelectedItem(previouslySelected);
        }
    }

    // ---------------------------------------------------------------
    // ORDERS TAB
    // ---------------------------------------------------------------
    private JPanel createOrdersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel header = new JLabel("All Orders");
        header.setFont(new Font("SansSerif", Font.BOLD, 22));
        panel.add(header, BorderLayout.NORTH);

        ordersModel = new DefaultTableModel(
                new Object[]{"ID", "Customer", "Service", "Qty", "Drop-off", "Status", "Price"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 5; // only Status column is editable
            }
        };
        JTable ordersTable = new JTable(ordersModel);
        ordersTable.setRowHeight(28);
        JComboBox<String> statusEditorCombo = new JComboBox<>(Order.STATUSES);
        ordersTable.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(statusEditorCombo));

        ordersModel.addTableModelListener(evt -> {
            if (evt.getType() == TableModelEvent.UPDATE && evt.getColumn() == 5) {
                int row = evt.getFirstRow();
                int orderId = (int) ordersModel.getValueAt(row, 0);
                String newStatus = (String) ordersModel.getValueAt(row, 5);
                Order order = DataStore.findOrderById(orderId);
                if (order != null) {
                    order.setStatus(newStatus);
                    if (order.getDbId() != -1) {
                        DatabaseManager.updateStatus(order.getDbId(), newStatus);
                    }
                    refreshDashboard();
                }
            }
        });

        panel.add(new JScrollPane(ordersTable), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBackground(BG);
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshOrdersTable());
        JButton deleteBtn = new JButton("Delete Selected");
        deleteBtn.addActionListener(e -> {
            int row = ordersTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Select an order to delete.");
                return;
            }
            int orderId = (int) ordersModel.getValueAt(row, 0);
            Order order = DataStore.findOrderById(orderId);
            if (order != null) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Delete order #" + orderId + "?", "Confirm Delete",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    DataStore.removeOrder(order);
                    refreshOrdersTable();
                    refreshDashboard();
                }
            }
        });
        buttons.add(refreshBtn);
        buttons.add(deleteBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        refreshOrdersTable();
        return panel;
    }

    private void refreshOrdersTable() {
        ordersModel.setRowCount(0);
        for (Order o : DataStore.getOrders()) {
            ordersModel.addRow(new Object[]{
                    o.getDisplayId(),
                    o.getCustomer().getName(),
                    o.getServiceType(),
                    o.getQuantity(),
                    o.getDropOffDate().format(DATE_FMT),
                    o.getStatus(),
                    String.format("\u20B1%.2f", o.getPrice())
            });
        }
    }

    // ---------------------------------------------------------------
    // CUSTOMERS TAB
    // ---------------------------------------------------------------
    private JPanel createCustomersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel header = new JLabel("Customers");
        header.setFont(new Font("SansSerif", Font.BOLD, 22));
        panel.add(header, BorderLayout.NORTH);

        customersModel = new DefaultTableModel(
                new Object[]{"ID", "Name", "Phone", "Address"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable customersTable = new JTable(customersModel);
        customersTable.setRowHeight(28);
        panel.add(new JScrollPane(customersTable), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBackground(BG);

        JButton addBtn = new JButton("Add Customer");
        addBtn.setBackground(PRIMARY);
        addBtn.setForeground(Color.WHITE);
        addBtn.setFocusPainted(false);
        addBtn.addActionListener(e -> showAddCustomerDialog());

        JButton deleteBtn = new JButton("Delete Selected");
        deleteBtn.addActionListener(e -> {
            int row = customersTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Select a customer to delete.");
                return;
            }
            int custId = (int) customersModel.getValueAt(row, 0);
            Customer target = null;
            for (Customer c : DataStore.getCustomers()) {
                if (c.getId() == custId) { target = c; break; }
            }
            if (target != null) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Delete customer \"" + target.getName() + "\"?", "Confirm Delete",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    DataStore.removeCustomer(target);
                    refreshCustomersTable();
                    refreshCustomerCombo();
                }
            }
        });

        buttons.add(addBtn);
        buttons.add(deleteBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        refreshCustomersTable();
        return panel;
    }

    private void showAddCustomerDialog() {
        JTextField nameField = new JTextField();
        JTextField phoneField = new JTextField();
        JTextField addressField = new JTextField();

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("Name:"));
        form.add(nameField);
        form.add(new JLabel("Phone:"));
        form.add(phoneField);
        form.add(new JLabel("Address:"));
        form.add(addressField);

        int result = JOptionPane.showConfirmDialog(this, form, "Add New Customer",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String phone = phoneField.getText().trim();
            String address = addressField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Customer name is required.",
                        "Missing Name", JOptionPane.WARNING_MESSAGE);
                return;
            }
            DataStore.addCustomer(name, phone, address);
            refreshCustomersTable();
            refreshCustomerCombo();
        }
    }

    private void refreshCustomersTable() {
        customersModel.setRowCount(0);
        for (Customer c : DataStore.getCustomers()) {
            customersModel.addRow(new Object[]{
                    c.getId(), c.getName(), c.getPhone(), c.getAddress()
            });
        }
    }

    // ---------------------------------------------------------------
    // CLAIM TAB -- "Scan to Claim": staff load a photo of a customer's QR
    // code (or the status-page link's QR), the app decodes it, looks the
    // token up in MySQL, marks the order claimed + delivered there, and
    // mirrors that onto the in-memory order too so the Orders tab and
    // Dashboard stats update immediately without needing a restart. Every
    // attempt -- success, already-claimed, or not-found -- is added to the
    // Customer Claims table below, most recent first.
    // ---------------------------------------------------------------
    private JPanel createClaimPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel header = new JLabel("Scan to Claim");
        header.setFont(new Font("SansSerif", Font.BOLD, 22));
        panel.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(15, 15));
        center.setBackground(BG);

        JPanel topPanels = new JPanel();
        topPanels.setLayout(new BoxLayout(topPanels, BoxLayout.Y_AXIS));
        topPanels.setBackground(BG);

        // --- Scan with phone: show a QR that claims the order the moment
        // a customer's phone opens it -- no staff scanning needed. ---
        JPanel phonePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        phonePanel.setBackground(Color.WHITE);
        phonePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(0, 0, 0, 0)));

        JLabel phoneLabel = new JLabel("Scan QR Code with Phone to Claim -- Order ID:");
        phoneLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        phonePanel.add(phoneLabel);

        JTextField phoneOrderIdField = new JTextField(6);
        phonePanel.add(phoneOrderIdField);

        JButton showClaimQrBtn = new JButton("Show QR Code");
        showClaimQrBtn.setBackground(PRIMARY);
        showClaimQrBtn.setForeground(Color.WHITE);
        showClaimQrBtn.setFocusPainted(false);
        showClaimQrBtn.addActionListener(e -> onShowClaimQr(phoneOrderIdField.getText().trim()));
        phonePanel.add(showClaimQrBtn);

        topPanels.add(phonePanel);
        topPanels.add(Box.createVerticalStrut(10));

        // --- Staff-side: load a photo of a QR code to claim it directly. ---
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        actionPanel.setBackground(Color.WHITE);
        actionPanel.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));

        JButton scanBtn = new JButton("Scan QR Code to Claim...");
        scanBtn.setBackground(PRIMARY);
        scanBtn.setForeground(Color.BLACK);
        scanBtn.setFocusPainted(false);
        scanBtn.addActionListener(e -> onScanQrToClaim());
        actionPanel.add(scanBtn);

        claimStatusLabel = new JLabel(" ");
        claimStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        actionPanel.add(claimStatusLabel);

        topPanels.add(actionPanel);
        center.add(topPanels, BorderLayout.NORTH);

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(Color.WHITE);
        tablePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(10, 10, 10, 10)));

        JLabel tableLabel = new JLabel("Customer Claims");
        tableLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        tablePanel.add(tableLabel, BorderLayout.NORTH);

        claimsModel = new DefaultTableModel(
                new Object[]{"Order ID", "Customer", "Service", "Price", "Result"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable claimsTable = new JTable(claimsModel);
        claimsTable.setRowHeight(26);
        tablePanel.add(new JScrollPane(claimsTable), BorderLayout.CENTER);

        center.add(tablePanel, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Looks up the order by ID and shows its CLAIM QR code (not the
     * read-only status QR) -- opening this link on a phone claims the
     * order immediately, no staff action needed. Only use this once an
     * order is actually ready for pickup.
     */
    private void onShowClaimQr(String orderIdText) {
        if (orderIdText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter an order ID first.");
            return;
        }
        int orderId;
        try {
            orderId = Integer.parseInt(orderIdText);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Order ID must be a number.");
            return;
        }

        DatabaseManager.OrderRecord record = DatabaseManager.findById(orderId);
        if (record == null) {
            JOptionPane.showMessageDialog(this,
                    "No order #" + orderId + " found in the database.",
                    "Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (record.claimed) {
            JOptionPane.showMessageDialog(this,
                    "Order #" + orderId + " was already claimed.",
                    "Already Claimed", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            java.awt.image.BufferedImage qrImage = QRCodeUtil.generate(
                    StatusServer.buildClaimUrl(record.claimToken));

            JPanel panel = new JPanel(new BorderLayout(10, 10));
            JLabel info = new JLabel("<html>Order #" + record.id + " for " + record.customerName
                    + "<br><b>Scanning this QR code immediately claims the order.</b><br>"
                    + "Hand your phone to the customer to scan, or let them scan it directly.</html>");
            panel.add(info, BorderLayout.NORTH);
            panel.add(new JLabel(new ImageIcon(qrImage)), BorderLayout.CENTER);

            JOptionPane.showMessageDialog(this, panel, "Claim QR Code", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Couldn't generate the QR code: " + ex.getMessage(),
                    "QR Generation Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void onScanQrToClaim() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select QR code image");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Image files", "png", "jpg", "jpeg"));

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File selected = chooser.getSelectedFile();

        try {
            String scanned = QRCodeUtil.decode(selected);
            if (scanned == null) {
                setClaimStatus("No QR code found in that image.", Color.RED);
                return;
            }
            String token = QRCodeUtil.extractTokenFromScannedContent(scanned);
            processClaim(token);
        } catch (Exception ex) {
            setClaimStatus("Couldn't read that image: " + ex.getMessage(), Color.RED);
        }
    }

    private void processClaim(String token) {
        DatabaseManager.OrderRecord record = DatabaseManager.findByToken(token);
        if (record == null) {
            setClaimStatus("QR code not recognized -- no matching order.", Color.RED);
            return;
        }
        if (record.claimed) {
            setClaimStatus("Order #" + record.id + " was already claimed.", new Color(230, 126, 34));
            addClaimRow(record, "Already claimed");
            return;
        }

        boolean success = DatabaseManager.markClaimed(record.id);
        if (success) {
            setClaimStatus("Order #" + record.id + " (" + record.customerName + ") claimed successfully.",
                    new Color(39, 174, 96));
            addClaimRow(record, "Claimed");

            // Mirror the claim onto the in-memory order so the Orders tab
            // and Dashboard stats update immediately, not just MySQL.
            Order localOrder = DataStore.findOrderById(record.id);
            if (localOrder != null) {
                localOrder.setStatus("Delivered");
            }
            refreshOrdersTable();
            refreshDashboard();

            // Sheets sync failure won't block the claim -- see GoogleSheetsSync.logClaim
            GoogleSheetsSync.logClaim(record.id, record.customerName, record.serviceType, record.price);
        } else {
            setClaimStatus("Could not claim order #" + record.id + " (already claimed by someone else?).",
                    Color.RED);
            addClaimRow(record, "Failed");
        }
    }

    private void addClaimRow(DatabaseManager.OrderRecord record, String outcome) {
        claimsModel.insertRow(0, new Object[]{
                record.id, record.customerName, record.serviceType,
                String.format("\u20B1%.2f", record.price), outcome
        });
    }

    private void setClaimStatus(String text, Color color) {
        claimStatusLabel.setText(text);
        claimStatusLabel.setForeground(color);
    }
}
