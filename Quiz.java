public class Quiz {
    private int questionId;
    private String questionText;
    private String[] options;
    private int correctAnswer;

    public Quiz(int questionId, String questionText,
                String[] options, int correctAnswer) {
        this.questionId = questionId;
        this.questionText = questionText;
        this.options = options;
        this.correctAnswer = correctAnswer;
    }

    public int getQuestionId() {
        return questionId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String[] getOptions() {
        return options;
    }

    public boolean checkAnswer(int answer) {
        return answer == correctAnswer;
    }

    public static void main(String[] args) {
        String[] options = {"Java", "Python", "C", "C++"};

        Quiz q = new Quiz(1, "Which is a programming language?",
                          options, 1);

        System.out.println("Question ID: " + q.getQuestionId());
        System.out.println("Question: " + q.getQuestionText());

        System.out.println("Options:");
        for (int i = 0; i < q.getOptions().length; i++) {
            System.out.println((i + 1) + ". " + q.getOptions()[i]);
        }

        int answer = 1;

        if (q.checkAnswer(answer)) {
            System.out.println("Correct Answer!");
        } else {
            System.out.println("Wrong Answer!");
        }
    }
}