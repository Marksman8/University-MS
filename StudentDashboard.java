
import javax.swing.*;
import java.awt.*;

public class StudentDashboard extends JFrame {

    public StudentDashboard() {

        setTitle("University Management System - Student Dashboard");
        setSize(700, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());

        JLabel heading = new JLabel(
            "STUDENT DASHBOARD", SwingConstants.CENTER
        );
        heading.setFont(new Font("Arial", Font.BOLD, 24));
        heading.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));

        JPanel buttonPanel = new JPanel(new GridLayout(2, 2, 20, 20));
        buttonPanel.setBorder(
            BorderFactory.createEmptyBorder(30, 50, 30, 50)
        );

        JButton updateButton = new JButton("Update Student");
        JButton marksButton = new JButton("View Marks");
        JButton attendanceButton = new JButton("View Attendance");
        JButton logoutButton = new JButton("Logout");

        buttonPanel.add(updateButton);
        buttonPanel.add(marksButton);
        buttonPanel.add(attendanceButton);
        buttonPanel.add(logoutButton);

        updateButton.addActionListener(e -> new UpdateStudentUI());
        marksButton.addActionListener(e -> new ViewMarksUI());
        attendanceButton.addActionListener(e -> new ViewAttendanceUI());

        logoutButton.addActionListener(e -> {
            dispose();
            new StudentLoginUI();
        });

        mainPanel.add(heading, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.CENTER);

        add(mainPanel);
        setVisible(true);
    }

    public static void main(String[] args) {
        new StudentDashboard();
    }
}
