import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Scanner;

public class StudentRegistration {
public static void main(String[] args) {

    Scanner sc = new Scanner(System.in);

    System.out.print("Enter Student Name: ");
    String name = sc.nextLine();

    System.out.print("Enter Email: ");
    String email = sc.nextLine();

    System.out.print("Enter Phone: ");
    String phone = sc.nextLine();

    System.out.print("Enter Department: ");
    String department = sc.nextLine();

    System.out.print("Enter Admission Year: ");
    int year = sc.nextInt();
    sc.nextLine();

    System.out.print("Enter Address: ");
    String address = sc.nextLine();

    String url = "jdbc:mysql://localhost:3306/UniversityDB";
    String user = "root";
    String password = "mysql";

    String query = "INSERT INTO Student (StudentName, Email, Phone, Department, AdmissionYear, Address) VALUES (?, ?, ?, ?, ?, ?)";

    try {
        Connection con = DriverManager.getConnection(url, user, password);

        PreparedStatement ps = con.prepareStatement(query);

        ps.setString(1, name);
        ps.setString(2, email);
        ps.setString(3, phone);
        ps.setString(4, department);
        ps.setInt(5, year);
        ps.setString(6, address);

        ps.executeUpdate();

        System.out.println("Student Registered Successfully!");

        ps.close();
        con.close();
        sc.close();

    } catch (Exception e) {
        System.out.println("Registration Failed!");
        e.printStackTrace();
    }
 }

}
