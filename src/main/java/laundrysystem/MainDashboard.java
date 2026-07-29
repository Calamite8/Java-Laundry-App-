package laundrysystem;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Main application window for the Simple Laundry System Dashboard.
 *
 * This class is structured as a NetBeans "JFrame Form": the outer frame
 * (this class) has a matching MainDashboard.form file, so it opens in the
 * NetBeans GUI Designer (Design view) as well as the Source view.
 *
 * Tabs: Dashboard, New Order & Customers (merged), Orders & Claims (merged,
 * multi-select + manually editable), Settings. Content is built in
 * buildTabs() with plain Java code rather than the drag-and-drop designer,
 * because it's dynamic (live JTables, computed stats, listeners) -- the
 * kind of thing Matisse's static designer can't represent.
 */
public class MainDashboard extends javax.swing.JFrame {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final Color PRIMARY = new Color(41, 128, 185);
    private static final Color BG = new Color(245, 247, 250);

    // Dashboard tab widgets
    private JLabel totalOrdersValue, pendingValue, inProgressValue, revenueValue;
    private DefaultTableModel recentModel;

    // Orders & Claims tab widgets
    private DefaultTableModel ordersModel;
    private JTable ordersTable;
    private DefaultTableModel claimsModel;
    private JLabel claimStatusLabel;

    // New Order & Customers tab widgets
    private DefaultTableModel customersModel;
    private JComboBox<Customer> customerCombo;
    private JComboBox<String> serviceCombo;
    private JSpinner quantitySpinner;
    private JCheckBox includeSoapCheck;
    private JCheckBox includeDetergentCheck;
    private JList<String> itemTypeList;
    private JLabel quantityHintLabel;
    private JLabel priceEstimateLabel;

    // Guards against the Orders table's edit-listener reacting to rows
    // being cleared/re-added during refreshOrdersTable() itself.
    private boolean suppressOrdersTableEvents = false;

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

