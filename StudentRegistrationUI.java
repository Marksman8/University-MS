
import javax.swing.*;
import java.awt.*;

public class StudentRegistrationUI extends JFrame {

    JTextField nameField, emailField, phoneField;
    JTextField departmentField, yearField, addressField;
    JPasswordField passwordField;

    public StudentRegistrationUI() {

        setTitle("University Management System - Student Registration");
        setSize(500, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(8, 2, 10, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        panel.add(new JLabel("Student Name:"));
        nameField = new JTextField();
        panel.add(nameField);

        panel.add(new JLabel("Email:"));
        emailField = new JTextField();
        panel.add(emailField);

        panel.add(new JLabel("Phone:"));
        phoneField = new JTextField();
        panel.add(phoneField);

        panel.add(new JLabel("Department:"));
        departmentField = new JTextField();
        panel.add(departmentField);

        panel.add(new JLabel("Admission Year:"));
        yearField = new JTextField();
        panel.add(yearField);

        panel.add(new JLabel("Address:"));
        addressField = new JTextField();
        panel.add(addressField);

        panel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        panel.add(passwordField);

        JButton registerButton = new JButton("Register");
        JButton clearButton = new JButton("Clear");

        panel.add(registerButton);
        panel.add(clearButton);

        registerButton.addActionListener(e ->
            JOptionPane.showMessageDialog(
                this, "Registration UI is working!"
            )
        );

        clearButton.addActionListener(e -> {
            nameField.setText("");
            emailField.setText("");
            phoneField.setText("");
            departmentField.setText("");
            yearField.setText("");
            addressField.setText("");
            passwordField.setText("");
        });

        add(panel);
        setVisible(true);
    }

    public static void main(String[] args) {
        new StudentRegistrationUI();
    }
}
