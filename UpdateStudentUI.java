
import javax.swing.*;
import java.awt.*;

public class UpdateStudentUI extends JFrame {

    JTextField nameField, emailField, phoneField;
    JTextField departmentField, yearField, addressField;

    public UpdateStudentUI() {

        setTitle("Update Student Details");
        setSize(450, 450);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel(new GridLayout(7, 2, 10, 10));
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

        JButton updateButton = new JButton("Update");
        JButton clearButton = new JButton("Clear");

        panel.add(updateButton);
        panel.add(clearButton);

        updateButton.addActionListener(e ->
            JOptionPane.showMessageDialog(
                this, "Update UI is working! Database connection pending."
            )
        );

        clearButton.addActionListener(e -> {
            nameField.setText("");
            emailField.setText("");
            phoneField.setText("");
            departmentField.setText("");
            yearField.setText("");
            addressField.setText("");
        });

        add(panel);
        setVisible(true);
    }
}
