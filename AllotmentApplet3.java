import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AllotmentApplet3 extends JFrame {

    private static final String DB_URL = "jdbc:oracle:thin:@localhost:1521:XE";
    private static final String USER = "class_db"; // Replace with your Oracle DB username
    private static final String PASS = "1234"; // Replace with your Oracle DB password

    private JTextField examDateField;
    private JTextField numRoomsField;
    private JTextField numStudentsField;
    private JTextArea outputTextArea;
    private JPanel inputPanel;

    public AllotmentApplet3() {
        setLayout(new BorderLayout());

        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }

        Font defaultFont = new Font("Arial", Font.PLAIN, 25);

        inputPanel = new JPanel(new GridLayout(0, 2));

        JLabel examDateLabel = new JLabel("Exam Date (DD-MON-YY):");
        examDateLabel.setFont(defaultFont);
        inputPanel.add(examDateLabel);
        examDateField = new JTextField(10);
        examDateField.setFont(defaultFont);
        inputPanel.add(examDateField);

        JLabel numRoomsLabel = new JLabel("Number of Rooms:");
        numRoomsLabel.setFont(defaultFont);
        inputPanel.add(numRoomsLabel);
        numRoomsField = new JTextField(10);
        numRoomsField.setFont(defaultFont);
        inputPanel.add(numRoomsField);

        JLabel numStudentsLabel = new JLabel("Number of Students:");
        numStudentsLabel.setFont(defaultFont);
        inputPanel.add(numStudentsLabel);
        numStudentsField = new JTextField(10);
        numStudentsField.setFont(defaultFont);
        inputPanel.add(numStudentsField);

        JButton submitButton = new JButton("Allocate Students");
        submitButton.setFont(defaultFont);
        submitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                handleAllocation();
            }
        });
        inputPanel.add(submitButton);

        add(inputPanel, BorderLayout.NORTH);

        outputTextArea = new JTextArea(20, 40);
        outputTextArea.setFont(new Font("Monospaced", Font.PLAIN, 25));
        outputTextArea.setEditable(false);
        add(new JScrollPane(outputTextArea), BorderLayout.CENTER);

        setTitle("Student Allocation");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    private void handleAllocation() {
        String examDate = examDateField.getText();
        int numRooms = Integer.parseInt(numRoomsField.getText());
        int numStudents = Integer.parseInt(numStudentsField.getText());

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            clearRoomTable(conn);
            allocateStudents(conn, examDate, numRooms, numStudents);
        } catch (SQLException e) {
            outputTextArea.append("Error: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private void clearRoomTable(Connection conn) throws SQLException {
        String clearTable = "DELETE FROM Room";
        try (PreparedStatement clearStmt = conn.prepareStatement(clearTable)) {
            clearStmt.executeUpdate();
        }
    }

    private void allocateStudents(Connection conn, String examDate, int numRooms, int numStudents) throws SQLException {
        List<Student> students = fetchStudents(conn, examDate, numStudents);

        int studentIndex = 0;

        // Initialize rooms
        String[][][] rooms = new String[numRooms][][];

        for (int roomIndex = 0; roomIndex < numRooms; roomIndex++) {
            int numRows = getIntegerInput("Enter number of rows for Room " + (roomIndex + 1));
            int numCols = getIntegerInput("Enter number of columns for Room " + (roomIndex + 1));
            rooms[roomIndex] = new String[numRows][numCols];
        }

        // Allocate students to rooms (column-wise)
        for (int roomIndex = 0; roomIndex < numRooms && studentIndex < students.size(); roomIndex++) {
            for (int col = 0; col < rooms[roomIndex][0].length && studentIndex < students.size(); col++) {
                for (int row = 0; row < rooms[roomIndex].length && studentIndex < students.size(); row++) {
                    if (rooms[roomIndex][row][col] == null) {
                        rooms[roomIndex][row][col] = students.get(studentIndex++).getStudentId();
                    }
                }
            }
        }

        // Allocate remaining students in pairs column-wise across rooms
        while (studentIndex < students.size()) {
            for (int roomIndex = 0; roomIndex < numRooms && studentIndex < students.size(); roomIndex++) {
                for (int col = 0; col < rooms[roomIndex][0].length && studentIndex < students.size(); col++) {
                    for (int row = 0; row < rooms[roomIndex].length && studentIndex < students.size(); row++) {
                        if (rooms[roomIndex][row][col] != null && !rooms[roomIndex][row][col].contains(",")) {
                            rooms[roomIndex][row][col] += "," + students.get(studentIndex++).getStudentId();
                        }
                    }
                }
            }
        }

        // Display room-wise seating arrangement in the outputTextArea
        outputTextArea.setText("");
        outputTextArea.append("\t\tSTUDENT ALLOCATION\n\n");
        outputTextArea.setFont(new Font("Arial", Font.BOLD, 20));

        for (int roomIndex = 0; roomIndex < numRooms; roomIndex++) {
            outputTextArea.append("Room " + (roomIndex + 1) + ":\n");

            for (int row = 0; row < rooms[roomIndex].length; row++) {
                for (int col = 0; col < rooms[roomIndex][row].length; col++) {
                    if (rooms[roomIndex][row][col] != null) {
                        String[] studentIds = rooms[roomIndex][row][col].split(",");
                        for (String studentId : studentIds) {
                            outputTextArea.append(studentId + "\t");
                        }
                    } else {
                        outputTextArea.append("\t");
                    }
                    outputTextArea.append("(" + (row + 1) + "," + (col + 1) + ")\t");
                }
                outputTextArea.append("\n");
            }
            outputTextArea.append("\n");
        }

        outputTextArea.append("Students allocated to rooms successfully.\n");
    }

    private int getIntegerInput(String message) {
        while (true) {
            String input = JOptionPane.showInputDialog(message);
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Invalid input. Please enter a valid number.");
            }
        }
    }

    private List<Student> fetchStudents(Connection conn, String examDate, int numStudents) throws SQLException {
        String fetchStudentsQuery = "SELECT s.student_id " +
                                    "FROM student s " +
                                    "WHERE s.dept_name IN " +
                                    "(SELECT d.dept_name " +
                                    " FROM dept d, exam e " +
                                    " WHERE d.dept_id = e.dept_id " +
                                    "   AND e.exam_date = ?) " +
                                    "OR s.student_id LIKE '22011A0401%' " + // Include 22011A0401 series
                                    "AND ROWNUM <= ? " +
                                    "ORDER BY s.student_id";

        List<Student> students = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(fetchStudentsQuery)) {
            stmt.setString(1, examDate);
            stmt.setInt(2, numStudents);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String studentId = rs.getString("student_id");
                students.add(new Student(studentId));
            }
        }
        return students;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new AllotmentApplet3();
            }
        });
    }
}

class Student {
    private String studentId;

    public Student(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentId() {
        return studentId;
    }
}