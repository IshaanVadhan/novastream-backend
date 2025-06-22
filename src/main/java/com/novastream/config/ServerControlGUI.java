package com.novastream.config;

import com.novastream.NovastreamBackendApplication;
import com.novastream.service.MediaService;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ServerControlGUI extends Application {

  private TextFlow logFlow;
  private ScrollPane logScrollPane;
  private Label statusLabel;
  private Label uptimeLabel;
  private Button startRestartBtn;
  private Button stopBtn;
  private Button clearLogsBtn;
  private ProgressIndicator loadingIndicator;
  private double xOffset = 0;
  private double yOffset = 0;
  private String currentIpAddress;

  private ConfigurableApplicationContext springContext;
  private boolean serverRunning = false;
  private LocalDateTime startTime;
  private Timeline uptimeTimer;

  public ServerControlGUI() {}

  public static void launchWithSplash() {
    Platform.startup(() -> {
      Stage primaryStage = new Stage();
      showSplashAndLaunch(primaryStage);
    });
  }

  public static void showSplashAndLaunch(Stage primaryStage) {
    Stage splashStage = new Stage(StageStyle.TRANSPARENT);

    ProgressIndicator progressIndicator = new ProgressIndicator();
    progressIndicator.setPrefSize(20, 20);
    progressIndicator.getStyleClass().add("loading-indicator");

    Label loadingLabel = new Label("Extracting essential binaries...");
    loadingLabel.getStyleClass().add("splash-label");

    HBox content = new HBox(10, progressIndicator, loadingLabel);
    content.setAlignment(Pos.CENTER);

    StackPane splashPane = new StackPane(content);
    splashPane.getStyleClass().add("splash-pane");
    splashPane.setPrefSize(275, 75);

    Scene splashScene = new Scene(splashPane);
    splashScene.setFill(Color.TRANSPARENT);
    splashScene
      .getStylesheets()
      .add(
        ServerControlGUI.class.getResource("/static/gui.css").toExternalForm()
      );

    splashStage.setScene(splashScene);
    splashStage.setAlwaysOnTop(true);
    splashStage.centerOnScreen();
    splashStage
      .getIcons()
      .add(
        new Image(
          ServerControlGUI.class.getResourceAsStream("/static/icon.png")
        )
      );
    splashStage.show();

    CompletableFuture
      .runAsync(() -> {
        com.novastream.util.BinaryExtractor.extractBinaries();
      })
      .thenRun(() -> {
        Platform.runLater(() -> {
          splashStage.close();
          new ServerControlGUI().start(primaryStage);
        });
      });
  }

  @SuppressWarnings("unused")
  @Override
  public void start(Stage primaryStage) {
    primaryStage.initStyle(StageStyle.TRANSPARENT);

    DropShadow shadow = new DropShadow(20, Color.BLACK);
    shadow.setOffsetX(0);
    shadow.setOffsetY(10);

    VBox root = createMainLayout(primaryStage);
    root.setEffect(shadow);
    root.getStyleClass().add("main-container");

    Rectangle clip = new Rectangle(800, 600);
    clip.setArcWidth(16);
    clip.setArcHeight(16);
    root.setClip(clip);

    Scene scene = new Scene(root, 800, 600);
    scene.setFill(Color.TRANSPARENT);
    scene
      .getStylesheets()
      .add(getClass().getResource("/static/gui.css").toExternalForm());

    primaryStage.setScene(scene);
    primaryStage.setTitle("NovaStream");
    primaryStage
      .getIcons()
      .add(new Image(getClass().getResourceAsStream("/static/icon.png")));
    primaryStage.show();
    animateStartup(primaryStage);

    primaryStage.setOnCloseRequest(e -> {
      stopServer();
      Platform.exit();
      System.exit(0);
    });

    setupSystemStreamRedirection();
  }

  private VBox createMainLayout(Stage stage) {
    VBox root = new VBox();

    HBox titleBar = createTitleBar(stage);
    HBox statusBar = createStatusBar();
    VBox controlPanel = createControlPanel();
    VBox logPanel = createLogPanel();

    root.getChildren().addAll(titleBar, statusBar, controlPanel, logPanel);
    VBox.setVgrow(logPanel, Priority.ALWAYS);

    return root;
  }

  private HBox createTitleBar(Stage stage) {
    HBox titleBar = new HBox();
    titleBar.setAlignment(Pos.CENTER_LEFT);
    titleBar.getStyleClass().add("title-bar");

    Image iconImage = new Image(
      getClass().getResourceAsStream("/static/icon.png")
    );
    ImageView iconView = new ImageView(iconImage);
    iconView.setFitWidth(30);
    iconView.setFitHeight(30);
    Rectangle clip = new Rectangle(
      iconView.getFitWidth(),
      iconView.getFitHeight()
    );
    clip.setArcWidth(10);
    clip.setArcHeight(10);

    iconView.setClip(clip);
    iconView.setPreserveRatio(true);

    Label title = new Label("NovaStream");
    title.getStyleClass().add("title-label");

    HBox titleContainer = new HBox(iconView, title);
    titleContainer.getStyleClass().add("title-container");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Button minimizeBtn = createWindowButton(
      "―",
      "window-button-normal",
      () -> animateMinimize(stage)
    );
    Button closeBtn = createWindowButton(
      "🗙",
      "window-button-close",
      () -> animateClose(stage)
    );

    titleBar
      .getChildren()
      .addAll(titleContainer, spacer, minimizeBtn, closeBtn);

    titleBar.setOnMousePressed(e -> {
      xOffset = e.getSceneX();
      yOffset = e.getSceneY();
    });
    titleBar.setOnMouseDragged(e -> {
      stage.setX(e.getScreenX() - xOffset);
      stage.setY(e.getScreenY() - yOffset);
    });

    return titleBar;
  }

  private void animateStartup(Stage stage) {
    var root = stage.getScene().getRoot();
    root.setScaleX(0.8);
    root.setScaleY(0.8);
    root.setOpacity(0.0);
    createAnimation(root, 400, 1.0, 1.0, 1.0, 0, 0, null).play();
  }

  @SuppressWarnings("unused")
  private void animateMinimize(Stage stage) {
    var root = stage.getScene().getRoot();
    var anim = createAnimation(
      root,
      200,
      0.1,
      0.1,
      0.0,
      0,
      100,
      e -> {
        stage.setIconified(true);
        root.setScaleX(1.0);
        root.setScaleY(1.0);
        root.setOpacity(1.0);
        root.setTranslateY(0);
      }
    );
    anim.play();

    stage
      .iconifiedProperty()
      .addListener((obs, wasIconified, isIconified) -> {
        if (!isIconified && wasIconified) animateRestore(stage);
      });
  }

  private void animateRestore(Stage stage) {
    var root = stage.getScene().getRoot();
    root.setScaleX(0.8);
    root.setScaleY(0.8);
    root.setOpacity(0.0);
    createAnimation(root, 300, 1.0, 1.0, 1.0, 0, 0, null).play();
  }

  @SuppressWarnings("unused")
  private void animateClose(Stage stage) {
    var anim = createAnimation(
      stage.getScene().getRoot(),
      300,
      0.0,
      0.0,
      0.0,
      0,
      0,
      e -> {
        stopServer();
        Platform.exit();
        System.exit(0);
      }
    );
    anim.play();
  }

  private ParallelTransition createAnimation(
    javafx.scene.Node node,
    int ms,
    double scaleX,
    double scaleY,
    double opacity,
    double translateX,
    double translateY,
    javafx.event.EventHandler<javafx.event.ActionEvent> onFinish
  ) {
    var scale = new ScaleTransition(Duration.millis(ms), node);
    scale.setToX(scaleX);
    scale.setToY(scaleY);
    var fade = new FadeTransition(Duration.millis(ms), node);
    fade.setToValue(opacity);
    var translate = new TranslateTransition(Duration.millis(ms), node);
    translate.setToX(translateX);
    translate.setToY(translateY);
    var parallel = new ParallelTransition(scale, fade, translate);
    if (onFinish != null) parallel.setOnFinished(onFinish);
    return parallel;
  }

  private HBox createStatusBar() {
    HBox statusBar = new HBox();
    statusBar.getStyleClass().add("status-bar");

    Label statusPrefix = new Label("Status:");
    statusPrefix.getStyleClass().add("status-prefix");

    statusLabel = new Label("Stopped");
    statusLabel.getStyleClass().add("status-stopped");

    Label uptimePrefix = new Label("Uptime:");
    uptimePrefix.getStyleClass().add("uptime-prefix");

    uptimeLabel = new Label("00:00:00");
    uptimeLabel.getStyleClass().add("uptime-label");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    loadingIndicator = new ProgressIndicator();
    loadingIndicator.getStyleClass().add("loading-indicator");
    loadingIndicator.setVisible(false);

    statusBar
      .getChildren()
      .addAll(
        statusPrefix,
        statusLabel,
        new Separator(),
        uptimePrefix,
        uptimeLabel,
        spacer,
        loadingIndicator
      );

    return statusBar;
  }

  private VBox createControlPanel() {
    VBox panel = new VBox();
    panel.getStyleClass().add("control-panel");

    Label title = new Label("Server Controls");
    title.getStyleClass().add("control-title");

    HBox controls = new HBox();
    controls.getStyleClass().add("controls-container");

    startRestartBtn =
      createActionButton("▶ Start Server", "start-button", this::startServer);
    stopBtn =
      createActionButton("⏹ Stop Server", "stop-button", this::stopServer);
    clearLogsBtn =
      createActionButton("🗑 Clear Logs", "clear-button", this::clearLogs);

    stopBtn.setDisable(true);
    clearLogsBtn.setDisable(true);

    controls.getChildren().addAll(startRestartBtn, stopBtn, clearLogsBtn);
    panel.getChildren().addAll(title, controls);

    return panel;
  }

  @SuppressWarnings("unused")
  private VBox createLogPanel() {
    VBox panel = new VBox();
    panel.getStyleClass().add("log-panel");
    VBox.setVgrow(panel, Priority.ALWAYS);

    Label title = new Label("Server Logs");
    title.getStyleClass().add("log-title");

    logFlow = new TextFlow();
    logFlow.getStyleClass().add("log-flow");
    logFlow.setMaxWidth(Double.MAX_VALUE);
    logFlow.prefWidthProperty().bind(panel.widthProperty());

    logScrollPane = new ScrollPane(logFlow);
    logScrollPane.setFitToWidth(true);
    logScrollPane.setPrefViewportHeight(200);
    logScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    logScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    logScrollPane.getStyleClass().add("log-scroll-pane");
    VBox.setVgrow(logScrollPane, Priority.ALWAYS);

    StackPane logContainer = new StackPane(logScrollPane);
    logContainer.getStyleClass().add("log-container");
    Rectangle clip = new Rectangle();
    clip.setArcWidth(8);
    clip.setArcHeight(8);
    clip.widthProperty().bind(logContainer.widthProperty());
    clip.heightProperty().bind(logContainer.heightProperty());
    logContainer.setClip(clip);
    VBox.setVgrow(logContainer, Priority.ALWAYS);

    logFlow
      .getChildren()
      .addListener(
        (ListChangeListener<Node>) change -> {
          clearLogsBtn.setDisable(logFlow.getChildren().isEmpty());
        }
      );

    panel.getChildren().addAll(title, logContainer);
    return panel;
  }

  @SuppressWarnings("unused")
  private Button createWindowButton(
    String text,
    String styleClass,
    Runnable action
  ) {
    Button btn = new Button(text);
    btn.setMaxHeight(Double.MAX_VALUE);
    btn.setPrefHeight(Region.USE_COMPUTED_SIZE);
    btn.getStyleClass().addAll("window-button", styleClass);
    btn.setOnAction(e -> action.run());
    return btn;
  }

  @SuppressWarnings("unused")
  private Button createActionButton(
    String text,
    String styleClass,
    Runnable action
  ) {
    Button btn = new Button(text);
    btn.getStyleClass().addAll("action-button", styleClass);
    btn.setOnAction(e -> action.run());
    return btn;
  }

  @SuppressWarnings("unused")
  private void updateStartRestartButton() {
    if (serverRunning) {
      startRestartBtn.setText("🔄 Restart Server");
      startRestartBtn
        .getStyleClass()
        .removeAll("start-button", "restart-button");
      startRestartBtn.getStyleClass().add("restart-button");
      startRestartBtn.setOnAction(e -> restartServer());
    } else {
      startRestartBtn.setText("▶ Start Server");
      startRestartBtn
        .getStyleClass()
        .removeAll("start-button", "restart-button");
      startRestartBtn.getStyleClass().add("start-button");
      startRestartBtn.setOnAction(e -> startServer());
    }
  }

  private void updateStatusLabel(String text, String styleClass) {
    statusLabel.setText(text);
    statusLabel.getStyleClass().clear();
    statusLabel.getStyleClass().add(styleClass);
  }

  private void startServer() {
    if (
      (loadingIndicator != null && loadingIndicator.isVisible()) ||
      serverRunning
    ) return;

    Platform.runLater(() -> {
      loadingIndicator.setVisible(true);
      updateStatusLabel("Starting...", "status-starting");
      startRestartBtn.setDisable(true);
      stopBtn.setDisable(true);
    });

    CompletableFuture.runAsync(() -> {
      try {
        appendLog("▶ Starting server...", LogType.WARNING);

        String selectedPath = MediaService.chooseFolder();
        if (!StringUtils.hasText(selectedPath)) {
          throw new RuntimeException("No media folder selected");
        }

        appendLog("📁 Media folder selected: " + selectedPath, LogType.INFO);

        springContext =
          SpringApplication.run(NovastreamBackendApplication.class);
        MediaConfig config = springContext.getBean(MediaConfig.class);
        config.setBasePath(selectedPath);
        serverRunning = true;
        startTime = LocalDateTime.now();
        currentIpAddress = getIpAddress();

        Platform.runLater(() -> {
          loadingIndicator.setVisible(false);
          updateStatusLabel("Running", "status-running");
          startRestartBtn.setDisable(false);
          stopBtn.setDisable(false);
          updateStartRestartButton();
          startUptimeTimer();
        });

        appendLog("✅ Server started successfully", LogType.SUCCESS);
        appendLog(
          "🌐 Server running on http://" + currentIpAddress + ":8080",
          LogType.INFO
        );
      } catch (Exception e) {
        Platform.runLater(() -> {
          loadingIndicator.setVisible(false);
          updateStatusLabel("Failed", "status-failed");
          startRestartBtn.setDisable(false);
        });

        Throwable cause = e;
        while (cause.getCause() != null) {
          cause = cause.getCause();
        }
        String msg = cause.getMessage();
        if (msg != null && msg.contains("Address already in use")) {
          appendLog(
            "❌ Failed to start server: Port 8080 is already in use. Please free the port and try again.",
            LogType.ERROR
          );
        } else {
          appendLog("❌ Failed to start server: " + msg, LogType.ERROR);
        }
      }
    });
  }

  private void restartServer() {
    if ((loadingIndicator != null && loadingIndicator.isVisible())) return;

    Platform.runLater(() -> {
      uptimeLabel.setText("00:00:00");
      stopUptimeTimer();
      loadingIndicator.setVisible(true);
      updateStatusLabel("Restarting...", "status-starting");
      startRestartBtn.setDisable(true);
      stopBtn.setDisable(true);
    });

    CompletableFuture.runAsync(() -> {
      try {
        appendLog("🔄 Restarting server...", LogType.WARNING);

        if (springContext != null && serverRunning) {
          appendLog("⏹ Stopping current server instance...", LogType.SYSTEM);
          springContext.close();
          springContext = null;
          serverRunning = false;
          Thread.sleep(1000);
        }

        appendLog("🚀 Starting new server instance...", LogType.SYSTEM);

        String selectedPath = MediaService.chooseFolder();
        if (!StringUtils.hasText(selectedPath)) {
          throw new RuntimeException("No media folder selected");
        }

        appendLog("📁 Media folder selected: " + selectedPath, LogType.INFO);

        springContext =
          SpringApplication.run(NovastreamBackendApplication.class);
        MediaConfig config = springContext.getBean(MediaConfig.class);
        config.setBasePath(selectedPath);
        serverRunning = true;
        startTime = LocalDateTime.now();
        currentIpAddress = getIpAddress();

        Platform.runLater(() -> {
          loadingIndicator.setVisible(false);
          updateStatusLabel("Running", "status-running");
          startRestartBtn.setDisable(false);
          stopBtn.setDisable(false);
          updateStartRestartButton();
          startUptimeTimer();
        });

        appendLog("✅ Server restarted successfully", LogType.SUCCESS);
        appendLog(
          "🌐 Server running on http://" + currentIpAddress + ":8080",
          LogType.INFO
        );
      } catch (Exception e) {
        Platform.runLater(() -> {
          loadingIndicator.setVisible(false);
          updateStatusLabel("Failed", "status-failed");
          startRestartBtn.setDisable(false);
          stopBtn.setDisable(true);
          updateStartRestartButton();
        });

        Throwable cause = e;
        while (cause.getCause() != null) {
          cause = cause.getCause();
        }
        String msg = cause.getMessage();
        if (msg != null && msg.contains("Address already in use")) {
          appendLog(
            "❌ Failed to restart server: Port 8080 is already in use. Please free the port and try again.",
            LogType.ERROR
          );
        } else {
          appendLog("❌ Failed to restart server: " + msg, LogType.ERROR);
        }
      }
    });
  }

  public void stopServer() {
    if (
      (loadingIndicator != null && loadingIndicator.isVisible()) ||
      !serverRunning
    ) return;

    Platform.runLater(() -> {
      loadingIndicator.setVisible(true);
      updateStatusLabel("Stopping...", "status-starting");
      startRestartBtn.setDisable(true);
      stopBtn.setDisable(true);
    });

    CompletableFuture.runAsync(() -> {
      try {
        appendLog("⏹ Stopping server...", LogType.WARNING);
        if (springContext != null) {
          springContext.close();
          springContext = null;
        }
        serverRunning = false;

        Platform.runLater(() -> {
          uptimeLabel.setText("00:00:00");
          stopUptimeTimer();
          loadingIndicator.setVisible(false);
          updateStatusLabel("Stopped", "status-stopped");
          startRestartBtn.setDisable(false);
          stopBtn.setDisable(true);

          updateStartRestartButton();
        });
        appendLog("✅ Server stopped successfully", LogType.ERROR);
      } catch (Exception e) {
        Platform.runLater(() -> {
          uptimeLabel.setText("00:00:00");
          stopUptimeTimer();
          loadingIndicator.setVisible(false);
          updateStatusLabel("Failed to stop", "status-failed-stop");
          startRestartBtn.setDisable(false);
          stopBtn.setDisable(false);
        });
        appendLog("❌ Failed to stop server: " + e.getMessage(), LogType.ERROR);
      }
    });
  }

  private void clearLogs() {
    logFlow.getChildren().clear();
  }

  @SuppressWarnings("unused")
  private void startUptimeTimer() {
    stopUptimeTimer();
    uptimeTimer =
      new Timeline(new KeyFrame(Duration.seconds(1), e -> updateUptime()));
    uptimeTimer.setCycleCount(Timeline.INDEFINITE);
    uptimeTimer.play();
  }

  private void stopUptimeTimer() {
    if (uptimeTimer != null) {
      uptimeTimer.stop();
    }
  }

  private void updateUptime() {
    if (startTime != null && serverRunning) {
      java.time.Duration uptime = java.time.Duration.between(
        startTime,
        LocalDateTime.now()
      );
      long hours = uptime.toHours();
      long minutes = uptime.toMinutes() % 60;
      long seconds = uptime.getSeconds() % 60;

      Platform.runLater(() ->
        uptimeLabel.setText(
          String.format("%02d:%02d:%02d", hours, minutes, seconds)
        )
      );
    }
  }

  private void appendLog(String message, LogType type) {
    Platform.runLater(() -> {
      String timestamp = LocalDateTime
        .now()
        .format(DateTimeFormatter.ofPattern("HH:mm:ss"));

      Text timestampText = new Text("[" + timestamp + "] ");
      timestampText.getStyleClass().add("log-timestamp");

      Text messageText = new Text(message + "\n");
      messageText.getStyleClass().addAll("log-message", type.getStyleClass());

      Font emojiFont = Font.font("Segoe UI Emoji", 12);
      messageText.setFont(emojiFont);
      timestampText.setFont(Font.font("Consolas", 11));

      logFlow.getChildren().addAll(timestampText, messageText);

      Platform.runLater(() -> {
        logScrollPane.setVvalue(1.0);
      });
    });
  }

  private enum LogType {
    INFO("log-info"),
    SUCCESS("log-success"),
    WARNING("log-warning"),
    ERROR("log-error"),
    SYSTEM("log-system");

    private final String styleClass;

    LogType(String styleClass) {
      this.styleClass = styleClass;
    }

    public String getStyleClass() {
      return styleClass;
    }
  }

  private void setupSystemStreamRedirection() {
    OutputStream logStream = new OutputStream() {
      private StringBuilder buffer = new StringBuilder();

      @Override
      public void write(int b) {
        buffer.append((char) b);
        if (b == '\n') {
          flush();
        }
      }

      @Override
      public void flush() {
        if (buffer.length() > 0) {
          String message = buffer.toString().trim();
          if (!message.isEmpty()) {
            appendLog("📋 " + message, LogType.ERROR);
          }
          buffer.setLength(0);
        }
      }
    };

    System.setOut(new PrintStream(logStream, true));
    System.setErr(new PrintStream(logStream, true));
  }

  public static void launchGUI() {
    launch();
  }

  private String getIpAddress() {
    try {
      return java.util.Collections
        .list(java.net.NetworkInterface.getNetworkInterfaces())
        .stream()
        .filter(ni -> {
          try {
            return ni.isUp() && !ni.isLoopback();
          } catch (java.net.SocketException e) {
            return false;
          }
        })
        .flatMap(ni ->
          java.util.Collections.list(ni.getInetAddresses()).stream()
        )
        .filter(addr -> addr instanceof java.net.Inet4Address)
        .map(java.net.InetAddress::getHostAddress)
        .findFirst()
        .orElse("Unable to determine IP");
    } catch (java.net.SocketException e) {
      return "Unable to determine IP";
    }
  }
}
