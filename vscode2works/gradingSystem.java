import javax.swing.*;

class grades {
    int eng, kis, math, avg;
    String grade, name;
    JTextField nField, kiswahili, english, mathematics;
    JButton btn;
    JLabel result;

    grades() {
    }

    grades(int e, int k, int m, int avg, String name, String g) {
        this.eng = e;
        this.kis = k;
        this.math = m;
        this.avg = avg;
        this.grade = g;
        this.name = name;
    }

    void guiSetup() {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 900);
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("Student Name :");
        JTextField n = new JTextField();
        JLabel mark = new JLabel("Enter students marks in respective subjects");
        JLabel Kis = new JLabel("Kiswahili");
        kiswahili = new JTextField();
        JLabel Eng = new JLabel("English");
        english = new JTextField();
        JLabel Maths = new JLabel("Mathematics");
        mathematics = new JTextField();
        btn = new JButton("enter");
        JLabel r = new JLabel("Student Grade");
        result = new JLabel();

        frame.add(panel);
        panel.add(name);
        panel.add(n);
        panel.add(mark);
        panel.add(Kis);
        panel.add(kiswahili);
        panel.add(Eng);
        panel.add(english);
        panel.add(Maths);
        panel.add(mathematics);
        panel.add(btn);
        panel.add(r);
        panel.add(result);

        frame.setVisible(true);

    }

    void allocation() {

        btn.addActionListener(e -> {
            try {
                int m = Integer.parseInt(mathematics.getText().trim());
                int k = Integer.parseInt(kiswahili.getText().trim());
                int engScore = Integer.parseInt(english.getText().trim());

                int avg = (m + k + engScore) / 3;
                if (avg >= 70) {
                    result.setText("A");
                } else if (avg >= 60) {
                    result.setText("B");
                } else if (avg >= 50) {
                    result.setText("C");
                } else if (avg >= 40) {
                    result.setText("D");
                } else {
                    result.setText("E");
                }
            } catch (NumberFormatException o) {
                result.setText("invalid input");
            }
        });

    }

}

public class gradingSystem {
    public static void main(String[] args) {
        grades grades = new grades();
        grades.guiSetup();
        grades.allocation();

    }
}