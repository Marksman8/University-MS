import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class TestConnection {
public static void main(String[] args) {

    String url = "jdbc:mysql://localhost:3306/UniversityDB";
    String user = "root";
    String password = "mysql";

    try {
        Connection con = DriverManager.getConnection(url, user, password);

        System.out.println("Database connected successfully!");

        con.close();

    } catch (SQLException e) {
        System.out.println("Connection failed!");
        e.printStackTrace();
    }
}

}