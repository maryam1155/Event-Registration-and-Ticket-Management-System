package com.mycompany.eventmanagementsystem;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class AdminReportsFrame extends JFrame {

    private JTable tblByEvent;
    private JTable tblByCategory;

    private DefaultTableModel modelByEvent;
    private DefaultTableModel modelByCategory;

    // Summary labels
    private JLabel lblTotalEvents;
    private JLabel lblTotalCapacity;
    private JLabel lblTotalRegistrations;
    private JLabel lblTotalWaitlist;
    private JLabel lblAvgOccupancy;

    public AdminReportsFrame() {
        setTitle("Event Reports & Analytics");
        setSize(900, 550);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initComponents();
        reloadAll();
    }

    private void initComponents() {
        JTabbedPane tabs = new JTabbedPane();

        // Tab 1: By Event 
        String[] eventCols = {
                "Event ID", "Title", "Category",
                "Capacity", "Seats Available",
                "Registrations", "Occupancy %", "Waitlist Count"
        };
        modelByEvent = new DefaultTableModel(eventCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        tblByEvent = new JTable(modelByEvent);
        tblByEvent.setAutoCreateRowSorter(true); // sortable
        tabs.addTab("By Event", new JScrollPane(tblByEvent));

        //  Tab 2: By Category 
        String[] catCols = {
                "Category",
                "Events",
                "Total Capacity",
                "Total Registrations",
                "Total Waitlist",
                "Occupancy %"
        };
        modelByCategory = new DefaultTableModel(catCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        tblByCategory = new JTable(modelByCategory);
        tblByCategory.setAutoCreateRowSorter(true); // sortable
        tabs.addTab("By Category", new JScrollPane(tblByCategory));

        //Tab 3: Summary 
        JPanel summaryPanel = new JPanel();
        summaryPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;

        lblTotalEvents = new JLabel("Total Events: 0");
        lblTotalCapacity = new JLabel("Total Capacity: 0");
        lblTotalRegistrations = new JLabel("Total Registrations: 0");
        lblTotalWaitlist = new JLabel("Total Waitlist Entries: 0");
        lblAvgOccupancy = new JLabel("Average Occupancy: 0.0%");

        gbc.gridx = 0; gbc.gridy = 0;
        summaryPanel.add(lblTotalEvents, gbc);
        gbc.gridy++;
        summaryPanel.add(lblTotalCapacity, gbc);
        gbc.gridy++;
        summaryPanel.add(lblTotalRegistrations, gbc);
        gbc.gridy++;
        summaryPanel.add(lblTotalWaitlist, gbc);
        gbc.gridy++;
        summaryPanel.add(lblAvgOccupancy, gbc);

        tabs.addTab("Summary", summaryPanel);

        add(tabs, BorderLayout.CENTER);

        // Bottom buttons 
        JPanel bottomPanel = new JPanel();
        JButton btnRefresh = new JButton("Refresh");
        JButton btnClose = new JButton("Close");

        btnRefresh.addActionListener(e -> reloadAll());
        btnClose.addActionListener(e -> dispose());

        bottomPanel.add(btnRefresh);
        bottomPanel.add(btnClose);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    
    private void reloadAll() {
        loadEventStats();
        loadCategoryStats();
        loadSummaryStats();
    }

    private void loadEventStats() {
        modelByEvent.setRowCount(0);

        
        String sql =
                "SELECT e.event_id, e.title, e.category, " +
                "       e.capacity, e.seats_available, " +
                "       COUNT(r.registration_id) AS registrations, " +
                "       (SELECT COUNT(*) FROM waitlist w " +
                "         WHERE w.event_id = e.event_id) AS waitlist_count " +
                "FROM events e " +
                "LEFT JOIN registrations r ON r.event_id = e.event_id " +
                "GROUP BY e.event_id, e.title, e.category, " +
                "         e.capacity, e.seats_available " +
                "ORDER BY e.event_date, e.event_time";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int capacity = rs.getInt("capacity");
                int registrations = rs.getInt("registrations");

                double occ = 0.0;
                if (capacity > 0) {
                    occ = (registrations * 100.0) / capacity;
                }

                Object[] row = {
                        rs.getInt("event_id"),
                        rs.getString("title"),
                        rs.getString("category"),
                        capacity,
                        rs.getInt("seats_available"),
                        registrations,
                        String.format("%.1f%%", occ),
                        rs.getInt("waitlist_count")
                };
                modelByEvent.addRow(row);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error loading event report:\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }


    private void loadCategoryStats() {
        modelByCategory.setRowCount(0);

        
        String sql =
                "SELECT e.category AS category, " +
                "       COUNT(DISTINCT e.event_id) AS num_events, " +
                "       SUM(e.capacity) AS total_capacity, " +
                "       COUNT(r.registration_id) AS total_registrations " +
                "FROM events e " +
                "LEFT JOIN registrations r ON r.event_id = e.event_id " +
                "GROUP BY e.category " +
                "ORDER BY total_registrations DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String category = rs.getString("category");
                int numEvents   = rs.getInt("num_events");
                int totalCap    = rs.getInt("total_capacity");
                int totalReg    = rs.getInt("total_registrations");

                // total waitlist for this category
                int totalWaitlist = 0;
                String waitSql =
                        "SELECT COUNT(*) AS cnt " +
                        "FROM waitlist w " +
                        "JOIN events e2 ON e2.event_id = w.event_id " +
                        "WHERE e2.category = ?";

                try (PreparedStatement psW = conn.prepareStatement(waitSql)) {
                    psW.setString(1, category);
                    try (ResultSet rsW = psW.executeQuery()) {
                        if (rsW.next()) {
                            totalWaitlist = rsW.getInt("cnt");
                        }
                    }
                }

                double occ = 0.0;
                if (totalCap > 0) {
                    occ = (totalReg * 100.0) / totalCap;
                }

                Object[] row = {
                        category,
                        numEvents,
                        totalCap,
                        totalReg,
                        totalWaitlist,
                        String.format("%.1f%%", occ)
                };
                modelByCategory.addRow(row);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error loading category report:\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

   
    private void loadSummaryStats() {

        int totalEvents = 0;
        int totalCapacity = 0;
        int totalRegistrations = 0;
        int totalWaitlist = 0;
        double avgOccupancy = 0.0;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return;

            // 1) Total events + total capacity
            String sqlEvents =
                    "SELECT COUNT(*) AS cnt, SUM(capacity) AS total_cap " +
                    "FROM events";
            try (PreparedStatement ps = conn.prepareStatement(sqlEvents);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalEvents = rs.getInt("cnt");
                    totalCapacity = rs.getInt("total_cap");
                }
            }

            // 2) Total registrations
            String sqlReg =
                    "SELECT COUNT(*) AS cnt FROM registrations";
            try (PreparedStatement ps = conn.prepareStatement(sqlReg);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalRegistrations = rs.getInt("cnt");
                }
            }

            // 3) Total waitlist
            String sqlW =
                    "SELECT COUNT(*) AS cnt FROM waitlist";
            try (PreparedStatement ps = conn.prepareStatement(sqlW);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalWaitlist = rs.getInt("cnt");
                }
            }

            // 4) Average occupancy across all events that have capacity
            String sqlOcc =
                    "SELECT AVG(1.0 * reg_count / capacity) AS avg_occ " +
                    "FROM (" +
                    "   SELECT e.event_id, e.capacity, " +
                    "          COUNT(r.registration_id) AS reg_count " +
                    "   FROM events e " +
                    "   LEFT JOIN registrations r ON r.event_id = e.event_id " +
                    "   WHERE e.capacity > 0 " +
                    "   GROUP BY e.event_id, e.capacity" +
                    ") sub";
            try (PreparedStatement ps = conn.prepareStatement(sqlOcc);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    avgOccupancy = rs.getDouble("avg_occ") * 100.0;
                }
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error loading summary stats:\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }

        // Update labels
        lblTotalEvents.setText("Total Events: " + totalEvents);
        lblTotalCapacity.setText("Total Capacity: " + totalCapacity);
        lblTotalRegistrations.setText("Total Registrations: " + totalRegistrations);
        lblTotalWaitlist.setText("Total Waitlist Entries: " + totalWaitlist);
        lblAvgOccupancy.setText(String.format("Average Occupancy: %.1f%%", avgOccupancy));
    }
}
