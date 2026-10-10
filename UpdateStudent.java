
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;

public class UpdateStudent {
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

            ResultSet rs = check.executeQuery();

            if (rs.next()) {

                System.out.print("Enter New Phone: ");
                String phone = sc.nextLine();

                System.out.print("Enter New Address: ");
                String address = sc.nextLine();

                String query = "UPDATE Student SET Phone = ?, Address = ? WHERE StudentID = ?";
                PreparedStatement ps = con.prepareStatement(query);

                ps.setString(1, phone);
                ps.setString(2, address);
                ps.setInt(3, id);

                ps.executeUpdate();

                System.out.println("Profile Updated Successfully!");

                ps.close();

            } else {
                System.out.println("Invalid Student ID or Password!");
            }

            rs.close();
            check.close();
            con.close();
            sc.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

