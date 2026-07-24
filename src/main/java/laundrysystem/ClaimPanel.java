package laundrysystem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;

/**
 * "Claim" tab: staff load a photo of a customer's QR code, the app decodes
 * it, looks the token up in MySQL, and marks the order as claimed +
 * delivered. A successful claim is also logged to Google Sheets.
 *
 * Add this as a tab in MainDashboard.buildTabs():
 *     jTabbedPane1.addTab("Claim", new ClaimPanel());
 */
public class ClaimPanel extends JPanel {

    private static final Color BG = new Color(245, 247, 250);

    private JLabel statusLabel;
    private DefaultTableModel recentClaimsModel;

    public ClaimPanel() {
        setLayout(new BorderLayout(15, 15));
        setBackground(BG);
        setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel header = new JLabel("Scan to Claim");
        header.setFont(new Font("SansSerif", Font.BOLD, 22));
        add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(15, 15));
        center.setBackground(BG);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        actionPanel.setBackground(Color.WHITE);
        actionPanel.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));

        JButton loadBtn = new JButton("Load QR Image to Claim...");
        loadBtn.addActionListener(e -> onLoadQrImage());
        actionPanel.add(loadBtn);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        actionPanel.add(statusLabel);

        center.add(actionPanel, BorderLayout.NORTH);

        recentClaimsModel = new DefaultTableModel(
                new Object[]{"Order ID", "Customer", "Service", "Price", "Result"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable recentClaimsTable = new JTable(recentClaimsModel);
        recentClaimsTable.setRowHeight(26);

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(Color.WHITE);
        tablePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220)),
                new EmptyBorder(10, 10, 10, 10)));
        JLabel tableLabel = new JLabel("Recent Claim Attempts");
        tableLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        tablePanel.add(tableLabel, BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(recentClaimsTable), BorderLayout.CENTER);

        center.add(tablePanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    private void onLoadQrImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select QR code image");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Image files", "png", "jpg", "jpeg"));

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File selected = chooser.getSelectedFile();

        try {
            String scanned = QRCodeUtil.decode(selected);
            if (scanned == null) {
                setStatus("No QR code found in that image.", Color.RED);
                return;
            }
            String token = QRCodeUtil.extractTokenFromScannedContent(scanned);
            processClaim(token);
        } catch (Exception ex) {
            setStatus("Couldn't read that image: " + ex.getMessage(), Color.RED);
        }
    }

    private void processClaim(String token) {
        DatabaseManager.OrderRecord order = DatabaseManager.findByToken(token);
        if (order == null) {
            setStatus("QR code not recognized -- no matching order.", Color.RED);
            return;
        }
        if (order.claimed) {
            setStatus("Order #" + order.id + " was already claimed.", new Color(230, 126, 34));
            addRow(order, "Already claimed");
            return;
        }

        boolean success = DatabaseManager.markClaimed(order.id);
        if (success) {
            setStatus("Order #" + order.id + " (" + order.customerName + ") claimed successfully.",
                    new Color(39, 174, 96));
            addRow(order, "Claimed");
            // Sheets sync failure won't block the claim -- see GoogleSheetsSync.logClaim
            GoogleSheetsSync.logClaim(order.id, order.customerName, order.serviceType, order.price);
        } else {
            setStatus("Could not claim order #" + order.id + " (already claimed by someone else?).",
                    Color.RED);
            addRow(order, "Failed");
        }
    }

    private void addRow(DatabaseManager.OrderRecord order, String outcome) {
        recentClaimsModel.insertRow(0, new Object[]{
                order.id, order.customerName, order.serviceType,
                String.format("\u20B1%.2f", order.price), outcome
        });
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }
}
