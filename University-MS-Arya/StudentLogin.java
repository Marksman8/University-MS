
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;

public class StudentLogin {
    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.print("Enter Student ID: ");
        int id = sc.nextInt();
        sc.nextLine();

        System.out.print("Enter Password: ");
        String password = sc.nextLine();

        String url = "jdbc:mysql://localhost:3306/UniversityDB";
        String user = "root";
        String dbPassword = "mysql";

        String query = "SELECT StudentName FROM Student WHERE StudentID = ? AND Password = ?";

        try {
            Connection con = DriverManager.getConnection(url, user, dbPassword);

            PreparedStatement ps = con.prepareStatement(query);

            ps.setInt(1, id);
            ps.setString(2, password);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                System.out.println("Login Successful!");
                System.out.println("Welcome, " + rs.getString("StudentName"));
            } else {
                System.out.println("Invalid Student ID or Password!");
            }

            rs.close();
            ps.close();
            con.close();
            sc.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

