package com.GymGate.UI.controller;

import ai.onnxruntime.OrtException;
import com.GymGate.Ai.recognition.RecognitionResult;
import com.GymGate.Ai.recognition.RecognitionStatus;
import com.GymGate.Camera.CameraManager;
import com.GymGate.UI.component.*;
import com.GymGate.UI.component.DialogEffects;
import com.GymGate.bussines.db.dao.AttendanceDao;
import com.GymGate.bussines.db.dao.EmbeddingDao;
import com.GymGate.bussines.db.dao.MemberDao;
import com.GymGate.bussines.db.dao.PaymentDao;
import com.GymGate.bussines.db.dao.ReminderDao;
import com.GymGate.bussines.db.entities.Member;
import com.GymGate.bussines.db.entities.MemberEmbedding;
import com.GymGate.bussines.db.entities.Payment;
import com.GymGate.bussines.db.entities.Seance;
import com.GymGate.bussines.db.entities.Plan;
import com.GymGate.bussines.models.CustomPlan;
import com.GymGate.bussines.models.Settings;
import com.GymGate.bussines.models.AttendanceRecord;
import com.GymGate.bussines.models.LatestAttendance;
import com.GymGate.bussines.models.PaymentRecord;
import com.GymGate.bussines.models.RegistrationData;
import com.GymGate.bussines.models.ValidationResult;
import com.GymGate.bussines.services.*;
import com.GymGate.bussines.util.Converter;
import com.GymGate.bussines.util.Mapper;
import com.GymGate.model.MemberInfo;
import com.GymGate.model.StatData;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.GymGate.bussines.services.PhotosService.loadPhoto;

