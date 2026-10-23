
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;

public class ViewMarks {
    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.print("Enter Student ID: ");
        int id = sc.nextInt();
        sc.nextLine();

        System.out.print("Enter Password: ");
        String studentPassword = sc.nextLine();

        String url = "jdbc:mysql://localhost:3306/UniversityDB";
        String user = "root";
        String dbPassword = "mysql";

        try {
            Connection con = DriverManager.getConnection(url, user, dbPassword);

            String checkQuery = "SELECT StudentID FROM Student WHERE StudentID = ? AND Password = ?";
            PreparedStatement check = con.prepareStatement(checkQuery);

            check.setInt(1, id);
            check.setString(2, studentPassword);

            ResultSet login = check.executeQuery();

            if (login.next()) {

                String query = "SELECT SubjectName, MarksObtained, TotalMarks FROM Marks WHERE StudentID = ?";
                PreparedStatement ps = con.prepareStatement(query);

                ps.setInt(1, id);

                ResultSet rs = ps.executeQuery();

                System.out.println("\n--- Student Marks ---");

                boolean found = false;

                while (rs.next()) {
                    found = true;
                    System.out.println("Subject: " + rs.getString("SubjectName"));
                    System.out.println("Marks: " + rs.getInt("MarksObtained") + "/" + rs.getInt("TotalMarks"));
                    System.out.println("----------------------");
                }

                if (!found) {
                    System.out.println("No marks available.");
                }

                rs.close();
                ps.close();

            } else {
                System.out.println("Invalid Student ID or Password!");
            }

            login.close();
            check.close();
            con.close();
            sc.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
