package com.GymGate;



import com.GymGate.UI.controller.MainController;
import com.GymGate.bussines.db.DatabaseManager;
import com.GymGate.bussines.models.Settings;
import com.GymGate.bussines.services.I18nService;
import com.GymGate.bussines.services.LicenseService;
import com.GymGate.bussines.services.ReminderScheduler;
import com.GymGate.bussines.services.RestartAppService;
import com.GymGate.bussines.services.SettingsLoader;
import com.GymGate.bussines.services.SupabaseClient;
import com.GymGate.bussines.services.SyncingService;
import com.GymGate.bussines.util.SoundUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import nu.pattern.OpenCV;
import java.io.IOException;
import java.util.Objects;

public class GymGateEntry extends Application {

    public static final double MIN_WIDTH = 1200;
    public static final double MIN_HEIGHT = 760;


    private Scene mainScene;
    private MainController mainController;

    /** Set (on the FX thread) when Supabase rejects our API key with 401 — the
     *  publishable key was rotated/revoked. If the main window is already up we
     *  lock it out immediately; otherwise {@link #attachMainStage} does it the
     *  moment the window would have been shown. */
    private volatile boolean keyRevoked = false;

    @Override
    public void init() {
        // A terminate signal — IDE Stop button, taskkill, Ctrl-Break, OS
        // logout — does NOT invoke Application.stop(). Without this hook the
        // camera handle, the sync scheduler and (worst of all) ONNX Runtime's
        // native inference threads outlive the "stopped" process. Runs the
        // same teardown as a clean quit; releaseResources() is idempotent, so
        // it is harmless when stop() has already run.
        Runtime.getRuntime().addShutdownHook(
                new Thread(RestartAppService::releaseResources, "gymgate-shutdown"));
    }

    /**
     * Called by the JavaFX runtime once the last window has closed (implicit
     * exit) or {@link javafx.application.Platform#exit()} has run. Without this,
     * a plain window close left the sync scheduler and camera threads running:
     * only the explicit restart/quit path went through
     * {@link RestartAppService#shutdown()}, so a direct run of this class (no
     * {@code Launcher.main} → no {@code System.exit(0)}) never terminated.
     */
    @Override
    public void stop() {
        RestartAppService.releaseResources();
    }

    @Override
    public void start(Stage primaryStage) {
        // Trial gate — runs before OpenCV, the database, settings or anything
        // else. If this is an expired demo build, show the "trial ended"
        // screen and nothing more; the app never really starts.
        if (LicenseService.isTrialExpired()) {
            new TrialExpiredScreen().show(primaryStage);
            return;
        }

        // Remote kill switch: if Supabase ever rejects our key with 401 (key
        // rotated/revoked server-side), show the same "trial ended" screen.
        SupabaseClient.setAuthRevokedHandler(() -> Platform.runLater(() -> {
            keyRevoked = true;
            if (RestartAppService.mainStage != null) {
                lockOutForRevokedKey(RestartAppService.mainStage);
            }
        }));

        SplashScreen splash = new SplashScreen();

        // <-- put your splash text here
        splash.setSubtitle("مرحبًا بك في GymGate، نظام الإدارة الذكي للنوادي الرياضية.\n\n" +
                "أدر أعضاء ناديك، تابع الاشتراكات والحضور، ونظّم المدفوعات بسهولة.\n\n" +
                "مع تقنية التعرف على الوجه وواجهة عصرية،تصبح إدارة ناديك أكثر ذكاءً وكفاءة.");

        splash.show();
        SoundUtil.initialize();
        splash.setOnProceed(() -> attachMainStage(primaryStage));

        splash.runInitTask(
                reporter -> {
                    // A missing database file means this is a first run: after
                    // DatabaseManager creates the empty file + schema below, the
                    // local data is seeded from the Supabase copy in its own
                    // progress step. A normal start skips that step.
                    boolean firstRun = !DatabaseManager.databaseFileExists();
                    int totalSteps = firstRun ? 5 : 4;
                    int step = 0;

                    reporter.report("Preparing camera", ++step, totalSteps);
                    OpenCV.loadLocally();

                    reporter.report("Loading database", ++step, totalSteps);
                    DatabaseManager.getInstance();

                    if (firstRun) {
                        reporter.report("Restoring data from the cloud", ++step, totalSteps);
                        SyncingService.getInstance().restoreFromCloud();
                    }

                    reporter.report("Initializing application", ++step, totalSteps);
                    SettingsLoader.loadSettings();

                    // Reminders (local-only, never blocks startup): purges any
                    // stale rows, then sweeps the members table hourly for expired
                    // / inactive members and shows the review. Self-guards on the
                    // WhatsApp setting.
                    try {
                        ReminderScheduler.getInstance().start();
                    } catch (RuntimeException e) {
                        System.err.println("Reminder scheduler failed to start (non-fatal): " + e.getMessage());
                    }

                    reporter.report("Loading interface", ++step, totalSteps);
                    I18nService.init();

                    SyncingService.getInstance().start();
                },

                this::buildMainScene
        );
    }

    private void buildMainScene() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(getClass().getResource("/fxml/MainView.fxml")));
        loader.setResources(I18nService.getBundle());
        Parent root = loader.load();
        mainController = loader.getController();

        Scene scene = new Scene(root, 1440, 900);
        // Dark fill (not JavaFX's default white): when a modal dialog blurs the
        // root, GaussianBlur fades the root's edges to transparent — over white
        // that reads as a bright band around the window; over this it's invisible.
        scene.setFill(javafx.scene.paint.Color.web("#0E1526"));
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/styles.css")).toExternalForm());

        mainScene = scene;
    }


    private void attachMainStage(Stage primaryStage) {
        primaryStage.setTitle(Settings.getGymName() + " - Reception Console");
        primaryStage.initStyle(StageStyle.UNDECORATED);

        Rectangle2D screenBounds = Screen.getPrimary().getBounds();


        primaryStage.setMinWidth(Math.min(MIN_WIDTH, screenBounds.getWidth()));
        primaryStage.setMinHeight(Math.min(MIN_HEIGHT, screenBounds.getHeight()));
        primaryStage.setScene(mainScene);

        primaryStage.setX(screenBounds.getMinX());
        primaryStage.setY(screenBounds.getMinY());
        primaryStage.setWidth(screenBounds.getWidth());
        primaryStage.setHeight(screenBounds.getHeight());


        mainController.getHeaderController().attachToStage(primaryStage);
        RestartAppService.mainStage = primaryStage;

        // A 401 that landed during startup (before this stage existed) — go
        // straight to the lockout screen instead of the app.
        if (keyRevoked) {
            lockOutForRevokedKey(primaryStage);
        } else {
            primaryStage.show();
        }
    }

    /**
     * Locks the running app behind the "trial ended" screen because Supabase
     * rejected our key with 401. Closes the secondary (camera) window, swaps the
     * lockout screen onto {@code stage}, and tears every background service down
     * off the FX thread so the swap is instant.
     */
    private void lockOutForRevokedKey(Stage stage) {
        if (RestartAppService.secondaryStage != null) {
            RestartAppService.secondaryStage.close();
            RestartAppService.secondaryStage = null;
        }
        new TrialExpiredScreen().takeOver(stage);
        new Thread(RestartAppService::releaseResources, "gymgate-lockout").start();
    }
}