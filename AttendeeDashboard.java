package com.mycompany.eventmanagementsystem;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class AttendeeDashboard extends JFrame {

    private int attendeeId;
    private String attendeeUsername;
    private String attendeeFullName;

    private JTable tblEvents;
    private DefaultTableModel eventsModel;

    private JTextField txtCategoryFilter;
    private JTextField txtLocationFilter;
    private JTextField txtDateFilter;

    private JButton btnFilter, btnRegister, btnMyTickets, btnLogout;

    public AttendeeDashboard(int attendeeId, String username, String fullName) {
        this.attendeeId = attendeeId;
        this.attendeeUsername = username;
        this.attendeeFullName = fullName;

        setTitle("Attendee Dashboard - " + fullName);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 450);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initComponents();
        loadEvents(null, null, null); // load all events initially
    }

    
    private void initComponents() {

       
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        JLabel lblTitle = new JLabel("Browse & Register for Events");
        lblTitle.setFont(lblTitle.getFont().deriveFont(18f));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 6;
        topPanel.add(lblTitle, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1;

        gbc.gridx = 0;
        topPanel.add(new JLabel("Category:"), gbc);

        gbc.gridx = 1;
        txtCategoryFilter = new JTextField(10);
        topPanel.add(txtCategoryFilter, gbc);

        gbc.gridx = 2;
        topPanel.add(new JLabel("Location:"), gbc);

        gbc.gridx = 3;
        txtLocationFilter = new JTextField(10);
        topPanel.add(txtLocationFilter, gbc);

        gbc.gridx = 4;
        topPanel.add(new JLabel("Date (YYYY-MM-DD):"), gbc);

        gbc.gridx = 5;
        txtDateFilter = new JTextField(10);
        topPanel.add(txtDateFilter, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 6;
        gbc.anchor = GridBagConstraints.CENTER;

        btnFilter = new JButton("Apply Filter");
        btnFilter.addActionListener(e -> applyFilter());
        topPanel.add(btnFilter, gbc);

        add(topPanel, BorderLayout.NORTH);

        //  Events table 
        String[] columns = {
                "ID", "Title", "Category", "Location",
                "Date", "Time", "Capacity", "Available"
        };

        eventsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        tblEvents = new JTable(eventsModel);
        add(new JScrollPane(tblEvents), BorderLayout.CENTER);

        // ---------- Bottom buttons ----------
        JPanel bottomPanel = new JPanel();

        btnRegister = new JButton("Register for Selected Event");
        btnMyTickets = new JButton("My Tickets");
        btnLogout = new JButton("Logout");

        btnRegister.addActionListener(e -> registerForSelectedEvent());
        btnMyTickets.addActionListener(e -> new MyTicketsFrame(attendeeId).setVisible(true));

        btnLogout.addActionListener(e -> {
            dispose();
            new LoginFrame().setVisible(true);
        });

        bottomPanel.add(btnRegister);
        bottomPanel.add(btnMyTickets);
        bottomPanel.add(btnLogout);

        add(bottomPanel, BorderLayout.SOUTH);
    }

   
    private void applyFilter() {
        String category = txtCategoryFilter.getText().trim();
        String location = txtLocationFilter.getText().trim();
        String date     = txtDateFilter.getText().trim();

        loadEvents(category, location, date);
    }

    private void loadEvents(String category, String location, String date) {
        eventsModel.setRowCount(0);

        String sql = "SELECT event_id, title, category, location, " +
                     "event_date, event_time, capacity, seats_available " +
                     "FROM events";

        boolean hasCondition = false;

        if (category != null && !category.isEmpty()) {
            sql += (hasCondition ? " AND" : " WHERE") + " category LIKE ?";
            hasCondition = true;
        }
        if (location != null && !location.isEmpty()) {
            sql += (hasCondition ? " AND" : " WHERE") + " location LIKE ?";
            hasCondition = true;
        }
        if (date != null && !date.isEmpty()) {
            sql += (hasCondition ? " AND" : " WHERE") + " event_date = ?";
            hasCondition = true;
        }

        sql += " ORDER BY event_date, event_time";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int paramIndex = 1;

            if (category != null && !category.isEmpty()) {
                ps.setString(paramIndex++, "%" + category + "%");
            }
            if (location != null && !location.isEmpty()) {
                ps.setString(paramIndex++, "%" + location + "%");
            }
            if (date != null && !date.isEmpty()) {
                ps.setString(paramIndex++, date);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object[] row = {
                            rs.getInt("event_id"),
                            rs.getString("title"),
                            rs.getString("category"),
                            rs.getString("location"),
                            rs.getString("event_date"),
                            rs.getString("event_time"),
                            rs.getInt("capacity"),
                            rs.getInt("seats_available")
                    };
                    eventsModel.addRow(row);
                }
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error loading events:\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }


    private void registerForSelectedEvent() {
        int row = tblEvents.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(
                    this,
                    "Please select an event to register.",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        int eventId = (Integer) eventsModel.getValueAt(row, 0);
        int seatsAvailable = (Integer) eventsModel.getValueAt(row, 7);

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return;

            //CASE 1: EVENT IS FULL → WAITLIST 
            if (seatsAvailable <= 0) {
                int choice = JOptionPane.showConfirmDialog(
                        this,
                        "This event is full.\nWould you like to join the waitlist?",
                        "Event Full",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }

                // Check if already on waitlist
                String waitCheckSql =
                        "SELECT COUNT(*) AS cnt FROM waitlist " +
                        "WHERE user_id = ? AND event_id = ?";

                try (PreparedStatement psCheck = conn.prepareStatement(waitCheckSql)) {
                    psCheck.setInt(1, attendeeId);
                    psCheck.setInt(2, eventId);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next() && rs.getInt("cnt") > 0) {
                            JOptionPane.showMessageDialog(
                                    this,
                                    "You are already on the waitlist for this event.",
                                    "Waitlist",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                            return;
                        }
                    }
                }

                // Add to waitlist
                String waitInsertSql =
                        "INSERT INTO waitlist (user_id, event_id, request_date) " +
                        "VALUES (?, ?, datetime('now'))";

                try (PreparedStatement psWait = conn.prepareStatement(waitInsertSql)) {
                    psWait.setInt(1, attendeeId);
                    psWait.setInt(2, eventId);
                    psWait.executeUpdate();
                }

                JOptionPane.showMessageDialog(
                        this,
                        "You have been added to the waitlist for this event.",
                        "Waitlist",
                        JOptionPane.INFORMATION_MESSAGE
                );

                return; // do not continue normal registration
            }

            // CASE 2: EVENT HAS SEATS → NORMAL REGISTRATION 

            // 1) Prevent duplicate registration
            String checkSql =
                    "SELECT COUNT(*) AS cnt FROM registrations " +
                    "WHERE user_id = ? AND event_id = ?";

            try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                psCheck.setInt(1, attendeeId);
                psCheck.setInt(2, eventId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next() && rs.getInt("cnt") > 0) {
                        JOptionPane.showMessageDialog(
                                this,
                                "You are already registered for this event.",
                                "Already Registered",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                        return;
                    }
                }
            }

            conn.setAutoCommit(false); // start transaction

            // 2) Insert registration
            String insertRegSql =
                    "INSERT INTO registrations (user_id, event_id, registration_date, status) " +
                    "VALUES (?, ?, datetime('now'), 'CONFIRMED')";

            int registrationId;
            try (PreparedStatement psReg = conn.prepareStatement(insertRegSql)) {
                psReg.setInt(1, attendeeId);
                psReg.setInt(2, eventId);
                psReg.executeUpdate();
            }

            // 3) Get last inserted registration_id
            try (Statement st = conn.createStatement();
                 ResultSet rsLast = st.executeQuery("SELECT last_insert_rowid() AS last_id")) {
                rsLast.next();
                registrationId = rsLast.getInt("last_id");
            }

            // 4) Generate ticket ID and insert ticket
            String ticketId = "T" + registrationId + "E" + eventId;

            String insertTicketSql =
                    "INSERT INTO tickets (ticket_id, registration_id) VALUES (?, ?)";

            try (PreparedStatement psTicket = conn.prepareStatement(insertTicketSql)) {
                psTicket.setString(1, ticketId);
                psTicket.setInt(2, registrationId);
                psTicket.executeUpdate();
            }

            // 5) Decrease seats_available
            String updateSeatsSql =
                    "UPDATE events SET seats_available = seats_available - 1 " +
                    "WHERE event_id = ? AND seats_available > 0";

            try (PreparedStatement psSeats = conn.prepareStatement(updateSeatsSql)) {
                psSeats.setInt(1, eventId);
                int updated = psSeats.executeUpdate();
                if (updated == 0) {
                    conn.rollback();
                    conn.setAutoCommit(true);
                    JOptionPane.showMessageDialog(
                            this,
                            "Event just became full. Please try another event.",
                            "Event Full",
                            JOptionPane.WARNING_MESSAGE
                    );
                    return;
                }
            }

            conn.commit();
            conn.setAutoCommit(true);

            JOptionPane.showMessageDialog(
                    this,
                    "Registration successful!\nYour ticket ID is: " + ticketId,
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE
            );

            // reload events to refresh seats_available
            applyFilter();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error during registration:\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