public class HomeController implements RecognitionListener {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);
    @FXML private HBox actionButtonsContainer;
    @FXML private VBox leftColumn;
    @FXML private GridPane statsGrid;
    @FXML private VBox statsPanel;
    @FXML private Button statsRefreshButton;
    @FXML private FontIcon statsRefreshIcon;

    /** The stats panel's normal contents (header + grid), captured once so the
     *  drill-down's back arrow can restore them - see showStatDetail(). */
    private List<javafx.scene.Node> statsPanelDefaultContent;

    private MainCameraPanel cameraPanel;
    private MemberInfoCard memberInfoCard;
    private SecondaryCameraPanel secondaryCameraPanel;
    private CameraManager cameraManager;
    private final MemberDao memberDao = MemberDao.getInstance();
    private final EmbeddingDao embeddingDao = EmbeddingDao.getInstance();
    private final StatsService statsService = StatsService.getInstance();
    private RegisterMemberController registerMemberController;
    private final PaymentDao paymentDao = PaymentDao.getInstance();
    private MemberValidationService validationService;
    private final AttendanceDao attendanceDao = AttendanceDao.getInstance();
    private Stage registerMemberDialogStage;
    private final Popup notificationPopup = new Popup();
    private final Label notificationLabel = new Label();

    private final PauseTransition notificationTimer =
            new PauseTransition(Duration.seconds(2));

    private long lastUnknownNotificationTime = 0;
    private static final long UNKNOWN_NOTIFICATION_COOLDOWN_MS = 3_000;

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /** Stat cards keyed by their label (e.g. "Check-ins", "Renewed Subs"),
     *  so any card can be looked up and updated later - see updateStatValue()
     *  and getStatCard(). */
    private static final Map<String, StatCard> statCardsByLabel = new LinkedHashMap<>();

    @FXML
    public void initialize() throws OrtException {
        buildActionButtons();
        buildStatsGrid();
        statsRefreshButton.setOnAction(e -> refreshAllStats());
        statsPanelDefaultContent = List.copyOf(statsPanel.getChildren());
        // Leaving Home (NavigationManager swaps the page out) collapses any open
        // drill-down, so coming back always lands on the stat grid.
        statsPanel.sceneProperty().addListener((obs, was, is) -> {
            if (is == null) {
                showStatGrid();
            }
        });
        this.secondaryCameraPanel=new SecondaryCameraPanel();
        RecognitionResultPresenter.init(this,secondaryCameraPanel);
        this.validationService=MemberValidationService.getInstance();
        buildLeftColumn();
        CameraManager.init(cameraPanel,secondaryCameraPanel);
        this.cameraManager = CameraManager.getInstance();
        this.cameraManager.startCamera();
        this.cameraManager.startHealthMonitor();
        // The RegisterMember dialog (the app's largest FXML, plus an enrollment
        // camera panel) is built lazily on the first "Register" click — a
        // session with no new registrations never pays for it.
        notificationLabel.getStyleClass().add("camera-notification");
        notificationPopup.getContent().add(notificationLabel);
        notificationPopup.setAutoHide(false);
        notificationPopup.setAutoFix(true);
    }

    private void buildActionButtons() {
        ToggleGroup actionGroup = new ToggleGroup();

        PillActionButton registerMember = new PillActionButton("fas-user-plus", I18nService.get("Register"), true);
        registerMember.setToggleGroup(actionGroup);
        registerMember.setOnAction(e -> openRegisterMemberDialog());

        PillActionButton registerSeance = new PillActionButton("fas-ticket-alt", I18nService.get("Single_Session"), false);
        registerSeance.setToggleGroup(actionGroup);
        registerSeance.setOnAction(e -> {
            openSeanceSaleDialog();
            registerMember.setSelected(true);   // it's an action, not a mode — restore
        });

        actionButtonsContainer.getChildren().addAll(registerMember, registerSeance);
    }

    private void openSeanceSaleDialog() {
        Scene scene = leftColumn.getScene();
        Seance seance = SeanceSaleDialog.show(
                scene == null ? null : scene.getWindow(),
                scene == null ? null : scene.getRoot());
        if (seance == null) {
            return;
        }
        int amount = (int) seance.getPrice();
        paymentDao.insertSeance(seance.getId(), amount);
        updateStatValue(I18nService.get("Revenue"), amount);
        AppEvents.publish(AppEvents.Type.PAYMENT_ADDED);
    }

    private void initRegisterMemberDialog() {
        // Home's root gets detached/reattached to the scene graph every time
        // NavigationManager.show() swaps pages (contentArea.getChildren().setAll(...)),
        // which re-fires this sceneProperty() listener on every visit back to Home —
        // not just once at startup. Without this guard we'd rebuild the dialog,
        // controller, and EnrollmentCameraPanel each time, but
        // CameraCapturingService.addView() only ever binds the *first* panel it's
        // given, so every later dialog instance would sit on screen never receiving
        // frames (blank/blue). Guard so the real init only ever runs once.
        if (registerMemberController != null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RegisterMemberDialog.fxml"));
            loader.setResources(I18nService.getBundle());
            Parent dialogRoot = loader.load();
            registerMemberController = loader.getController();
            registerMemberController.setCameraManager(cameraManager);

            registerMemberDialogStage = new Stage(StageStyle.TRANSPARENT);
            registerMemberDialogStage.initOwner(leftColumn.getScene().getWindow());
            registerMemberDialogStage.initModality(Modality.APPLICATION_MODAL);

            Scene dialogScene = new Scene(DialogEffects.wrapForTransparentScene(dialogRoot));
            dialogScene.setFill(Color.TRANSPARENT);
            dialogScene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            registerMemberDialogStage.setScene(dialogScene);
            DialogEffects.preventShowFlash(registerMemberDialogStage);

            registerMemberController.setDialogStage(registerMemberDialogStage);
            Parent homeRoot = leftColumn.getScene().getRoot();
            registerMemberDialogStage.setOnShowing(e ->DialogEffects.applyBlurAndDim(homeRoot));
            registerMemberDialogStage.setOnHidden(e -> DialogEffects.clear(homeRoot));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openRegisterMemberDialog() {
        initRegisterMemberDialog();
        if (registerMemberController == null) {
            return; // FXML failed to load (already logged) — nothing to show
        }
        registerMemberController.prepareForShow();
        registerMemberDialogStage.showAndWait();

        Optional<RegistrationData> result = registerMemberController.getResult();
        Optional<Plan> plan = registerMemberController.getSelectedPlan();
        if (result.isEmpty()
                || plan.isEmpty()||registerMemberController.isCanceled()) {
            return;
        }
        Optional<Mat> middleFace=registerMemberController.getMiddleFaceMat();
        List<float[]> embeddings = registerMemberController.getCapturedEmbeddings();
        Optional<CustomPlan> customPlan=registerMemberController.getSelectedCustomPlan();
        RegistrationData data = result.get();
        Member member = Mapper.toMember(data);
        Plan selectedPlan=plan.get();
        if(customPlan.isPresent()){
            CustomPlan selectedCustomPlan=customPlan.get();
            member.setStartDate(selectedCustomPlan.getStartDate());
            member.setRemainingDays(selectedCustomPlan.getRemainingDays());
            member.setEndDate(selectedCustomPlan.getEndDate());
        } else  {
            member.setStartDate(LocalDate.now());
            member.setRemainingDays(selectedPlan.getTotalDays());
            member.setEndDate(LocalDate.now().plusMonths(selectedPlan.getDurationMonths()));
        }
        member.setPlanId(selectedPlan.getId());
        Member insertedMember = memberDao.insert(member);
       if(embeddings!=null&&!embeddings.isEmpty()){
           embeddingDao.insert(new MemberEmbedding(insertedMember.getId(), embeddings));
       }
        middleFace.ifPresent(mat -> PhotosService.uploadPhoto(mat, member.getId()));
        paymentDao.insert(new Payment(member.getId(), selectedPlan.getId(), selectedPlan.getPrice()));
        updateStatValue(I18nService.get("New_Members"), 1);
        updateStatValue(I18nService.get("Revenue"), selectedPlan.getPrice());
        AppEvents.publish(AppEvents.Type.MEMBER_ADDED, AppEvents.Type.PAYMENT_ADDED);
    }

    private void openRenewPlanDialog(int memberId) {
        try {

            Member member = memberDao.findById(memberId).orElse(null);

            if (member == null){
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RenewPlanDialog.fxml"));
            loader.setResources(I18nService.getBundle());
            Parent dialogRoot = loader.load();
            RenewPlanController controller = loader.getController();

            Stage dialogStage = new Stage(StageStyle.TRANSPARENT);
            dialogStage.initOwner(leftColumn.getScene().getWindow());
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            Scene dialogScene = new Scene(DialogEffects.wrapForTransparentScene(dialogRoot));
            dialogScene.setFill(Color.TRANSPARENT);
            dialogScene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            dialogStage.setScene(dialogScene);
            DialogEffects.preventShowFlash(dialogStage);
            controller.setDialogStage(dialogStage);

            Parent homeRoot = leftColumn.getScene().getRoot();
            dialogStage.setOnShowing(e -> DialogEffects.applyBlurAndDim(homeRoot));
            dialogStage.setOnHidden(e ->DialogEffects.clear(homeRoot));

            String fullName = Converter.capitalize(member.getFirstName()) + " " + Converter.capitalize(member.getLastName());
            controller.prepareForShow(fullName, member.getPlanId(), member.getSexe());

            dialogStage.showAndWait();

            controller.getResult().ifPresent(plan -> {
                boolean wasExpiring = countsAsExpiring(member);
                member.setPlanId(plan.getId());
                member.setStartDate(LocalDate.now());
                member.setEndDate(LocalDate.now().plusMonths(plan.getDurationMonths()));
                member.setRemainingDays(plan.getTotalDays());
                memberDao.update(member);
                // renewed — clear today's renewal reminder if it was logged
                ReminderDao.getInstance().deleteForMember(memberId);
                paymentDao.insert(new Payment(memberId,plan.getId(),plan.getPrice()));
                updateStatValue(I18nService.get("Renewed_Subs"),1);
                updateStatValue(I18nService.get("Revenue"),plan.getPrice());
                if (wasExpiring) {
                    updateStatValue(I18nService.get("Expires_Today"), -1);
                }
                AppEvents.publish(AppEvents.Type.MEMBER_UPDATED, AppEvents.Type.PAYMENT_ADDED);
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void buildLeftColumn() {
        cameraPanel = new MainCameraPanel();
        // Still grows to fill leftover vertical space (vgrow), same as
        // before — a rigid fixed height risked demanding more room than was
        // actually available and pushing the member card out of view. Only
        // a max-height cap, a bit taller than the previous 460, so it can
        // still shrink to fit when space is tight but reaches a bit higher
        // when there's room, keeping the member card reliably visible.
        cameraPanel.setMaxHeight(500);
        VBox.setVgrow(cameraPanel, Priority.ALWAYS);

        Optional<LatestAttendance> attendance = attendanceDao.findLatest();
        MemberInfo memberInfo;
        AtomicReference<Member> member=new AtomicReference<>();
        AtomicBoolean planActive = new AtomicBoolean(false);
        memberInfo = attendance.map(latestAttendance -> {
            ValidationResult result=validationService.validate(new RecognitionResult(latestAttendance.memberId(),1, RecognitionStatus.MATCHED));
            member.set(result.getMember());
            ValidationResult.ValidationStatus status = result.getStatus() == ValidationResult.ValidationStatus.ALREADY_CHECKED_IN
                    ? ValidationResult.ValidationStatus.SUCCESS
                    : result.getStatus();
            planActive.set(status == ValidationResult.ValidationStatus.SUCCESS);
            return new MemberInfo(
                    member.get().getId(),
                    Converter.capitalize(member.get().getFirstName()) + " " + Converter.capitalize(member.get().getLastName()),
                    member.get().getPhoneNumber(),
                    member.get().getPlanName(),
                    I18nService.get(status.name()),
                    String.valueOf(member.get().getEndDate()),
                    attendance.get().checkIn().toLocalTime().format(HHMM),
                    initialsOf(member.get().getFirstName(), member.get().getLastName()),
                    planActive.get()
            );
        }).orElseGet( ()->{
            planActive.set(true);
            return
                    new MemberInfo(0,
                            I18nService.get("No_Person"),
                            "0xxxxxxxxx",
                            "demo",
                            I18nService.get("SUCCESS"),
                            "",
                            LocalTime.now().format(HHMM),
                            initialsOf("No","Person"),
                            planActive.get()
                    );
        });

        memberInfoCard = new MemberInfoCard(memberInfo);

        memberInfoCard.setRenewVisible(!planActive.get());

        memberInfoCard.setOnRenew(this::openRenewPlanDialog);

        if (member.get() != null) {
            loadPhoto(member.get().getId())
                    .ifPresentOrElse(memberInfoCard::setPhoto, () -> memberInfoCard.setPhoto(null));

            if (member.get().getRemainingDays() != null) {
                memberInfoCard.setRemainingDays(member.get().getRemainingDays());
            }
        } else {
            memberInfoCard.setPhoto(null);
        }

        leftColumn.getChildren().addAll(cameraPanel, memberInfoCard);
    }

    private void buildStatsGrid() {
        statCardsByLabel.clear();

        List<StatData> stats = List.of(
                new StatData("fas-sign-in-alt", "icon-tile-blue", String.valueOf(statsService.getTodayChecksIn()), I18nService.get("Check-ins")),
                new StatData("fas-sync-alt", "icon-tile-green", String.valueOf(statsService.getTodayRenewedSubs()), I18nService.get("Renewed_Subs")),
                new StatData("fas-calendar-times", "icon-tile-orange", String.valueOf(statsService.getTodayExpiredMem()), I18nService.get("Expires_Today")),
                new StatData("fas-user-plus", "icon-tile-blue", String.valueOf(statsService.getTodayNewMem()), I18nService.get("New_Members"))
        );

        statsGrid.getColumnConstraints().clear();
        for (int col = 0; col < 2; col++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(50);
            statsGrid.getColumnConstraints().add(cc);
        }

        // Row 0 and 1 (the 4 normal cards) keep their natural/compact height.
        // Row 2 (Renewed Subs) absorbs ALL leftover vertical space in the grid.
        statsGrid.getRowConstraints().clear();
        RowConstraints row0 = new RowConstraints();
        row0.setVgrow(Priority.NEVER);
        RowConstraints row1 = new RowConstraints();
        row1.setVgrow(Priority.NEVER);
        RowConstraints wideRow = new RowConstraints();
        wideRow.setVgrow(Priority.ALWAYS);
        statsGrid.getRowConstraints().addAll(row0, row1, wideRow);

        int index = 0;
        for (StatData stat : stats) {
            StatCard card = new StatCard(stat);
            card.setOnActivate(() -> showStatDetail(stat.getLabel()));
            card.setOnRefresh(() -> recomputeStat(stat.getLabel(), card));
            statsGrid.add(card, index % 2, index / 2);
            statCardsByLabel.put(stat.getLabel(), card);
            index++;
        }
        StatData renewedSubsData = new StatData(
                "fas-dollar-sign", "icon-tile-green",
                String.valueOf(statsService.getTodayRevenue()),
                I18nService.get("Revenue")
        );
        StatCard renewedSubsCard = new StatCard(renewedSubsData, true);
        renewedSubsCard.setOnActivate(() -> showStatDetail(renewedSubsData.getLabel()));
        renewedSubsCard.setOnRefresh(() -> recomputeStat(renewedSubsData.getLabel(), renewedSubsCard));
        renewedSubsCard.setMaxHeight(Double.MAX_VALUE);
        statsGrid.add(renewedSubsCard, 0, 2, 2, 1);
        GridPane.setVgrow(renewedSubsCard, Priority.ALWAYS);
        statCardsByLabel.put(renewedSubsData.getLabel(), renewedSubsCard);
    }

    /**
     * The panel-wide refresh button next to the "Real-time" chip: spins its
     * icon and recomputes every tile at once via {@link #recomputeStat}, the
     * same per-tile logic each card's own corner button uses.
     */
    private void refreshAllStats() {
        SpinAnimation.spinOnce(statsRefreshIcon);
        statCardsByLabel.forEach(this::recomputeStat);
    }

    /**
     * Re-runs the single {@code StatsService} query behind one tile and sets
     * it to that fresh value — wired to each card's corner refresh button, for
     * pulling a stale-looking tile back in sync without waiting for whatever
     * event would normally bump it (or for the next full page reload).
     */
    private void recomputeStat(String label, StatCard card) {
        if (label.equals(I18nService.get("Check-ins"))) {
            card.setValue(statsService.getTodayChecksIn());
        } else if (label.equals(I18nService.get("Renewed_Subs"))) {
            card.setValue(statsService.getTodayRenewedSubs());
        } else if (label.equals(I18nService.get("Expires_Today"))) {
            card.setValue(statsService.getTodayExpiredMem());
        } else if (label.equals(I18nService.get("New_Members"))) {
            card.setValue(statsService.getTodayNewMem());
        } else if (label.equals(I18nService.get("Revenue"))) {
            card.setValue(statsService.getTodayRevenue());
        }
    }

    private static final DateTimeFormatter DAY_MONTH =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    /**
     * Swaps the whole stats panel for the drill-down list behind one card.
     * The list is fetched with the same rule that produced the card's number
     * (see StatsService / MemberDao), sorted the same way, so the count and
     * the list length always agree. The panel is restored by showStatGrid(),
     * wired to the drill-down's back arrow.
     */
    private void showStatDetail(String label) {
        List<StatDetailPanel.Row> rows = statDetailRows(label);
        StatDetailPanel panel = new StatDetailPanel(label, rows, this::showStatGrid);
        VBox.setVgrow(panel, Priority.ALWAYS);
        statsPanel.getChildren().setAll(panel);
    }

    private void showStatGrid() {
        if (statsPanelDefaultContent != null
                && !statsPanel.getChildren().equals(statsPanelDefaultContent)) {
            statsPanel.getChildren().setAll(statsPanelDefaultContent);
        }
    }

    private List<StatDetailPanel.Row> statDetailRows(String label) {
        List<StatDetailPanel.Row> rows = new ArrayList<>();

        if (label.equals(I18nService.get("Check-ins"))) {
            for (AttendanceRecord r : statsService.getTodayCheckInList()) {
                rows.add(new StatDetailPanel.Row(r.getId(), initialsOf(r.getFullName()),
                        r.getFullName(), null, r.getFormattedTime()));
            }
            return rows;
        }

        if (label.equals(I18nService.get("Revenue"))) {
            for (PaymentRecord p : statsService.getTodayPayments()) {
                rows.add(new StatDetailPanel.Row(p.getId(), initialsOf(p.getFullName()),
                        p.getFullName(), p.getPlanName(),
                        String.format(Locale.US, "+ %,d DZD", p.getAmount())));
            }
            return rows;
        }

        List<Member> members;
        boolean expiring = false;
        if (label.equals(I18nService.get("Expires_Today"))) {
            members = statsService.getTodayExpiringList();
            expiring = true;
        } else if (label.equals(I18nService.get("Renewed_Subs"))) {
            members = statsService.getTodayRenewedList();
        } else if (label.equals(I18nService.get("New_Members"))) {
            members = statsService.getTodayNewList();
        } else {
            return rows;
        }

        for (Member m : members) {
            String name = Converter.capitalize(m.getFirstName()) + " " + Converter.capitalize(m.getLastName());
            String trailing;
            if (expiring) {
                trailing = m.getEndDate() != null ? m.getEndDate().format(DAY_MONTH) : "";
            } else {
                trailing = m.getRemainingDays() != null
                        ? m.getRemainingDays() + " " + I18nService.get("days_left") : "";
            }
            rows.add(new StatDetailPanel.Row(m.getId(),
                    initialsOf(m.getFirstName(), m.getLastName()),
                    name, m.getPlanName(), trailing));
        }
        return rows;
    }

    /** First letter of the first two words of a display name, for the avatar. */
    private static String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return sb.toString();
    }

    public static void updateStatValue(String label, int v) {
        StatCard card = statCardsByLabel.get(label);
        if (card != null) {
            card.incrementValue(v);
        }
    }

    /** Mirrors MemberDao.count(EXPIRED): a member is in today's "Expiring"
     *  tally if their plan ends today, or their remaining days have hit zero
     *  and they attended today. Presentation-only — used to keep the live
     *  stat card in sync when a member renews. */
    private boolean countsAsExpiring(Member member) {
        LocalDate today = LocalDate.now();
        boolean endsToday = member.getEndDate() != null && member.getEndDate().isEqual(today);
        boolean zeroAfterVisit = member.getRemainingDays() != null
                && member.getRemainingDays() <= 0
                && attendanceDao.existToday(member.getId());
        return endsToday || zeroAfterVisit;
    }

    public void setCameraVisible(boolean visible) {
        cameraPanel.setPrivacyMode(!visible);
    }

    private void showNotification(String text, boolean success) {

        notificationLabel.setText(text);

        notificationLabel.getStyleClass().removeAll(
                "camera-notification-error",
                "camera-notification-success"
        );

        notificationLabel.getStyleClass().add(
                success ? "camera-notification-success"
                        : "camera-notification-error"
        );

        Bounds bounds = cameraPanel.localToScreen(cameraPanel.getBoundsInLocal());
        if (bounds == null)
            return;

        double x = bounds.getMinX()
                + (bounds.getWidth() - notificationLabel.prefWidth(-1)) / 2;
        double y = bounds.getMinY() + 20;

        notificationPopup.hide();
        notificationPopup.show(cameraPanel.getScene().getWindow(), x, y);

        notificationTimer.stop();
        notificationTimer.setOnFinished(e -> notificationPopup.hide());
        notificationTimer.playFromStart();
    }

    private void showUnknownMemberNotification() {

        long now = System.currentTimeMillis();

        if (now - lastUnknownNotificationTime < UNKNOWN_NOTIFICATION_COOLDOWN_MS)
            return;

        lastUnknownNotificationTime = now;

        showNotification(I18nService.get("Member_not_found"),false);
    }

    private String initialsOf(String firstName, String lastName) {
        String a = firstName != null && !firstName.isBlank() ? firstName.trim().substring(0, 1) : "";
        String b = lastName != null && !lastName.isBlank() ? lastName.trim().substring(0, 1) : "";
        return (a + b).toUpperCase();
    }

    private void updateMemberCard(ValidationResult result) {
        Member member = result.getMember();
        boolean planActive = result.getStatus()== ValidationResult.ValidationStatus.SUCCESS;


        memberInfoCard.setMember(new MemberInfo(
                member.getId(),
                Converter.capitalize(member.getFirstName()) + " " + Converter.capitalize(member.getLastName()),
                member.getPhoneNumber(),
                member.getPlanName(),
                I18nService.get(result.getStatus().name()),
                String.valueOf(member.getEndDate()),
                LocalTime.now().format(HHMM),
                initialsOf(member.getFirstName(), member.getLastName()),
                planActive
        ));

        if(member.getRemainingDays()!=null) {
            memberInfoCard.setRemainingDays(member.getRemainingDays());
        }


        loadPhoto(member.getId())
                .ifPresentOrElse(memberInfoCard::setPhoto, () -> memberInfoCard.setPhoto(null));

        memberInfoCard.setRenewVisible(!planActive);
    }

    /**
     * Not confident enough to check in outright — the closest candidate is
     * offered to staff for a manual accept/reject instead of a bare "not
     * found" (see MemberValidationService / ValidationResult.POSSIBLE_MATCH).
     * Accepting re-runs the normal MATCHED pipeline for real (attendance +
     * remaining-days decrement); rejecting is a pure UI dismissal — the card
     * itself already reverts to whatever it showed before.
     */
    private void showPossibleMatch(Member candidate) {
        MemberInfo candidateInfo = new MemberInfo(
                candidate.getId(),
                Converter.capitalize(candidate.getFirstName()) + " " + Converter.capitalize(candidate.getLastName()),
                candidate.getPhoneNumber(),
                candidate.getPlanName(),
                I18nService.get(ValidationResult.ValidationStatus.POSSIBLE_MATCH.name()),
                String.valueOf(candidate.getEndDate()),
                LocalTime.now().format(HHMM),
                initialsOf(candidate.getFirstName(), candidate.getLastName()),
                false
        );

        Image candidatePhoto = loadPhoto(candidate.getId()).orElse(null);
        int candidateId = candidate.getId();

        memberInfoCard.showPendingCandidate(candidateInfo, candidatePhoto,
                () -> validationService.confirmPossibleMatch(candidateId),
                () -> { });
    }

    @Override
    public void onRecognition(ValidationResult result) {
        if (result.getStatus() == ValidationResult.ValidationStatus.MEMBER_NOT_FOUND) {
            showUnknownMemberNotification();
        } else if (result.getStatus() == ValidationResult.ValidationStatus.POSSIBLE_MATCH) {
            showPossibleMatch(result.getMember());
        } else {
            if (result.getStatus() == ValidationResult.ValidationStatus.SUCCESS) {
                updateStatValue(I18nService.get("Check-ins"), 1);

                // This check-in spent the member's last day — they now show up
                // in getTodayExpiredMem() (remaining_days <= 0 AND attended
                // today), so bump the "Expires Today" card live to match.
                Member member = result.getMember();
                if (member.getRemainingDays() != null && member.getRemainingDays() <= 0) {
                    updateStatValue(I18nService.get("Expires_Today"), 1);
                }
                AppEvents.publish(AppEvents.Type.ATTENDANCE_ADDED, AppEvents.Type.MEMBER_UPDATED);
            }else if(result.getStatus()== ValidationResult.ValidationStatus.ALREADY_CHECKED_IN){
                showNotification(I18nService.get("Welcome_Again"),true);
                return;
            }
            updateMemberCard(result);
        }
    }

}
