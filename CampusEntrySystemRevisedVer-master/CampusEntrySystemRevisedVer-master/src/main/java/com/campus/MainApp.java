package com.campus;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainApp extends Application {

    private static Stage  stage;
    private static String currentStudentId;
    private static final LocalTime CLOSE_TIME = LocalTime.of(22, 0); // 10:00 PM
    private static LocalDate lastAutoTimeoutDate = null;

    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "auto-timeout-scheduler");
                t.setDaemon(true);
                return t;
            });

    public static String getCurrentStudentId()         { return currentStudentId; }
    public static void   setCurrentStudentId(String s) { currentStudentId = s; }

    @Override
    public void start(Stage primaryStage) {
        try {
            stage = primaryStage;
            stage.setTitle("Campus Entry & Exit Monitoring System");
            stage.setResizable(true);
            stage.setMinWidth(400);
            stage.setMinHeight(300);

            // ── Initialize DB tables (shows its own error dialog on failure) ─
            try {
                DatabaseInitializer.initialize();
            } catch (Exception dbEx) {
                // DatabaseConnection already showed an alert; just log & continue
                // so the UI still opens (read-only mode gracefully degrades).
                System.err.println("[Startup] DB init failed: " + dbEx.getMessage());
            }

            startAutoTimeoutScheduler();

            switchScene("role-selection.fxml");
            stage.setMaximized(true);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Startup Error");
                alert.setHeaderText("The application failed to start");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            });
        }
    }

    /**
     * Polls every minute. Once the clock passes 10:00 PM (and hasn't already
     * run for today), it sweeps all students still timed-in and auto-times
     * them out, flagging them as blocked for the next day's time-in.
     */
    private void startAutoTimeoutScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                LocalDateTime now = LocalDateTime.now();
                LocalDate today = now.toLocalDate();

                boolean pastClose = !now.toLocalTime().isBefore(CLOSE_TIME);
                boolean alreadyRanToday = today.equals(lastAutoTimeoutDate);

                if (pastClose && !alreadyRanToday) {
                    AttendanceDAO dao = new AttendanceDAO();
                    int count = dao.autoTimeOutAll();
                    lastAutoTimeoutDate = today;
                    if (count > 0) {
                        System.out.println("[AutoTimeout] " + count +
                                " student(s) auto-timed-out at 10:00 PM and blocked for next day.");
                    }
                }
            } catch (Exception e) {
                System.err.println("[AutoTimeout] Failed: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    public static void switchScene(String fxml) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/com/campus/" + fxml));
        Parent root = loader.load();
        Scene scene;

        if (stage.getScene() != null) {
            scene = new Scene(root, stage.getScene().getWidth(), stage.getScene().getHeight());
        } else {
            scene = new Scene(root, 900, 600);
        }

        scene.getStylesheets().add(
                MainApp.class.getResource("/com/campus/style.css").toExternalForm());
        stage.setScene(scene);
        stage.centerOnScreen();
    }

    public static Stage getStage() { return stage; }

    public static void main(String[] args) { launch(args); }
}
