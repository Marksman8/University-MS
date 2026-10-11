CREATE TABLE Student (
    StudentID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    StudentName VARCHAR(100) NOT NULL,
    Email VARCHAR(100) UNIQUE NOT NULL,
    Phone VARCHAR(15),
    Department VARCHAR(50),
    AdmissionYear INT,
    Address VARCHAR(200),
    Password VARCHAR(255) NOT NULL
);


CREATE TABLE Marks (
    MarkID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    StudentID INT REFERENCES Student(StudentID),
    SubjectName VARCHAR(100),
    MarksObtained INT,
    TotalMarks INT
);

CREATE TABLE Attendance (
    AttendanceID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    StudentID INT REFERENCES Student(StudentID),
    SubjectName VARCHAR(100),
    TotalClasses INT,
    AttendedClasses INT
);