        // "Keep signed in": reaching this point means login just succeeded
        // (Login.java constructs MainDashboard directly on success), so
        // remember that for next launch. Main.java checks this at startup
        // to skip the login form entirely. "Log Out" in Settings clears it.
        SessionManager.markSignedIn();
    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Simple Laundry System - Dashboard");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 1050, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 680, Short.MAX_VALUE)
        );

        pack();
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
        jTabbedPane1.addTab("New Order & Customers", createNewOrderAndCustomersPanel());
        jTabbedPane1.addTab("Orders & Claims", createOrdersAndClaimsPanel());
        jTabbedPane1.addTab("Settings", createSettingsPanel());

        jTabbedPane1.addChangeListener(e -> {
            refreshDashboard();
            refreshCustomerCombo();
            refreshItemTypeList();
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
            showReceiptDialog(record.id, record.customerName, record.customerPhone, record.customerAddress,
                    record.status, record.dropOffDate, record.claimByDate, record.getItemTypesList(),
                    record.getLineItems(), record.price, StatusServer.buildStatusUrl(record.claimToken));
        });
        card.add(generateBtn);

        return card;
    }

    /**
     * Renders and displays the QR code for the given content (the order's
     * status-page URL), with an option to save it as a PNG for printing.
     * Shared by order creation and the Dashboard's Generate/Reprint tool.
     */
    /**
     * Shows the full digital receipt (matching the LOGO / LAUNDRY NAME /
     * status / dropoff+claim dates / customer info / itemized order details
     * / estimated total / QR code layout), with an option to save the QR
     * as a PNG. The exact same content is rendered on the web status page
     * (see StatusServer.renderStatus), just as HTML instead of Swing.
     */
    private void showReceiptDialog(int orderId, String customerName, String customerPhone, String customerAddress,
                                    String status, LocalDate dropOffDate, LocalDate claimByDate,
                                    List<String> itemTypes, List<Order.LineItem> lineItems, double totalPrice,
                                    String qrContent) {
        try {
            JPanel outer = new JPanel();
            outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
            outer.setBackground(new Color(250, 246, 240));
            outer.setBorder(new EmptyBorder(20, 24, 20, 24));

            // Logo + laundry name
            String logoPath = PricingConfig.getLogoPath();
            if (!logoPath.isBlank()) {
                try {
                    java.awt.Image logoImg = ImageIO.read(new java.io.File(logoPath));
                    if (logoImg != null) {
                        Image scaled = logoImg.getScaledInstance(60, 60, Image.SCALE_SMOOTH);
                        JLabel logoLabel = new JLabel(new ImageIcon(scaled));
                        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                        outer.add(logoLabel);
                        outer.add(Box.createVerticalStrut(6));
                    }
                } catch (Exception ignored) {
                    // bad/missing logo file -- just skip it, rest of the receipt still renders
                }
            }

            JLabel nameLabel = new JLabel(PricingConfig.getLaundryName());
            nameLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
            nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            outer.add(nameLabel);
            outer.add(Box.createVerticalStrut(14));

            // Status / Dropoff / Claim
            StringBuilder html = new StringBuilder("<html><body style='width:320px;font-family:sans-serif;font-size:12px;'>");
            html.append("<table width='100%'><tr>")
                    .append("<td><b>STATUS:</b> ").append(escapeHtml(status)).append("</td>")
                    .append("<td align='right'><b>DROPOFF:</b> ").append(dropOffDate.format(DATE_FMT)).append("</td>")
                    .append("</tr><tr><td></td><td align='right'><b>CLAIM BY:</b> ")
                    .append(claimByDate.format(DATE_FMT)).append("</td></tr></table>")
                    .append("<hr>")
                    .append("<b>USER:</b> ").append(escapeHtml(customerName)).append("<br>")
                    .append("<b>CONTACT:</b> ").append(escapeHtml(customerPhone.isBlank() ? "-" : customerPhone)).append("<br>")
                    .append("<b>ADDRESS:</b> ").append(escapeHtml(customerAddress.isBlank() ? "-" : customerAddress));

            if (itemTypes != null && !itemTypes.isEmpty()) {
                html.append("<br><b>ITEMS:</b> ").append(escapeHtml(String.join(", ", itemTypes)));
            }

            html.append("<hr>")
                    .append("<b>ORDER DETAILS</b><table width='100%'>");

            int i = 1;
            for (Order.LineItem item : lineItems) {
                html.append("<tr><td>").append(i++).append(". ").append(escapeHtml(item.name))
                        .append(" (").append(item.quantityDisplay).append(")</td>")
                        .append("<td align='right'>\u20B1").append(String.format("%.2f", item.price)).append("</td></tr>");
            }
            html.append("</table><hr>")
                    .append("<table width='100%'><tr><td><b>ESTIMATED TOTAL</b></td>")
                    .append("<td align='right'><b>\u20B1").append(String.format("%.2f", totalPrice)).append("</b></td></tr></table>")
                    .append("</body></html>");

            JLabel receiptLabel = new JLabel(html.toString());
            receiptLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            outer.add(receiptLabel);
            outer.add(Box.createVerticalStrut(16));

            java.awt.image.BufferedImage qrImage = QRCodeUtil.generate(qrContent);
            JLabel qrLabel = new JLabel(new ImageIcon(qrImage));
            qrLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            outer.add(qrLabel);

            JLabel orderIdLabel = new JLabel("Order #" + orderId);
            orderIdLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            orderIdLabel.setForeground(new Color(140, 140, 140));
            orderIdLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            outer.add(Box.createVerticalStrut(6));
            outer.add(orderIdLabel);

            int result = JOptionPane.showOptionDialog(this, outer, "Digital Receipt",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                    new Object[]{"Save QR as PNG", "Close"}, "Close");

            if (result == 0) {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new java.io.File("order-" + orderId + "-qr.png"));
                if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    QRCodeUtil.generateToFile(qrContent, chooser.getSelectedFile());
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Couldn't generate the receipt: " + ex.getMessage(),
                    "Receipt Generation Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
    // MERGED TAB: NEW ORDER + CUSTOMERS
    // Side by side so the wide empty space in each separate tab gets used:
    // order form on the left, full customer list + management on the right.
    // ---------------------------------------------------------------
    private JPanel createNewOrderAndCustomersPanel() {
        JPanel outer = new JPanel(new BorderLayout(20, 0));
        outer.setBackground(BG);
        outer.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel left = new JPanel(new BorderLayout());
        left.setBackground(BG);
        left.setPreferredSize(new Dimension(380, 0));
        left.add(buildNewOrderForm(), BorderLayout.NORTH);

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(BG);
        right.add(buildCustomersSection(), BorderLayout.CENTER);

        outer.add(left, BorderLayout.WEST);
        outer.add(right, BorderLayout.CENTER);

        return outer;
    }

    private JPanel buildNewOrderForm() {
        JPanel outer = new JPanel(new BorderLayout(0, 15));
        outer.setBackground(BG);

        JLabel header = new JLabel("Create a New Order");
        header.setFont(new Font("SansSerif", Font.BOLD, 20));
        outer.add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(20, 20, 20, 20)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        Font labelFont = new Font("SansSerif", Font.PLAIN, 13);

        // Customer
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel custLbl = new JLabel("Customer:");
        custLbl.setFont(labelFont);
        form.add(custLbl, gbc);

        gbc.gridy = 1;
        customerCombo = new JComboBox<>();
        refreshCustomerCombo();
        gbc.weightx = 1;
        form.add(customerCombo, gbc);
        gbc.gridwidth = 1; gbc.weightx = 0;

        JButton newCustBtn = new JButton("+ New");
        newCustBtn.addActionListener(e -> showAddCustomerDialog());
        gbc.gridx = 2; gbc.gridy = 1;
        form.add(newCustBtn, gbc);

        // Service type
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        JLabel svcLbl = new JLabel("Service Type:");
        svcLbl.setFont(labelFont);
        form.add(svcLbl, gbc);

        gbc.gridy = 3;
        serviceCombo = new JComboBox<>(Order.SERVICE_TYPES);
        serviceCombo.addActionListener(e -> updatePriceEstimate());
        form.add(serviceCombo, gbc);

        // Quantity
        gbc.gridy = 4;
        JLabel qtyLbl = new JLabel("Quantity:");
        qtyLbl.setFont(labelFont);
        form.add(qtyLbl, gbc);

        gbc.gridy = 5; gbc.gridwidth = 2;
        quantitySpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.5, 100.0, 0.5));
        quantitySpinner.addChangeListener(e -> updatePriceEstimate());
        form.add(quantitySpinner, gbc);

        quantityHintLabel = new JLabel("(kilos)");
        quantityHintLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        quantityHintLabel.setForeground(Color.GRAY);
        gbc.gridx = 2; gbc.gridwidth = 1;
        form.add(quantityHintLabel, gbc);

        // Soap add-on
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 3;
        includeSoapCheck = new JCheckBox("Include Soap (+\u20B1" + String.format("%.2f", PricingConfig.getSoapFee()) + ")");
        includeSoapCheck.setFont(labelFont);
        includeSoapCheck.setBackground(Color.WHITE);
        includeSoapCheck.addActionListener(e -> updatePriceEstimate());
        form.add(includeSoapCheck, gbc);

        // Detergent add-on
        gbc.gridy = 7;
        includeDetergentCheck = new JCheckBox("Include Detergent (+\u20B1"
                + String.format("%.2f", PricingConfig.getDetergentFee()) + ")");
        includeDetergentCheck.setFont(labelFont);
        includeDetergentCheck.setBackground(Color.WHITE);
        includeDetergentCheck.addActionListener(e -> updatePriceEstimate());
        form.add(includeDetergentCheck, gbc);

        // Item types (staff-defined catalog, e.g. Clothes/Hat/Undergarments -- optional, multi-select)
        gbc.gridy = 8;
        JLabel itemTypeLbl = new JLabel("Item Types (optional, Ctrl/Shift-click for multiple):");
        itemTypeLbl.setFont(labelFont);
        form.add(itemTypeLbl, gbc);

        gbc.gridy = 9;
        itemTypeList = new JList<>();
        itemTypeList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        itemTypeList.setVisibleRowCount(4);
        refreshItemTypeList();
        JScrollPane itemTypeScroll = new JScrollPane(itemTypeList);
        itemTypeScroll.setPreferredSize(new Dimension(200, 90));
        form.add(itemTypeScroll, gbc);

        // Drop-off date (today, fixed)
        gbc.gridy = 10;
        JLabel dateLbl = new JLabel("Drop-off Date: " + LocalDate.now().format(DATE_FMT));
        dateLbl.setFont(labelFont);
        form.add(dateLbl, gbc);

        // Price estimate
        gbc.gridy = 11;
        JLabel priceLbl = new JLabel("Estimated Total:");
        priceLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        form.add(priceLbl, gbc);

        gbc.gridy = 12;
        priceEstimateLabel = new JLabel();
        priceEstimateLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        priceEstimateLabel.setForeground(new Color(39, 174, 96));
        form.add(priceEstimateLabel, gbc);

        // Submit
        gbc.gridy = 13;
        JButton submitBtn = new JButton("Create Order");
        submitBtn.setBackground(PRIMARY);
        submitBtn.setForeground(Color.WHITE);
        submitBtn.setFocusPainted(false);
        submitBtn.addActionListener(this::onCreateOrder);
        form.add(submitBtn, gbc);

        updatePriceEstimate();
        outer.add(form, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildCustomersSection() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG);

        JLabel header = new JLabel("Customers");
        header.setFont(new Font("SansSerif", Font.BOLD, 20));
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

    private void updatePriceEstimate() {
        if (priceEstimateLabel == null) return;
        String service = (String) serviceCombo.getSelectedItem();
        double qty = ((Number) quantitySpinner.getValue()).doubleValue();
        double rate = PricingConfig.getRate(service);
        String unit = service.equals("Wash & Fold") ? "kilos" : "items";
        quantityHintLabel.setText("(" + unit + ")");
        double total = qty * rate
                + (includeSoapCheck != null && includeSoapCheck.isSelected() ? PricingConfig.getSoapFee() : 0.0)
                + (includeDetergentCheck != null && includeDetergentCheck.isSelected() ? PricingConfig.getDetergentFee() : 0.0);
        priceEstimateLabel.setText(String.format("\u20B1%.2f", total));
    }

    private void onCreateOrder(ActionEvent e) {
        Customer selected = (Customer) customerCombo.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                    "Please add a customer first.",
                    "No Customer", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String service = (String) serviceCombo.getSelectedItem();
        double qty = ((Number) quantitySpinner.getValue()).doubleValue();
        boolean includeSoap = includeSoapCheck.isSelected();
        boolean includeDetergent = includeDetergentCheck.isSelected();
        List<String> selectedItemTypes = itemTypeList.getSelectedValuesList();

        // In-memory order (drives the Dashboard/Orders tabs as before)
        Order order = DataStore.addOrder(selected, service, qty, LocalDate.now(), includeSoap, includeDetergent);
        order.setItemTypes(selectedItemTypes);

        // Persist to MySQL with a fresh claim token, then show the digital
        // receipt (status, dates, itemized breakdown, QR) for pickup.
        String claimToken = QRCodeUtil.newClaimToken();
        String itemTypesCsv = String.join(",", selectedItemTypes);
        int dbId = DatabaseManager.insertOrder(selected.getName(), selected.getPhone(), selected.getAddress(),
                service, qty, order.getPrice(), claimToken, includeSoap, includeDetergent, itemTypesCsv);

        if (dbId == -1) {
            JOptionPane.showMessageDialog(this,
                    "Order #" + order.getId() + " was created, but saving it to the database failed.\n"
                            + "Check the console for details -- the order won't be claimable until this is fixed.",
                    "Database Error", JOptionPane.WARNING_MESSAGE);
        } else {
            order.setDbId(dbId); // keeps the displayed/QR'd ID matching the real MySQL row
            showReceiptDialog(order.getDisplayId(), selected.getName(), selected.getPhone(), selected.getAddress(),
                    order.getStatus(), order.getDropOffDate(), order.getClaimByDate(), order.getItemTypes(),
                    order.getLineItems(), order.getPrice(), StatusServer.buildStatusUrl(claimToken));
        }

        refreshOrdersTable();
        refreshDashboard();
        jTabbedPane1.setSelectedIndex(2); // Orders & Claims
        includeSoapCheck.setSelected(false);
        includeDetergentCheck.setSelected(false);
        itemTypeList.clearSelection();
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

    /** Refreshes the New Order form's Item Type dropdown from PricingConfig's staff-managed catalog. */
    private void refreshItemTypeList() {
        if (itemTypeList == null) return;
        List<String> previouslySelected = itemTypeList.getSelectedValuesList();
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String item : PricingConfig.getItems()) {
            model.addElement(item);
        }
        itemTypeList.setModel(model);

        // Re-select whatever's still valid after the catalog changed
        List<Integer> indicesToSelect = new java.util.ArrayList<>();
        for (String prev : previouslySelected) {
            int idx = model.indexOf(prev);
            if (idx != -1) indicesToSelect.add(idx);
        }
        int[] indices = indicesToSelect.stream().mapToInt(Integer::intValue).toArray();
        itemTypeList.setSelectedIndices(indices);
    }

    // ---------------------------------------------------------------
    // MERGED TAB: ORDERS + CLAIMS
    // Top: full orders table, multi-select enabled, most fields directly
    // editable in place. Bottom: claim tools (phone-scan and file-scan)
    // plus the claims log, in a resizable split so both get real space.
    // ---------------------------------------------------------------
    private JPanel createOrdersAndClaimsPanel() {
        JPanel outer = new JPanel(new BorderLayout(10, 10));
        outer.setBackground(BG);
        outer.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel header = new JLabel("Orders & Claims");
        header.setFont(new Font("SansSerif", Font.BOLD, 22));
        outer.add(header, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildOrdersSection(), buildClaimsSection());
        split.setResizeWeight(0.6);
        split.setBackground(BG);
        split.setBorder(null);
        outer.add(split, BorderLayout.CENTER);

        return outer;
    }

    private JPanel buildOrdersSection() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG);

        JLabel label = new JLabel("All Orders (select multiple with Ctrl/Shift-click; "
                + "double-click a cell to edit Customer, Service, Qty, Status, or Price)");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(new Color(120, 120, 120));
        panel.add(label, BorderLayout.NORTH);

        ordersModel = new DefaultTableModel(
                new Object[]{"ID", "Customer", "Service", "Qty", "Drop-off", "Status", "Price"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                // Editable: Customer(1), Service(2), Qty(3), Status(5), Price(6).
                // Not editable: ID(0, primary key) and Drop-off(4, historical record).
                return col == 1 || col == 2 || col == 3 || col == 5 || col == 6;
            }
        };
        ordersTable = new JTable(ordersModel);
        ordersTable.setRowHeight(28);
        ordersTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JComboBox<String> statusEditorCombo = new JComboBox<>(Order.STATUSES);
        ordersTable.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(statusEditorCombo));

        JComboBox<String> serviceEditorCombo = new JComboBox<>(Order.SERVICE_TYPES);
        ordersTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(serviceEditorCombo));

        // Price column: right-aligned currency display, plain numeric underneath for editing
        DefaultTableCellRenderer priceRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                if (value instanceof Number) {
                    setText(String.format("\u20B1%.2f", ((Number) value).doubleValue()));
                }
                return c;
            }
        };
        ordersTable.getColumnModel().getColumn(6).setCellRenderer(priceRenderer);

        ordersModel.addTableModelListener(this::onOrdersTableEdited);

        panel.add(new JScrollPane(ordersTable), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBackground(BG);
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshOrdersTable());

        JButton deleteBtn = new JButton("Delete Selected");
        deleteBtn.addActionListener(e -> {
            int[] rows = ordersTable.getSelectedRows();
            if (rows.length == 0) {
                JOptionPane.showMessageDialog(this, "Select one or more orders to delete.");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete " + rows.length + " order(s)?", "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            // Convert view rows to model rows and resolve to Orders BEFORE
            // deleting any of them, since indices shift once removal starts.
            List<Order> toDelete = new java.util.ArrayList<>();
            for (int viewRow : rows) {
                int modelRow = ordersTable.convertRowIndexToModel(viewRow);
                int orderId = (int) ordersModel.getValueAt(modelRow, 0);
                Order order = DataStore.findOrderById(orderId);
                if (order != null) toDelete.add(order);
            }

            List<String> failed = new java.util.ArrayList<>();
            for (Order order : toDelete) {
                boolean dbOk = true;
                if (order.getDbId() != -1) {
                    dbOk = DatabaseManager.deleteOrder(order.getDbId());
                }
                if (dbOk) {
                    DataStore.removeOrder(order);
                } else {
                    failed.add("#" + order.getDisplayId());
                }
            }

            if (!failed.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Removed from the app, but the database delete failed for: "
                                + String.join(", ", failed)
                                + "\nCheck the console for the exact database error -- "
                                + "these orders are still in MySQL.",
                        "Database Delete Failed", JOptionPane.WARNING_MESSAGE);
            }
            refreshOrdersTable();
            refreshDashboard();
        });

        buttons.add(refreshBtn);
        buttons.add(deleteBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        refreshOrdersTable();
        return panel;
    }

    /**
     * Handles in-place edits to the Orders table: Customer name, Service
     * type, Quantity, Status, or Price. Service/Qty edits recalculate the
     * price automatically; editing Price directly is treated as an
     * intentional manual override instead. Every edit pushes the full
     * updated row to MySQL (or the local/testing store) via
     * DatabaseManager.updateOrder(), same pattern as the existing Status
     * column already used.
     */
    private void onOrdersTableEdited(TableModelEvent evt) {
        if (suppressOrdersTableEvents) return;
        if (evt.getType() != TableModelEvent.UPDATE) return;

        int col = evt.getColumn();
        if (col != 1 && col != 2 && col != 3 && col != 5 && col != 6) return;

        int row = evt.getFirstRow();
        if (row < 0 || row >= ordersModel.getRowCount()) return;

        int orderId = (int) ordersModel.getValueAt(row, 0);
        Order order = DataStore.findOrderById(orderId);
        if (order == null) return;

        try {
            switch (col) {
                case 1: // Customer name
                    String newName = String.valueOf(ordersModel.getValueAt(row, 1)).trim();
                    if (!newName.isEmpty()) {
                        order.getCustomer().setName(newName);
                    }
                    break;
                case 2: // Service type
                    order.setServiceType(String.valueOf(ordersModel.getValueAt(row, 2)));
                    order.recalculatePrice();
                    break;
                case 3: // Quantity
                    double newQty = Double.parseDouble(String.valueOf(ordersModel.getValueAt(row, 3)));
                    order.setQuantity(newQty);
                    order.recalculatePrice();
                    break;
                case 5: // Status
                    order.setStatus(String.valueOf(ordersModel.getValueAt(row, 5)));
                    break;
                case 6: // Price (manual override -- no recalculation)
                    double newPrice = Double.parseDouble(String.valueOf(ordersModel.getValueAt(row, 6)));
                    order.setPrice(newPrice);
                    break;
            }

            if (order.getDbId() != -1) {
                if (col == 5) {
                    DatabaseManager.updateStatus(order.getDbId(), order.getStatus());
                } else {
                    DatabaseManager.updateOrder(order.getDbId(), order.getCustomer().getName(),
                            order.getServiceType(), order.getQuantity(), order.getPrice());
                }
            }

            // Service/Qty edits change the price -- reflect that in the
            // table immediately without re-triggering this same listener.
            if (col == 2 || col == 3) {
                suppressOrdersTableEvents = true;
                ordersModel.setValueAt(order.getPrice(), row, 6);
                suppressOrdersTableEvents = false;
            }

            refreshDashboard();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "That column needs a number.", "Invalid Value",
                    JOptionPane.WARNING_MESSAGE);
            refreshOrdersTable();
        }
    }

    private void refreshOrdersTable() {
        suppressOrdersTableEvents = true;
        ordersModel.setRowCount(0);
        for (Order o : DataStore.getOrders()) {
            ordersModel.addRow(new Object[]{
                    o.getDisplayId(),
                    o.getCustomer().getName(),
                    o.getServiceType(),
                    o.getQuantity(),
                    o.getDropOffDate().format(DATE_FMT),
                    o.getStatus(),
                    o.getPrice()
            });
        }
        suppressOrdersTableEvents = false;
    }

    // ---------------------------------------------------------------
    // CLAIMS SECTION -- "Scan to Claim": staff load a photo of a customer's
    // QR code (or generate one for the customer to scan themselves), the
    // app decodes it, looks the token up in MySQL, marks the order claimed
    // + delivered there, and mirrors that onto the in-memory order too so
    // the Orders table and Dashboard stats update immediately. Every
    // attempt -- success, already-claimed, or not-found -- is added to the
    // Customer Claims table, most recent first.
    // ---------------------------------------------------------------
    private JPanel buildClaimsSection() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JPanel topPanels = new JPanel();
        topPanels.setLayout(new BoxLayout(topPanels, BoxLayout.Y_AXIS));
        topPanels.setBackground(BG);

        // --- Scan with phone: show a QR that claims the order the moment
        // a customer's phone opens it -- no staff scanning needed. ---
        JPanel phonePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        phonePanel.setBackground(Color.WHITE);
        phonePanel.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));

        JLabel phoneLabel = new JLabel("Scan QR Code with Phone to Claim -- Order ID:");
        phoneLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
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
        topPanels.add(Box.createVerticalStrut(6));

        // --- Staff-side: load a photo of a QR code to claim it directly. ---
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        actionPanel.setBackground(Color.WHITE);
        actionPanel.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));

        JButton scanBtn = new JButton("Scan QR Code to Claim...");
        scanBtn.setBackground(PRIMARY);
        scanBtn.setForeground(Color.BLACK);
        scanBtn.setFocusPainted(false);
        scanBtn.addActionListener(e -> onScanQrToClaim());
        actionPanel.add(scanBtn);

        claimStatusLabel = new JLabel(" ");
        claimStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        actionPanel.add(claimStatusLabel);

        topPanels.add(actionPanel);
        panel.add(topPanels, BorderLayout.NORTH);

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(Color.WHITE);
        tablePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(10, 10, 10, 10)));

        JLabel tableLabel = new JLabel("Customer Claims");
        tableLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        tablePanel.add(tableLabel, BorderLayout.NORTH);

        claimsModel = new DefaultTableModel(
                new Object[]{"Order ID", "Customer", "Service", "Price", "Result"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable claimsTable = new JTable(claimsModel);
        claimsTable.setRowHeight(26);
        tablePanel.add(new JScrollPane(claimsTable), BorderLayout.CENTER);

        panel.add(tablePanel, BorderLayout.CENTER);

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

    // ---------------------------------------------------------------
    // SETTINGS TAB
    // ---------------------------------------------------------------
    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel header = new JLabel("Settings");
        header.setFont(new Font("SansSerif", Font.BOLD, 22));
        panel.add(header, BorderLayout.NORTH);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(20, 20, 20, 20)));

        JCheckBox disableMysqlCheck = new JCheckBox("Disable MySQL (testing mode)");
        disableMysqlCheck.setFont(new Font("SansSerif", Font.BOLD, 14));
        disableMysqlCheck.setBackground(Color.WHITE);
        disableMysqlCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        disableMysqlCheck.setSelected(!DatabaseManager.isMysqlEnabled());

        JLabel explain = new JLabel("<html><body style='width:380px'>"
                + "When checked, orders and claims are kept only in memory for "
                + "this session -- nothing is read from or written to MySQL. "
                + "Useful for testing the app (order creation, QR codes, "
                + "claiming) without a working database connection. "
                + "Data created while this is on disappears when the app closes."
                + "</body></html>");
        explain.setFont(new Font("SansSerif", Font.PLAIN, 12));
        explain.setForeground(new Color(120, 120, 120));
        explain.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Sub-option: only meaningful once MySQL is disabled above. Instead
        // of the plain in-memory mode losing everything on close, this also
        // writes orders to a local file on disk, so the QR status-check and
        // claim pages keep working correctly (same as always) AND the data
        // survives an app restart -- no MySQL server needed at all.
        JCheckBox localSaveCheck = new JCheckBox("Save data locally to a file (survives app restart)");
        localSaveCheck.setFont(new Font("SansSerif", Font.PLAIN, 13));
        localSaveCheck.setBackground(Color.WHITE);
        localSaveCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        localSaveCheck.setBorder(new EmptyBorder(0, 22, 0, 0));
        localSaveCheck.setSelected(DatabaseManager.isLocalPersistenceEnabled());
        localSaveCheck.setEnabled(disableMysqlCheck.isSelected());

        JLabel localExplain = new JLabel("<html><body style='width:380px'>"
                + "Instead of losing everything on close, orders/claims are also "
                + "saved to a local file next to the app. Order creation, QR "
                + "codes, and claiming still work exactly the same -- they just "
                + "keep working after a restart too, with no database required."
                + "</body></html>");
        localExplain.setFont(new Font("SansSerif", Font.PLAIN, 12));
        localExplain.setForeground(new Color(120, 120, 120));
        localExplain.setAlignmentX(Component.LEFT_ALIGNMENT);
        localExplain.setBorder(new EmptyBorder(0, 22, 0, 0));

        JLabel statusLabel = new JLabel();
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        Runnable updateStatusLabel = () -> {
            if (DatabaseManager.isMysqlEnabled()) {
                statusLabel.setText("Currently: MySQL ENABLED (normal mode)");
                statusLabel.setForeground(new Color(39, 174, 96));
            } else if (DatabaseManager.isLocalPersistenceEnabled()) {
                statusLabel.setText("Currently: MySQL DISABLED -- saving locally (survives restart)");
                statusLabel.setForeground(new Color(41, 128, 185));
            } else {
                statusLabel.setText("Currently: MySQL DISABLED (testing mode -- nothing is saved)");
                statusLabel.setForeground(new Color(230, 126, 34));
            }
        };
        updateStatusLabel.run();

        disableMysqlCheck.addActionListener(e -> {
            boolean disabled = disableMysqlCheck.isSelected();
            DatabaseManager.setMysqlEnabled(!disabled);
            localSaveCheck.setEnabled(disabled);
            // Turning MySQL back on doesn't silently keep writing to the
            // local file -- uncheck the sub-option so its state stays honest.
            if (!disabled && localSaveCheck.isSelected()) {
                localSaveCheck.setSelected(false);
                DatabaseManager.setLocalPersistenceEnabled(false);
            }
            updateStatusLabel.run();
        });

        localSaveCheck.addActionListener(e -> {
            DatabaseManager.setLocalPersistenceEnabled(localSaveCheck.isSelected());
            updateStatusLabel.run();
        });

        card.add(disableMysqlCheck);
        card.add(Box.createVerticalStrut(8));
        card.add(explain);
        card.add(Box.createVerticalStrut(14));
        card.add(localSaveCheck);
        card.add(Box.createVerticalStrut(8));
        card.add(localExplain);
        card.add(Box.createVerticalStrut(12));
        card.add(statusLabel);

        // --- Account section: keep-signed-in / log out ---
        card.add(Box.createVerticalStrut(24));
        JSeparator separator = new JSeparator();
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        separator.setMaximumSize(new Dimension(380, 1));
        card.add(separator);
        card.add(Box.createVerticalStrut(16));

        JLabel accountHeader = new JLabel("Account");
        accountHeader.setFont(new Font("SansSerif", Font.BOLD, 14));
        accountHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(accountHeader);
        card.add(Box.createVerticalStrut(6));

        JLabel accountExplain = new JLabel("<html><body style='width:380px'>"
                + "You're kept signed in across restarts -- the app opens straight "
                + "to this dashboard instead of showing the login screen each time. "
                + "Log out below to require signing in again next launch."
                + "</body></html>");
        accountExplain.setFont(new Font("SansSerif", Font.PLAIN, 12));
        accountExplain.setForeground(new Color(120, 120, 120));
        accountExplain.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(accountExplain);
        card.add(Box.createVerticalStrut(10));

        JButton logoutBtn = new JButton("Log Out");
        logoutBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutBtn.setBackground(new Color(192, 57, 43));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> onLogout());
        card.add(logoutBtn);

        JPanel leftColumn = new JPanel();
        leftColumn.setLayout(new BoxLayout(leftColumn, BoxLayout.Y_AXIS));
        leftColumn.setBackground(BG);
        leftColumn.setAlignmentY(Component.TOP_ALIGNMENT);
        leftColumn.add(card);
        leftColumn.add(Box.createVerticalStrut(20));
        leftColumn.add(createPricingAndItemsCard());
        leftColumn.add(Box.createVerticalStrut(20));
        leftColumn.add(createBrandingCard());

        JPanel rightColumn = new JPanel();
        rightColumn.setLayout(new BoxLayout(rightColumn, BoxLayout.Y_AXIS));
        rightColumn.setBackground(BG);
        rightColumn.setAlignmentY(Component.TOP_ALIGNMENT);
        rightColumn.add(createDomainCard());

        JPanel columns = new JPanel();
        columns.setLayout(new BoxLayout(columns, BoxLayout.X_AXIS));
        columns.setBackground(BG);
        columns.add(leftColumn);
        columns.add(Box.createHorizontalStrut(20));
        columns.add(rightColumn);

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wrapper.setBackground(BG);
        wrapper.add(columns);

        JScrollPane scrollPane = new JScrollPane(wrapper);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Settings card (right column): the custom domain/base URL embedded in
     * every QR code for the web status/claim pages -- e.g. an ngrok domain
     * or a LAN IP:port. "Set Permanently" persists it via DomainConfig so
     * it survives an app restart, and keeps a short history of previously
     * used domains so staff can switch back without retyping.
     */
    private JPanel createDomainCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(20, 20, 20, 20)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setPreferredSize(new Dimension(320, 360));

        JLabel header = new JLabel("Web Status Domain");
        header.setFont(new Font("SansSerif", Font.BOLD, 14));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(header);
        card.add(Box.createVerticalStrut(6));

        JLabel explain = new JLabel("<html><body style='width:260px'>"
                + "The address embedded in every QR code (status + claim pages). "
                + "E.g. an ngrok domain like <i>your-name.ngrok-free.dev</i>, or a "
                + "LAN address like <i>192.168.1.50:8080</i>.</body></html>");
        explain.setFont(new Font("SansSerif", Font.PLAIN, 12));
        explain.setForeground(new Color(120, 120, 120));
        explain.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(explain);
        card.add(Box.createVerticalStrut(10));

        JLabel currentLabel = new JLabel("Current: " + DomainConfig.getCurrentDomain());
        currentLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        currentLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(currentLabel);
        card.add(Box.createVerticalStrut(8));

        JTextField domainField = new JTextField(DomainConfig.getCurrentDomain());
        domainField.setAlignmentX(Component.LEFT_ALIGNMENT);
        domainField.setMaximumSize(new Dimension(280, 28));
        card.add(domainField);
        card.add(Box.createVerticalStrut(8));

        JLabel domainStatus = new JLabel(" ");
        domainStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        domainStatus.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton setPermanentBtn = new JButton("Set Permanently");
        setPermanentBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        setPermanentBtn.setBackground(PRIMARY);
        setPermanentBtn.setForeground(Color.WHITE);
        setPermanentBtn.setFocusPainted(false);

        card.add(setPermanentBtn);
        card.add(Box.createVerticalStrut(4));
        card.add(domainStatus);
        card.add(Box.createVerticalStrut(16));

        JSeparator separator = new JSeparator();
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        separator.setMaximumSize(new Dimension(280, 1));
        card.add(separator);
        card.add(Box.createVerticalStrut(12));

        JLabel historyHeader = new JLabel("Previous Domains");
        historyHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
        historyHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(historyHeader);
        card.add(Box.createVerticalStrut(6));

        DefaultListModel<String> historyModel = new DefaultListModel<>();
        for (String d : DomainConfig.getHistory()) {
            historyModel.addElement(d);
        }
        JList<String> historyList = new JList<>(historyModel);
        historyList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        historyScroll.setMaximumSize(new Dimension(280, 130));
        historyScroll.setPreferredSize(new Dimension(280, 130));
        card.add(historyScroll);
        card.add(Box.createVerticalStrut(8));

        JButton useSelectedBtn = new JButton("Use Selected");
        useSelectedBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        useSelectedBtn.addActionListener(e -> {
            String selected = historyList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select a domain from the list first.");
                return;
            }
            domainField.setText(selected);
        });
        card.add(useSelectedBtn);

        // Wired after historyModel/domainField exist, so it can refresh both.
        setPermanentBtn.addActionListener(e -> {
            String domain = domainField.getText().trim();
            if (domain.isEmpty()) {
                domainStatus.setText("Enter a domain first.");
                domainStatus.setForeground(Color.RED);
                return;
            }
            StatusServer.setBaseUrl(domain);
            currentLabel.setText("Current: " + domain);
            historyModel.clear();
            for (String d : DomainConfig.getHistory()) {
                historyModel.addElement(d);
            }
            domainStatus.setText("Saved -- new QR codes will use this domain.");
            domainStatus.setForeground(new Color(39, 174, 96));
        });

        return card;
    }

    /**
     * Settings card: editable per-service rates + soap fee, and a
     * staff-managed catalog of item types (e.g. Clothes, Hat,
     * Undergarments) used as the optional "Item Type" dropdown on the New
     * Order form. Both persist via PricingConfig to laundry_pricing.properties.
     */
    private JPanel createPricingAndItemsCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(20, 20, 20, 20)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel pricingHeader = new JLabel("Service Prices");
        pricingHeader.setFont(new Font("SansSerif", Font.BOLD, 14));
        pricingHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(pricingHeader);
        card.add(Box.createVerticalStrut(6));

        JLabel pricingExplain = new JLabel("<html><body style='width:380px'>"
                + "Rates staff can edit here apply to the next order created "
                + "(existing orders keep whatever price they already had).</body></html>");
        pricingExplain.setFont(new Font("SansSerif", Font.PLAIN, 12));
        pricingExplain.setForeground(new Color(120, 120, 120));
        pricingExplain.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(pricingExplain);
        card.add(Box.createVerticalStrut(10));

        JPanel ratesGrid = new JPanel(new GridBagLayout());
        ratesGrid.setBackground(Color.WHITE);
        ratesGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints pgbc = new GridBagConstraints();
        pgbc.insets = new Insets(4, 4, 4, 10);
        pgbc.anchor = GridBagConstraints.WEST;

        JTextField washFoldField = new JTextField(String.valueOf(PricingConfig.getRate("Wash & Fold")), 8);
        JTextField dryCleanField = new JTextField(String.valueOf(PricingConfig.getRate("Dry Clean")), 8);
        JTextField ironOnlyField = new JTextField(String.valueOf(PricingConfig.getRate("Iron Only")), 8);
        JTextField soapFeeField = new JTextField(String.valueOf(PricingConfig.getSoapFee()), 8);
        JTextField detergentFeeField = new JTextField(String.valueOf(PricingConfig.getDetergentFee()), 8);
        JTextField claimWindowField = new JTextField(String.valueOf(PricingConfig.getClaimWindowDays()), 8);

        addPricingRow(ratesGrid, pgbc, 0, "Wash & Fold (per kilo):", washFoldField);
        addPricingRow(ratesGrid, pgbc, 1, "Dry Clean (per item):", dryCleanField);
        addPricingRow(ratesGrid, pgbc, 2, "Iron Only (per item):", ironOnlyField);
        addPricingRow(ratesGrid, pgbc, 3, "Soap add-on (flat fee):", soapFeeField);
        addPricingRow(ratesGrid, pgbc, 4, "Detergent add-on (flat fee):", detergentFeeField);
        addPricingRow(ratesGrid, pgbc, 5, "Claim window (days after drop-off):", claimWindowField);

        card.add(ratesGrid);
        card.add(Box.createVerticalStrut(8));

        JLabel pricingStatus = new JLabel(" ");
        pricingStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        pricingStatus.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton savePricesBtn = new JButton("Save Prices");
        savePricesBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        savePricesBtn.setBackground(PRIMARY);
        savePricesBtn.setForeground(Color.WHITE);
        savePricesBtn.setFocusPainted(false);
        savePricesBtn.addActionListener(e -> {
            try {
                PricingConfig.setRate("Wash & Fold", Double.parseDouble(washFoldField.getText().trim()));
                PricingConfig.setRate("Dry Clean", Double.parseDouble(dryCleanField.getText().trim()));
                PricingConfig.setRate("Iron Only", Double.parseDouble(ironOnlyField.getText().trim()));
                PricingConfig.setSoapFee(Double.parseDouble(soapFeeField.getText().trim()));
                PricingConfig.setDetergentFee(Double.parseDouble(detergentFeeField.getText().trim()));
                PricingConfig.setClaimWindowDays(Integer.parseInt(claimWindowField.getText().trim()));
                pricingStatus.setText("Saved.");
                pricingStatus.setForeground(new Color(39, 174, 96));
                if (includeSoapCheck != null) {
                    includeSoapCheck.setText("Include Soap (+\u20B1"
                            + String.format("%.2f", PricingConfig.getSoapFee()) + ")");
                }
                if (includeDetergentCheck != null) {
                    includeDetergentCheck.setText("Include Detergent (+\u20B1"
                            + String.format("%.2f", PricingConfig.getDetergentFee()) + ")");
                }
                updatePriceEstimate();
            } catch (NumberFormatException ex) {
                pricingStatus.setText("All prices must be numbers (claim window must be a whole number).");
                pricingStatus.setForeground(Color.RED);
            }
        });
        card.add(savePricesBtn);
        card.add(Box.createVerticalStrut(4));
        card.add(pricingStatus);

        // --- Items catalog ---
        card.add(Box.createVerticalStrut(20));
        JSeparator itemsSeparator = new JSeparator();
        itemsSeparator.setAlignmentX(Component.LEFT_ALIGNMENT);
        itemsSeparator.setMaximumSize(new Dimension(420, 1));
        card.add(itemsSeparator);
        card.add(Box.createVerticalStrut(16));

        JLabel itemsHeader = new JLabel("Item Types");
        itemsHeader.setFont(new Font("SansSerif", Font.BOLD, 14));
        itemsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(itemsHeader);
        card.add(Box.createVerticalStrut(6));

        JLabel itemsExplain = new JLabel("<html><body style='width:380px'>"
                + "Staff-defined catalog (e.g. Clothes, Hat, Undergarments) shown "
                + "as the optional \"Item Type\" dropdown when creating an order.</body></html>");
        itemsExplain.setFont(new Font("SansSerif", Font.PLAIN, 12));
        itemsExplain.setForeground(new Color(120, 120, 120));
        itemsExplain.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(itemsExplain);
        card.add(Box.createVerticalStrut(10));

        DefaultListModel<String> itemsListModel = new DefaultListModel<>();
        for (String item : PricingConfig.getItems()) {
            itemsListModel.addElement(item);
        }
        JList<String> itemsList = new JList<>(itemsListModel);
        itemsList.setVisibleRowCount(5);
        JScrollPane itemsScroll = new JScrollPane(itemsList);
        itemsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        itemsScroll.setMaximumSize(new Dimension(420, 120));
        itemsScroll.setPreferredSize(new Dimension(420, 120));
        card.add(itemsScroll);
        card.add(Box.createVerticalStrut(8));

        JPanel itemsAddRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        itemsAddRow.setBackground(Color.WHITE);
        itemsAddRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField newItemField = new JTextField(16);
        itemsAddRow.add(newItemField);

        JButton addItemBtn = new JButton("Add");
        addItemBtn.addActionListener(e -> {
            String name = newItemField.getText().trim();
            if (name.isEmpty()) return;
            PricingConfig.addItem(name);
            itemsListModel.clear();
            for (String item : PricingConfig.getItems()) {
                itemsListModel.addElement(item);
            }
            newItemField.setText("");
            refreshItemTypeList();
        });
        itemsAddRow.add(addItemBtn);

        JButton removeItemBtn = new JButton("Remove Selected");
        removeItemBtn.addActionListener(e -> {
            String selected = itemsList.getSelectedValue();
            if (selected == null) return;
            PricingConfig.removeItem(selected);
            itemsListModel.removeElement(selected);
            refreshItemTypeList();
        });
        itemsAddRow.add(removeItemBtn);

        card.add(itemsAddRow);

        return card;
    }

    /**
     * Settings card: laundry name and logo shown at the top of the digital
     * receipt (both the desktop dialog after creating an order, and the
     * matching web status page). Persisted via PricingConfig.
     */
    private JPanel createBrandingCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(20, 20, 20, 20)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel header = new JLabel("Receipt Branding");
        header.setFont(new Font("SansSerif", Font.BOLD, 14));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(header);
        card.add(Box.createVerticalStrut(6));

        JLabel explain = new JLabel("<html><body style='width:380px'>"
                + "Shown at the top of every digital receipt -- the dialog after "
                + "creating an order, and the matching web status page.</body></html>");
        explain.setFont(new Font("SansSerif", Font.PLAIN, 12));
        explain.setForeground(new Color(120, 120, 120));
        explain.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(explain);
        card.add(Box.createVerticalStrut(10));

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        nameRow.setBackground(Color.WHITE);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameRow.add(new JLabel("Laundry Name:"));
        JTextField nameField = new JTextField(PricingConfig.getLaundryName(), 20);
        nameRow.add(nameField);
        card.add(nameRow);
        card.add(Box.createVerticalStrut(10));

        JPanel logoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        logoRow.setBackground(Color.WHITE);
        logoRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel logoPreview = new JLabel();
        logoPreview.setPreferredSize(new Dimension(48, 48));
        refreshLogoPreview(logoPreview);
        logoRow.add(logoPreview);

        JButton chooseLogoBtn = new JButton("Choose Logo...");
        chooseLogoBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Image files", "png", "jpg", "jpeg"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                PricingConfig.setLogoPath(chooser.getSelectedFile().getAbsolutePath());
                refreshLogoPreview(logoPreview);
            }
        });
        logoRow.add(chooseLogoBtn);

        JButton clearLogoBtn = new JButton("Clear Logo");
        clearLogoBtn.addActionListener(e -> {
            PricingConfig.setLogoPath("");
            refreshLogoPreview(logoPreview);
        });
        logoRow.add(clearLogoBtn);

        card.add(logoRow);
        card.add(Box.createVerticalStrut(10));

        JLabel brandingStatus = new JLabel(" ");
        brandingStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        brandingStatus.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton saveBrandingBtn = new JButton("Save Branding");
        saveBrandingBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveBrandingBtn.setBackground(PRIMARY);
        saveBrandingBtn.setForeground(Color.WHITE);
        saveBrandingBtn.setFocusPainted(false);
        saveBrandingBtn.addActionListener(e -> {
            PricingConfig.setLaundryName(nameField.getText());
            brandingStatus.setText("Saved.");
            brandingStatus.setForeground(new Color(39, 174, 96));
        });
        card.add(saveBrandingBtn);
        card.add(Box.createVerticalStrut(4));
        card.add(brandingStatus);

        return card;
    }

    private void refreshLogoPreview(JLabel logoPreview) {
        String path = PricingConfig.getLogoPath();
        if (path.isBlank()) {
            logoPreview.setIcon(null);
            logoPreview.setText("(no logo)");
            return;
        }
        try {
            java.awt.Image img = ImageIO.read(new java.io.File(path));
            if (img != null) {
                logoPreview.setText(null);
                logoPreview.setIcon(new ImageIcon(img.getScaledInstance(48, 48, Image.SCALE_SMOOTH)));
            } else {
                logoPreview.setIcon(null);
                logoPreview.setText("(invalid)");
            }
        } catch (Exception ex) {
            logoPreview.setIcon(null);
            logoPreview.setText("(invalid)");
        }
    }

    private void addPricingRow(JPanel grid, GridBagConstraints gbc, int row, String label, JTextField field) {
        gbc.gridx = 0; gbc.gridy = row;
        JLabel l = new JLabel(label);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        grid.add(l, gbc);
        gbc.gridx = 1;
        grid.add(field, gbc);
    }

    private void onLogout() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Log out and return to the login screen?", "Confirm Log Out",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        SessionManager.clearSignedIn();
        StatusServer.stop();
        dispose();

        SwingUtilities.invokeLater(() -> {
            Login loginFrame = new Login();
            loginFrame.setVisible(true);
            loginFrame.setLocationRelativeTo(null);
        });
    }
}
