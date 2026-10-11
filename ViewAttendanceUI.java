
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class ViewAttendanceUI extends JFrame {

    public ViewAttendanceUI() {

        setTitle("View Student Attendance");
        setSize(650, 350);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        String[] columns = {
            "Subject", "Total Classes", "Attended Classes", "Percentage"
        };

        DefaultTableModel model = new DefaultTableModel(columns, 0);
        JTable table = new JTable(model);

        add(new JScrollPane(table), BorderLayout.CENTER);

        JLabel label = new JLabel(
            "Student Attendance (Database connection pending)",
            SwingConstants.CENTER
        );
        add(label, BorderLayout.NORTH);

        setVisible(true);
    }
}
