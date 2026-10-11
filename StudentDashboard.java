

import javax.swing.*;
import java.awt.*;

public class StudentDashboard extends JFrame {

    public StudentDashboard() {

        setTitle("University Management System - Student Dashboard");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.WHITE);

        JLabel heading = new JLabel(
            "STUDENT DASHBOARD",
            SwingConstants.CENTER
        );

        heading.setFont(new Font("Arial", Font.BOLD, 26));
        heading.setForeground(new Color(25, 65, 120));
        heading.setBorder(
            BorderFactory.createEmptyBorder(25, 10, 20, 10)
        );

        JPanel buttonPanel = new JPanel(
            new GridLayout(3, 2, 20, 20)
        );

        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.setBorder(
            BorderFactory.createEmptyBorder(30, 50, 30, 50)
        );

        JButton registrationButton =
            new JButton("Student Registration");

        JButton updateButton =
            new JButton("Update Student");

        JButton marksButton =
            new JButton("View Marks");

        JButton attendanceButton =
            new JButton("View Attendance");

        JButton quizButton =
            new JButton("Quiz");

        JButton logoutButton =
            new JButton("Logout");

        JButton[] buttons = {
            registrationButton,
            updateButton,
            marksButton,
            attendanceButton,
            quizButton,
            logoutButton
        };

        for (JButton button : buttons) {
            button.setFont(new Font("Arial", Font.BOLD, 16));
            button.setBackground(new Color(40, 100, 180));
            button.setForeground(Color.WHITE);
            button.setFocusPainted(false);
        }

        buttonPanel.add(registrationButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(marksButton);
        buttonPanel.add(attendanceButton);
        buttonPanel.add(quizButton);
        buttonPanel.add(logoutButton);

        registrationButton.addActionListener(e -> {
            new StudentRegistrationUI();
        });

        updateButton.addActionListener(e -> {
            new UpdateStudentUI();
        });

        marksButton.addActionListener(e -> {
            new ViewMarksUI();
        });

        attendanceButton.addActionListener(e -> {
            new ViewAttendanceUI();
        });

        quizButton.addActionListener(e -> {
            new Quiz(
                10,
                "Java",
                new String[]{"A", "B", "C", "D"},
                1
            );
        });

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
