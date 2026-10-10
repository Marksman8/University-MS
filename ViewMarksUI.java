
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class ViewMarksUI extends JFrame {

    public ViewMarksUI() {

        setTitle("View Student Marks");
        setSize(600, 350);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        String[] columns = {
            "Subject", "Marks Obtained", "Total Marks"
        };

        DefaultTableModel model = new DefaultTableModel(columns, 0);
        JTable table = new JTable(model);

        add(new JScrollPane(table), BorderLayout.CENTER);

        JLabel label = new JLabel(
            "Student Marks (Database connection pending)",
            SwingConstants.CENTER
        );
        add(label, BorderLayout.NORTH);

        setVisible(true);
    }
}
