package com.mycompany.eventmanagementsystem;

import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class AddEventFrame extends JFrame {

    private JTextField txtTitle;
    private JTextField txtCategory;
    private JTextField txtLocation;
    private JTextField txtDate;
    private JTextField txtTime;
    private JTextField txtCapacity;

    private JButton btnSave;
    private JButton btnCancel;

    private boolean editMode = false;
    private int eventId = -1;   

    public AddEventFrame() {
        
        setTitle("Add New Event");
        setupFrame();
    }

   
    public AddEventFrame(int eventId,
                         String title,
                         String category,
                         String location,
                         String date,
                         String time,
                         int capacity) {

        this.editMode = true;
        this.eventId = eventId;

        setTitle("Edit Event - ID " + eventId);
        setupFrame();

        // pre-fill fields
        txtTitle.setText(title);
        txtCategory.setText(category);
        txtLocation.setText(location);
        txtDate.setText(date);
        txtTime.setText(time);
        txtCapacity.setText(String.valueOf(capacity));
    }

   
    private void setupFrame() {
        setSize(450, 320);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        txtTitle    = new JTextField(20);
        txtCategory = new JTextField(20);
        txtLocation = new JTextField(20);
        txtDate     = new JTextField(20);  
        txtTime     = new JTextField(20); 
        txtCapacity = new JTextField(20);

        int row = 0;

        // Title
        gbc.gridx = 0; gbc.gridy = row;
        form.add(new JLabel("Title:"), gbc);
        gbc.gridx = 1;
        form.add(txtTitle, gbc);
        row++;

        // Category
        gbc.gridx = 0; gbc.gridy = row;
        form.add(new JLabel("Category:"), gbc);
        gbc.gridx = 1;
        form.add(txtCategory, gbc);
        row++;

        // Location
        gbc.gridx = 0; gbc.gridy = row;
        form.add(new JLabel("Location:"), gbc);
        gbc.gridx = 1;
        form.add(txtLocation, gbc);
        row++;

        // Date
        gbc.gridx = 0; gbc.gridy = row;
        form.add(new JLabel("Date:"), gbc);
        gbc.gridx = 1;
        form.add(txtDate, gbc);
        row++;

        // Time
        gbc.gridx = 0; gbc.gridy = row;
        form.add(new JLabel("Time:"), gbc);
        gbc.gridx = 1;
        form.add(txtTime, gbc);
        row++;

        // Capacity
        gbc.gridx = 0; gbc.gridy = row;
        form.add(new JLabel("Capacity:"), gbc);
        gbc.gridx = 1;
        form.add(txtCapacity, gbc);
        row++;

        add(form, BorderLayout.CENTER);

        // Buttons
        JPanel bottom = new JPanel();
        btnSave = new JButton(editMode ? "Save Changes" : "Add Event");
        btnCancel = new JButton("Cancel");

        btnSave.addActionListener(e -> onSave());
        btnCancel.addActionListener(e -> dispose());

        bottom.add(btnSave);
        bottom.add(btnCancel);

        add(bottom, BorderLayout.SOUTH);
    }

    
    private void onSave() {

        String title    = txtTitle.getText().trim();
        String category = txtCategory.getText().trim();
        String location = txtLocation.getText().trim();
        String date     = txtDate.getText().trim();
        String time     = txtTime.getText().trim();
        String capStr   = txtCapacity.getText().trim();

        if (title.isEmpty() || category.isEmpty() || location.isEmpty()
                || date.isEmpty() || time.isEmpty() || capStr.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Please fill in all fields.",
                    "Validation Error",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        int capacity;
        try {
            capacity = Integer.parseInt(capStr);
            if (capacity <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Capacity must be a positive integer.",
                    "Validation Error",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        if (!editMode) {
            addNewEvent(title, category, location, date, time, capacity);
        } else {
            updateExistingEvent(title, category, location, date, time, capacity);
        }
    }

    
    private void addNewEvent(String title, String category, String location,
                             String date, String time, int capacity) {

        String sql = "INSERT INTO events " +
                "(title, category, location, event_date, event_time, capacity, seats_available) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, title);
            ps.setString(2, category);
            ps.setString(3, location);
            ps.setString(4, date);
            ps.setString(5, time);
            ps.setInt(6, capacity);
            ps.setInt(7, capacity); 

            ps.executeUpdate();

            JOptionPane.showMessageDialog(
                    this,
                    "Event added successfully.",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE
            );

            dispose();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error adding event:\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    
    private void updateExistingEvent(String title, String category, String location,
                                     String date, String time, int newCapacity) {

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return;

            conn.setAutoCommit(false);

            // --- 1. Get current capacity + seats_available ---
            int oldCapacity;
            int oldSeats;

            String selectSql = "SELECT capacity, seats_available FROM events WHERE event_id = ?";
            try (PreparedStatement psSel = conn.prepareStatement(selectSql)) {
                psSel.setInt(1, eventId);
                try (ResultSet rs = psSel.executeQuery()) {
                    if (!rs.next()) {
                        conn.setAutoCommit(true);
                        JOptionPane.showMessageDialog(
                                this,
                                "Event not found.",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                        return;
                    }
                    oldCapacity = rs.getInt("capacity");
                    oldSeats    = rs.getInt("seats_available");
                }
            }

            
            if (newCapacity < oldCapacity) {
                conn.setAutoCommit(true); 
                JOptionPane.showMessageDialog(
                        this,
                        "You cannot decrease the capacity of an existing event.\n" +
                        "You may only increase it (current capacity: " + oldCapacity + ").",
                        "Invalid Capacity",
                        JOptionPane.WARNING_MESSAGE
                );
            return;
            }

          
            int diff = newCapacity - oldCapacity;         
            int newSeatsAvailable = oldSeats + diff;

            if (newSeatsAvailable < 0) {
                newSeatsAvailable = 0;
            }
            if (newSeatsAvailable > newCapacity) {
                newSeatsAvailable = newCapacity;
            }

           
            String updateSql =
                    "UPDATE events SET title = ?, category = ?, location = ?, " +
                    "event_date = ?, event_time = ?, capacity = ?, seats_available = ? " +
                    "WHERE event_id = ?";

            try (PreparedStatement psUpd = conn.prepareStatement(updateSql)) {
                psUpd.setString(1, title);
                psUpd.setString(2, category);
                psUpd.setString(3, location);
                psUpd.setString(4, date);
                psUpd.setString(5, time);
                psUpd.setInt(6, newCapacity);
                psUpd.setInt(7, newSeatsAvailable);
                psUpd.setInt(8, eventId);
                psUpd.executeUpdate();
            }

           
            promoteWaitlistedAttendees(conn, eventId);

            conn.commit();
            conn.setAutoCommit(true);

            JOptionPane.showMessageDialog(
                    this,
                    "Event updated successfully.",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE
            );
            dispose();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error updating event:\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }


    public static void promoteWaitlistedAttendees(Connection conn, int eventId) throws SQLException {

        while (true) {

            // 1) Check available seats
            int seatsAvailable;
            try (PreparedStatement psSeats = conn.prepareStatement(
                    "SELECT seats_available, capacity FROM events WHERE event_id = ?")) {
                psSeats.setInt(1, eventId);
                try (ResultSet rs = psSeats.executeQuery()) {
                    if (!rs.next()) return;
                    seatsAvailable = rs.getInt("seats_available");
                    int cap = rs.getInt("capacity");
                    if (seatsAvailable <= 0 || seatsAvailable > cap) {
                        return;
                    }
                }
            }

            if (seatsAvailable <= 0) return;

            // 2) Get next person on waitlist (FIFO)
            int waitlistId;
            int userId;

            String waitSql =
                    "SELECT waitlist_id, user_id " +
                    "FROM waitlist " +
                    "WHERE event_id = ? " +
                    "ORDER BY request_date, waitlist_id " +
                    "LIMIT 1";

            try (PreparedStatement psWait = conn.prepareStatement(waitSql)) {
                psWait.setInt(1, eventId);
                try (ResultSet rsW = psWait.executeQuery()) {
                    if (!rsW.next()) {
                        // no one on waitlist
                        return;
                    }
                    waitlistId = rsW.getInt("waitlist_id");
                    userId     = rsW.getInt("user_id");
                }
            }

            // 3) Insert into registrations as CONFIRMED
            int registrationId;
            String insertReg =
                    "INSERT INTO registrations (user_id, event_id, registration_date, status) " +
                    "VALUES (?, ?, datetime('now'), 'CONFIRMED')";

            try (PreparedStatement psReg = conn.prepareStatement(insertReg)) {
                psReg.setInt(1, userId);
                psReg.setInt(2, eventId);
                psReg.executeUpdate();
            }

            try (Statement st = conn.createStatement();
                 ResultSet rsLast = st.executeQuery("SELECT last_insert_rowid() AS last_id")) {
                rsLast.next();
                registrationId = rsLast.getInt("last_id");
            }

            // 4) Create ticket
            String ticketId = "T" + registrationId + "E" + eventId;
            try (PreparedStatement psTicket = conn.prepareStatement(
                    "INSERT INTO tickets (ticket_id, registration_id) VALUES (?, ?)")) {
                psTicket.setString(1, ticketId);
                psTicket.setInt(2, registrationId);
                psTicket.executeUpdate();
            }

            // 5) Remove from waitlist
            try (PreparedStatement psDelW = conn.prepareStatement(
                    "DELETE FROM waitlist WHERE waitlist_id = ?")) {
                psDelW.setInt(1, waitlistId);
                psDelW.executeUpdate();
            }

            // 6) Decrease seats_available
            try (PreparedStatement psUpdSeats = conn.prepareStatement(
                    "UPDATE events SET seats_available = seats_available - 1 WHERE event_id = ?")) {
                psUpdSeats.setInt(1, eventId);
                psUpdSeats.executeUpdate();
            }

            // loop again if still seats + still waitlist
        }
    }
}
