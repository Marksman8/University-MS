package edu.ums.ui;

import edu.ums.api.Session;
import edu.ums.api.UmsClient;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Username + password sign-in. The password leaves the field only as a {@code char[]}. */
final class LoginView {

  private final BorderPane root = new BorderPane();

  LoginView(UmsClient client, Background background, boolean demoHints, Consumer<Session> onLogin) {
    StatusBar status = new StatusBar(background);
    TextField username = new TextField();
    username.setPromptText("Username");
    PasswordField password = new PasswordField();
    password.setPromptText("Password");
    Button signIn = Ui.primary("Sign in", () -> {});
    signIn.setDefaultButton(true);
    signIn.setMaxWidth(Double.MAX_VALUE);
    signIn.setOnAction(
        e -> {
          char[] secret = password.getText().toCharArray();
          password.clear();
          String user = username.getText();
          signIn.setDisable(true);
          status.info("Signing in…");
          background.run(
              () -> client.login(user, secret),
              onLogin,
              err -> {
                signIn.setDisable(false);
                status.error(err);
              });
        });
    Label title = new Label("University Management System");
    title.getStyleClass().add("title");
    VBox card = new VBox(10, title, new Label("Sign in to continue"), username, password, signIn);
    card.getStyleClass().add("card");
    card.setMaxWidth(380);
    card.setPadding(new Insets(24));
    if (demoHints) {
      Label hint =
          new Label(
              "Demo users: admin, dev, fac.rao, fac.menon, stu.anu, stu.binu, stu.chitra,"
                  + " stu.dev, stu.esha — password DemoPass@2026");
      hint.setWrapText(true);
      hint.getStyleClass().add("hint");
      card.getChildren().add(hint);
    }
    VBox center = new VBox(card);
    center.setAlignment(Pos.CENTER);
    root.setCenter(center);
    root.setBottom(status.root());
  }

  Parent root() {
    return root;
  }
}
