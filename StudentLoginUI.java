
import javax.swing.*;
import java.awt.*;

public class StudentLoginUI extends JFrame {

    JTextField emailField;
    JPasswordField passwordField;

    public StudentLoginUI() {

        setTitle("University Management System - Student Login");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(30, 25, 30, 25));

        panel.add(new JLabel("Email:"));
        emailField = new JTextField();
        panel.add(emailField);

        panel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        panel.add(passwordField);

        JButton loginButton = new JButton("Login");
        JButton clearButton = new JButton("Clear");

        panel.add(loginButton);
        panel.add(clearButton);

        loginButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(
                this, "Login UI is working!"
            );
        });

        clearButton.addActionListener(e -> {
            emailField.setText("");
            passwordField.setText("");
        });

        add(panel);
        setVisible(true);
    }

    public static void main(String[] args) {
        new StudentLoginUI();
    }
}
