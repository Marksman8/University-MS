
import java.sql.*;
import java.util.Scanner;

class ViewAttendance
{
    public static void main(String args[])
    {
        Scanner sc = new Scanner(System.in);

        try
        {
            Connection con = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/UniversityDB",
                "root",
                "mysql"
            );

            System.out.print("Enter Student ID: ");
            int id = sc.nextInt();

            sc.nextLine();

            System.out.print("Enter Password: ");
            String password = sc.nextLine();

            PreparedStatement ps = con.prepareStatement(
                "SELECT StudentID FROM Student WHERE StudentID = ? AND Password = ?"
            );

            ps.setInt(1, id);
            ps.setString(2, password);

            ResultSet rs = ps.executeQuery();

            if (rs.next())
            {
                PreparedStatement ps2 = con.prepareStatement(
                    "SELECT SubjectName, TotalClasses, AttendedClasses FROM Attendance WHERE StudentID = ?"
                );

                ps2.setInt(1, id);

                ResultSet rs2 = ps2.executeQuery();

                System.out.println("Subject\t\tTotal Classes\tAttended Classes\tPercentage");

                while (rs2.next())
                {
                    String subject = rs2.getString("SubjectName");
                    int total = rs2.getInt("TotalClasses");
                    int attended = rs2.getInt("AttendedClasses");

                    double percentage = (attended * 100.0) / total;

                    System.out.println(subject + "\t" + total + "\t\t" +
                        attended + "\t\t" + percentage + "%");
                }

                rs2.close();
                ps2.close();
            }
            else
            {
                System.out.println("Invalid Student ID or Password");
            }

            rs.close();
            ps.close();
            con.close();
        }
        catch (Exception e)
        {
            System.out.println(e);
        }

        sc.close();
    }
}
