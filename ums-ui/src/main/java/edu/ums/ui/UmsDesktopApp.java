package edu.ums.ui;

import edu.ums.api.Session;
import edu.ums.api.UmsClient;
import edu.ums.app.UmsRuntime;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Desktop entry point (prototype). Starts the backend runtime off the FX thread in {@link #init()},
 * then swaps between the login view and the role shell.
 */
public final class UmsDesktopApp extends Application {

  private UmsRuntime runtime;
  private UmsClient client;
  private boolean demoHints;
  private String startupError;
  private Background background;
  private Stage stage;

  /** Launches the application. */
  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void init() {
    background = new Background();
    runtime = UmsRuntime.fromEnvironment();
    try {
      runtime.start();
      client = runtime.client();
      demoHints = runtime.environment().name().matches("LOCAL|TEST");
    } catch (RuntimeException e) { // startup boundary: show the reason in the window
      startupError = e.getMessage();
    }
  }

  @Override
  public void start(Stage primary) {
    this.stage = primary;
    stage.setTitle("UMS — University Management System");
    stage.setWidth(1200);
    stage.setHeight(760);
    if (startupError != null) {
      stage.setScene(styled(new StartupErrorView(startupError).root()));
    } else {
      showLogin();
    }
    stage.show();
  }

  void showLogin() {
    LoginView login = new LoginView(client, background, demoHints, this::showShell);
    stage.setScene(styled(login.root()));
  }

  void showShell(Session session) {
    ShellView shell = new ShellView(session, background, this::showLogin);
    stage.setScene(styled(shell.root()));
  }

  private static Scene styled(javafx.scene.Parent root) {
    Scene scene = new Scene(root);
    scene.getStylesheets().add(UmsDesktopApp.class.getResource("/app.css").toExternalForm());
    return scene;
  }

  @Override
  public void stop() {
    background.shutdown();
    if (runtime != null) {
      runtime.stop();
    }
    Platform.exit();
  }
}
