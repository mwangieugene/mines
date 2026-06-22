import java.awt.*;
import java.awt.event.*;

class grades {
    int eng, kis, math, avg;
    String grade, name;
    TextField nField, kiswahili, english, mathematics;
    Button btn;
    Label result;

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

    Frame frame;

    void guiSetup() {
        frame = new Frame("Grading System");
        frame.setSize(400, 400);
        Panel panel = new Panel();
        panel.setLayout(new GridLayout(0, 1, 5, 5));
        Label name = new Label("Student Name :");
        TextField n = new TextField();
        Label mark = new Label("Enter students marks in respective subjects");
        Label Kis = new Label("Kiswahili");
        kiswahili = new TextField();
        Label Eng = new Label("English");
        english = new TextField();
        Label Maths = new Label("Mathematics");
        mathematics = new TextField();
        btn = new Button("enter");
        Label r = new Label("Student Grade");
        result = new Label("");

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

        frame.add(panel);

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                frame.dispose();
                System.exit(0);
            }
        });

        frame.setVisible(true);

    }

    void allocation() {

        btn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
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