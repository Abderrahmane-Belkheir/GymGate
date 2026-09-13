package com.GymGate.UI.controller;



import ai.onnxruntime.OrtException;
import com.GymGate.Ai.FaceProcessor;
import com.GymGate.Ai.recognition.RecognitionConfig;
import com.GymGate.Ai.recognition.RecognitionResult;
import com.GymGate.Ai.recognition.RecognitionStatus;
import com.GymGate.Camera.CameraManager;
import com.GymGate.UI.component.EnrollmentCameraPanel;
import com.GymGate.UI.component.PlanSelectionPanel;
import com.GymGate.UI.component.SecondaryCameraPanel;
import com.GymGate.bussines.db.entities.Plan;
import com.GymGate.bussines.models.CustomPlan;
import com.GymGate.bussines.models.RegistrationData;
import com.GymGate.bussines.models.Sexe;
import com.GymGate.bussines.services.I18nService;
import com.GymGate.bussines.services.MemberValidationService;
import com.GymGate.bussines.util.SoundUtil;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;



public class RegisterMemberController {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d+$");
    private static final Logger log = LoggerFactory.getLogger(RegisterMemberController.class);

    /** Enrollment poses captured in order, after the form step. */
    private static final String[] POSE_TITLES = {I18nService.get("Center_Shot"), I18nService.get("Slight_Left"), I18nService.get("Slight_Right")};
    private static final String[] POSE_INSTRUCTIONS = {
            I18nService.get("Look_straight_at_the_camera"),
            I18nService.get("Turn_head_slightly_left"),
            I18nService.get("Turn_head_slightly_right")
    };

    private static final Duration ALERT_DURATION = Duration.seconds(3);
    private static final Duration POSE_FADE_DURATION = Duration.millis(220);
    private static final Duration POSE_ADVANCE_PAUSE = Duration.millis(650);

    @FXML private Label dialogTitleLabel;
    @FXML private Label dialogSubtitleLabel;
    @FXML private Label footerNoteLabel;

    @FXML private VBox formPage;
    @FXML private VBox cameraPageContainer;

    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField phoneField;
    @FXML private ToggleGroup genderToggleGroup;
    @FXML private ToggleButton maleToggle;
    @FXML private ToggleButton femaleToggle;
    @FXML private CheckBox skipPhotosCheckBox;
    @FXML private Button proceedButton;
    @FXML private Button cancelButton;
    @FXML private Button closeButton;

    @FXML private Button takePhotoButton;
    @FXML private HBox reviewButtonsRow;
    @FXML private Button retakeButton;
    @FXML private Button confirmButton;

    @FXML private VBox planPage;
    @FXML private VBox planListContainer;
    @FXML private Button finishRegistrationButton;

    @FXML private VBox customPlanPage;
    @FXML private TextField remainingDaysField;
    @FXML private ComboBox<Month> startMonthCombo;
    @FXML private ComboBox<Integer> startDayCombo;
    @FXML private ComboBox<Month> endMonthCombo;
    @FXML private ComboBox<Integer> endDayCombo;
    @FXML private VBox customPlanListContainer;
    @FXML private Button confirmCustomPlanButton;
    @FXML private Button backToPlansButton;

    private static final String CUSTOM_PLAN_NAME = I18nService.get("Custom");

    private boolean canceled;
    private final EnrollmentCameraPanel enrollmentCameraPanel = new EnrollmentCameraPanel();
    private final PlanSelectionPanel customBasePlanPanel = new PlanSelectionPanel();


    private final Label statusAlertLabel = new Label();
    private PauseTransition statusAlertHideTransition;

    private Stage dialogStage;
    private CameraManager cameraManager;

    private RegistrationData formResult;

    private final FaceProcessor processor;
    private final MemberValidationService validationService;
    private final PlanSelectionPanel planSelectionPanel = new PlanSelectionPanel();

    private int currentPoseIndex = 0;
    private final List<float[]> enrollmentEmbeddings = new ArrayList<>();


    private Image pendingPhoto;
    private Mat liveMat;
    private Mat pendingFaceMat;
    private Mat middleFaceMat;
    private Plan selectedPlan;
    private CustomPlan customPlan;
    /** The real Plan picked inside the custom-plan card (null while unset/invalid). */
    private Plan customBasePlan;
    /** True when the member was registered via the "Skip Photos" checkbox
     *  (no face enrollment, no plan selection performed here). Callers can
     *  use this to decide what to do afterward, e.g. assign a default plan. */
    private boolean photosSkipped;
    /** True while this controller is being used for registering a new member;
     *  false when it's being reused to update an existing member. Controlled
     *  by the code driving the dialog (not the checkbox) — defaults to true,
     *  and callers switch it off via markAsUpdate() when reusing the dialog
     *  for an update flow. */
    private boolean registrationFlow = true;
    /** Independent of the "Skip Photos" checkbox — controlled by the code
     *  driving the dialog. When true, skipping photos also skips plan
     *  selection (straight to "All Set"); when false (default), skipping
     *  photos still shows the plan-selection step. */
    private boolean skipPlanSelection = false;

    public RegisterMemberController() throws OrtException {
        this.processor = FaceProcessor.getInstance();
        this.validationService = MemberValidationService.getInstance();
    }

    @FXML
    private void initialize() {
        BooleanBinding formInvalid = Bindings.createBooleanBinding(
                () -> !isFormValid(),
                firstNameField.textProperty(),
                lastNameField.textProperty(),
                phoneField.textProperty()
        );
        proceedButton.disableProperty().bind(formInvalid);

        // Male/Female is a real switch, not a deselectable pair — without this,
        // clicking the already-selected option turns it off and leaves neither chosen.
        genderToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });

        proceedButton.setOnAction(e -> {
            if (skipPhotosCheckBox.isSelected()) {
                skipPhotosAndFinish();
            } else {
                showPhotoCaptureStep();
            }
        });
        cancelButton.setOnAction(e -> {
            canceled=true;
            discardPending();
            closeDialog();
        });
        closeButton.setOnAction(e -> {
            canceled=true;
            discardPending();
            closeDialog();
        });

        takePhotoButton.setOnAction(e -> {
            try {
                capturePhoto();
            } catch (OrtException ex) {
                throw new RuntimeException(ex);
            }
        });
        retakeButton.setOnAction(e -> retakePhoto());
        confirmButton.setOnAction(e -> {
            try {
                confirmPhoto();
            } catch (OrtException ex) {
                throw new RuntimeException(ex);
            }
        });

        finishRegistrationButton.setOnAction(e -> finishRegistration());
        finishRegistrationButton.setDisable(true);

        planListContainer.getChildren().add(planSelectionPanel);
        planSelectionPanel.setOnSelectionChanged(() ->
                finishRegistrationButton.setDisable(planSelectionPanel.getSelectedPlan().isEmpty()));

        cameraPageContainer.getChildren().add(0, enrollmentCameraPanel); // above the buttons

        // --- Custom plan card ---
        customPlanListContainer.getChildren().add(customBasePlanPanel);
        customBasePlanPanel.setOnSelectionChanged(() -> {
            customBasePlan = customBasePlanPanel.getSelectedPlan()
                    .filter(p -> !isCustomPlanOption(p))
                    .orElse(null);
            updateCustomPlanConfirmState();
        });

        setupDateCombos();
        remainingDaysField.textProperty().addListener((obs, o, n) -> updateCustomPlanConfirmState());
        startMonthCombo.valueProperty().addListener((obs, o, n) -> {
            refreshDayOptions(startMonthCombo, startDayCombo);
            updateCustomPlanConfirmState();
        });
        endMonthCombo.valueProperty().addListener((obs, o, n) -> {
            refreshDayOptions(endMonthCombo, endDayCombo);
            updateCustomPlanConfirmState();
        });
        startDayCombo.valueProperty().addListener((obs, o, n) -> updateCustomPlanConfirmState());
        endDayCombo.valueProperty().addListener((obs, o, n) -> updateCustomPlanConfirmState());

        confirmCustomPlanButton.setOnAction(e -> confirmCustomPlan());
        backToPlansButton.setOnAction(e -> showPlanSelectionStep());

        statusAlertLabel.setWrapText(true);
        statusAlertLabel.setVisible(false);
        statusAlertLabel.setManaged(false);
        statusAlertLabel.setStyle(
                "-fx-background-radius: 8; -fx-padding: 8 14; -fx-font-weight: bold;"
        );
        cameraPageContainer.getChildren().add(1, statusAlertLabel); // between camera feed and buttons

        showFormStep(null,null,null);
    }

    private Sexe selectedSexe() {
        return maleToggle.isSelected() ? Sexe.MALE : Sexe.FEMALE;
    }

    private boolean isFormValid() {
        String first = firstNameField.getText();
        String last = lastNameField.getText();
        String phone = phoneField.getText();

        boolean namesValid = first != null && !first.isBlank()
                && last != null && !last.isBlank();
        // Phone is optional — accept it empty, otherwise it must look like a number.
        boolean phoneValid = phone == null || phone.isBlank()
                || PHONE_PATTERN.matcher(phone.trim()).matches();

        return namesValid && phoneValid;
    }

    // ---------------------------------------------------------------
    // Step visibility helper
    // ---------------------------------------------------------------

    private void resizeDialogToContent() {
        if (dialogStage != null) {
            Platform.runLater(() -> {
                Parent root = dialogStage.getScene().getRoot();
                // Force CSS + layout to fully resolve first. Without this,
                // sizeToScene() can measure preferred height before newly
                // added children (e.g. the plan tiles just loaded into the
                // custom-plan card) have a resolved CSS pass, undersizing
                // the stage. On this TRANSPARENT stage the overflow still
                // paints at first, then gets clipped away the moment any
                // repaint (e.g. a hover) forces the window to reclip to its
                // real, too-short frame.
                root.applyCss();
                root.layout();
                dialogStage.sizeToScene();
            });
        }
    }

    private void showFormStep(String first,String last,String phone) {
        dialogTitleLabel.setText(registrationFlow?I18nService.get("New_Member"):I18nService.get("Existing_Member"));
        dialogSubtitleLabel.setText(registrationFlow?I18nService.get("This_person_isn't_registered_yet._Enter_their_details_to_continue"):I18nService.get("This_person_is_registered._Enter_their_new_details_to_continue"));
        footerNoteLabel.setText(I18nService.get("Face_enrollment_will_be_completed_in_the_next_step."));
        firstNameField.setText(first);
        lastNameField.setText(last);
        phoneField.setText(phone);
        cameraPageContainer.setVisible(false);
        cameraPageContainer.setManaged(false);
        planPage.setVisible(false);
        planPage.setManaged(false);
        customPlanPage.setVisible(false);
        customPlanPage.setManaged(false);
        formPage.setVisible(true);
        formPage.setManaged(true);

        resizeDialogToContent();
    }

    /** Kicks off the 3-shot enrollment sequence (center -> left -> right). */
    private void showPhotoCaptureStep() {
        formResult = new RegistrationData(
                firstNameField.getText().trim(),
                lastNameField.getText().trim(),
                phoneField.getText().trim(),
                selectedSexe()
        );

        currentPoseIndex = 0;
        enrollmentEmbeddings.clear();

        formPage.setVisible(false);
        formPage.setManaged(false);
        cameraPageContainer.setVisible(true);
        cameraPageContainer.setManaged(true);
        cameraManager.registrationView();
        updatePoseInstructions();
        resetPhotoCapture();
        resizeDialogToContent();
    }

    /** "Skip Photos" checked on the form step: bypass face enrollment entirely.
     *  Whether plan selection is also skipped is independent of the checkbox —
     *  controlled by skipPlanSelection, set by the surrounding code. */
    private void skipPhotosAndFinish() {
        formResult = new RegistrationData(
                firstNameField.getText().trim(),
                lastNameField.getText().trim(),
                phoneField.getText().trim(),
                selectedSexe()
        );

        currentPoseIndex = 0;
        enrollmentEmbeddings.clear();
        selectedPlan = null;
        photosSkipped = true;

        formPage.setVisible(false);
        formPage.setManaged(false);

        if (skipPlanSelection) {
            completeRegistrationFlow();
        } else {
            showPlanSelectionStep();
        }
    }

    private void updatePoseInstructions() {
        dialogTitleLabel.setText(I18nService.get("Capture_Photo") +" — " + POSE_TITLES[currentPoseIndex]);
        dialogSubtitleLabel.setText(POSE_INSTRUCTIONS[currentPoseIndex]);
        footerNoteLabel.setText(I18nService.get("Shot")+" " + (currentPoseIndex + 1) + " of " + POSE_TITLES.length
                + " — "+I18nService.get("retake_if_needed"));
    }

    /** The fixed public-facing-screen view, if this build has a second monitor —
     *  empty (and every caller a no-op) otherwise. */
    private java.util.Optional<SecondaryCameraPanel> secondaryPanel() {
        if (cameraManager == null) {
            return java.util.Optional.empty();
        }
        return cameraManager.secondaryView() instanceof SecondaryCameraPanel scp
                ? java.util.Optional.of(scp)
                : java.util.Optional.empty();
    }

    private void resetPhotoCapture() {
        discardPendingPhoto();
        enrollmentCameraPanel.resumeLiveFeed();
        secondaryPanel().ifPresent(SecondaryCameraPanel::resumeLiveFeed);

        reviewButtonsRow.setVisible(false);
        reviewButtonsRow.setManaged(false);
        takePhotoButton.setVisible(true);
        takePhotoButton.setManaged(true);

        resizeDialogToContent();
    }

    private void capturePhoto() throws OrtException {
        Image frame = enrollmentCameraPanel.getLastLiveFrame();
        Mat liveMat = enrollmentCameraPanel.getLastLiveMat();
        if (frame == null || liveMat == null || liveMat.empty()) {
            return;
        }

        processor.detect(liveMat).ifPresentOrElse(
                faceDetectionResult -> {
                    SoundUtil.playCameraShutter();
                    // MatConverter reuses a single WritableImage across every
                    // camera tick — "frame" is the SAME mutable object every
                    // call, with its pixel buffer overwritten in place, not a
                    // fresh Image per frame. Freezing a preview on that
                    // reference without copying it out first is a no-op: the
                    // very next live tick silently repaints the pixels
                    // underneath whatever ImageView is still holding it. Snapshot
                    // into an independent copy so the frozen shot genuinely
                    // stops changing.
                    Image frozenShot = snapshotImage(frame);
                    pendingPhoto = frozenShot;
                    this.liveMat = liveMat.clone();
                    pendingFaceMat = faceDetectionResult.alignedFaceMat;
                    enrollmentCameraPanel.showFrozenImage(frozenShot);
                    secondaryPanel().ifPresent(scp -> scp.showFrozenImage(frozenShot));
                    takePhotoButton.setVisible(false);
                    takePhotoButton.setManaged(false);
                    reviewButtonsRow.setVisible(true);
                    reviewButtonsRow.setManaged(true);
                    resizeDialogToContent();
                },
                () -> showTransientAlert(I18nService.get("No_face_detected._Center_the_face_in_the_frame_and_try_again"))
        );
    }

    private void retakePhoto() {
        resetPhotoCapture();
    }

    /** Copies a camera-feed Image out into an independent, never-mutated
     *  WritableImage — see the comment in {@link #capturePhoto()} for why a
     *  raw reference to the live-feed Image can't be frozen on-screen as is. */
    private static Image snapshotImage(Image source) {
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        WritableImage copy = new WritableImage(width, height);
        copy.getPixelWriter().setPixels(0, 0, width, height, source.getPixelReader(), 0, 0);
        return copy;
    }

    private void confirmPhoto() throws OrtException {
        if (pendingFaceMat == null || pendingPhoto == null) return;

        RecognitionResult recognitionResult = processor.recognize(pendingFaceMat, true);


        // Block a new registration only on a CONFIRMED strong match against an
        // existing member — not on the recognition-time bar, which is set low
        // on purpose and would bounce genuinely new members who merely resemble
        // someone. AMBIGUOUS / near-threshold results fall through and the shot
        // is still captured.
        boolean alreadyRegistered = registrationFlow
                && recognitionResult.getStatus() == RecognitionStatus.MATCHED
                && recognitionResult.getScore() >= RecognitionConfig.active().enrollmentDedupThreshold();
        if (alreadyRegistered) {
            showTransientAlert(I18nService.get("This_person_appears_to_already_be_registered"));
            retakePhoto();
            return;
        }

        acceptEnrollmentShot(recognitionResult);
    }

    private void acceptEnrollmentShot(RecognitionResult recognitionResult) {
        float[] embedding = recognitionResult.getEmbedding();
        if (embedding != null) {
            enrollmentEmbeddings.add(embedding);
        }

        if (currentPoseIndex == 0 && liveMat != null && !liveMat.empty()) {
            // Simple and robust: store the whole live frame, just downscaled -
            // no dependency on boundingBox being in the same coordinate space
            // as liveMat, which was the source of the earlier crash/empty-Mat
            // issues. Aspect ratio is preserved so widescreen camera frames
            // don't get squashed into a square.
            int maxDimension = 256;
            double scale = Math.min(
                    (double) maxDimension / liveMat.cols(),
                    (double) maxDimension / liveMat.rows()
            );
            Size targetSize = new Size(
                    Math.round(liveMat.cols() * scale),
                    Math.round(liveMat.rows() * scale)
            );

            middleFaceMat = new Mat();
            Imgproc.resize(liveMat, middleFaceMat, targetSize, 0, 0, Imgproc.INTER_AREA);
        } else if (currentPoseIndex == 0) {
            // If this fires, liveMat was already null/empty at the moment of use -
            // meaning it was empty even at capture time in capturePhoto(), not
            // something that went empty afterward. That points to
            // enrollmentCameraPanel.getLastLiveMat() itself returning an empty
            // Mat (e.g. camera not fully started/frame not yet grabbed).
            log.warn("Skipping display-photo capture: liveMat was {} at use time",
                    liveMat == null ? "null" : "empty");
        }

        if (pendingFaceMat != null) {
            pendingFaceMat.release();
        }
        pendingPhoto = null;
        pendingFaceMat = null;

        // Released here (not left to resetPhotoCapture) because the final pose
        // never calls resetPhotoCapture again — it goes straight to
        // finishEnrollment() — so relying on that path would leak this Mat
        // for the whole plan-selection/finish step.
        if (liveMat != null) {
            liveMat.release();
            liveMat = null;
        }


        if (currentPoseIndex < POSE_TITLES.length - 1) {
            currentPoseIndex++;
            advanceToNextPoseSmoothly();
        } else {
            finishEnrollment();
        }
    }

    /** Fades the camera card out, swaps in the next pose's instructions and a
     *  resumed live feed, then fades back in - no extra click required. */
    private void advanceToNextPoseSmoothly() {
        FadeTransition fadeOut = new FadeTransition(POSE_FADE_DURATION, cameraPageContainer);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.15);
        fadeOut.setOnFinished(e -> {
            updatePoseInstructions();
            resetPhotoCapture();

            FadeTransition fadeIn = new FadeTransition(POSE_FADE_DURATION, cameraPageContainer);
            fadeIn.setFromValue(0.15);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        fadeOut.play();
    }
    private void showPlanSelectionStep() {
        dialogTitleLabel.setText(I18nService.get("Choose_a_Plan"));
        dialogSubtitleLabel.setText(I18nService.get("Select_a_membership_plan_to_complete_registration"));
        footerNoteLabel.setText("");

        planSelectionPanel.loadPlans(true, selectedSexe());
        finishRegistrationButton.setDisable(true);

        customPlanPage.setVisible(false);
        customPlanPage.setManaged(false);
        planPage.setVisible(true);
        planPage.setManaged(true);

        resizeDialogToContent();
    }
    /** All poses captured. New registration continues to plan selection; an
     *  update flow (markAsUpdate → skipPlanSelection) goes straight to "All Set",
     *  same as the "Skip Photos" path does. */
    private void finishEnrollment() {
        cameraPageContainer.setVisible(false);
        cameraPageContainer.setManaged(false);
        // The camera page (and its EnrollmentCameraPanel) is hidden from here on,
        // but the secondary/public screen is a separate monitor entirely — left
        // frozen, it would stay stuck on the last captured shot through plan
        // selection and beyond, all the way until the dialog closes. Resume both
        // now, the moment the last pose is confirmed.
        enrollmentCameraPanel.resumeLiveFeed();
        secondaryPanel().ifPresent(SecondaryCameraPanel::resumeLiveFeed);
        if (skipPlanSelection) {
            completeRegistrationFlow();
        } else {
            showPlanSelectionStep();
        }
    }


    private void finishRegistration() {
        selectedPlan = planSelectionPanel.getSelectedPlan().orElse(null);

        if (selectedPlan != null && isCustomPlanOption(selectedPlan)) {
            showCustomPlanStep();
            return;
        }

        planPage.setVisible(false);
        planPage.setManaged(false);

        completeRegistrationFlow();
    }

    private boolean isCustomPlanOption(Plan plan) {
        return plan != null && CUSTOM_PLAN_NAME.equalsIgnoreCase(plan.getName());
    }

    // ---------------------------------------------------------------
    // Custom plan card
    // ---------------------------------------------------------------

    private void showCustomPlanStep() {
        dialogTitleLabel.setText(I18nService.get("Custom_Plan"));
        dialogSubtitleLabel.setText(I18nService.get("Set_the_custom_membership_details_for_this_member"));
        footerNoteLabel.setText("");

        customBasePlan = null;
        remainingDaysField.clear();
        setupDateCombos();
        customBasePlanPanel.loadPlans(false, selectedSexe());
        updateCustomPlanConfirmState();

        planPage.setVisible(false);
        planPage.setManaged(false);
        customPlanPage.setVisible(true);
        customPlanPage.setManaged(true);

        resizeDialogToContent();
    }

    private void setupDateCombos() {
        startMonthCombo.getItems().setAll(Month.values());
        endMonthCombo.getItems().setAll(Month.values());
        startMonthCombo.setConverter(monthConverter());
        endMonthCombo.setConverter(monthConverter());

        LocalDate today = LocalDate.now();
        startMonthCombo.setValue(today.getMonth());
        refreshDayOptions(startMonthCombo, startDayCombo);
        startDayCombo.setValue(today.getDayOfMonth());

        LocalDate defaultEnd = today.plusMonths(1);
        endMonthCombo.setValue(defaultEnd.getMonth());
        refreshDayOptions(endMonthCombo, endDayCombo);
        endDayCombo.setValue(defaultEnd.getDayOfMonth());
    }

    private StringConverter<Month> monthConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Month month) {
                return month == null ? "" : month.getDisplayName(TextStyle.FULL, Locale.getDefault());
            }

            @Override
            public Month fromString(String string) {
                return null; // combo is selection-only, no free text entry
            }
        };
    }

    /** Rebuilds the day combo's options for the number of days in the
     *  currently selected month (current year), keeping the current day
     *  selection if it's still valid. */
    private void refreshDayOptions(ComboBox<Month> monthCombo, ComboBox<Integer> dayCombo) {
        Month month = monthCombo.getValue();
        if (month == null) return;

        int daysInMonth = YearMonth.of(LocalDate.now().getYear(), month).lengthOfMonth();
        Integer current = dayCombo.getValue();

        dayCombo.getItems().setAll(IntStream.rangeClosed(1, daysInMonth).boxed().toList());
        dayCombo.setValue(current != null && current <= daysInMonth ? current : 1);
    }

    private void updateCustomPlanConfirmState() {
        boolean daysValid = isPositiveInteger(remainingDaysField.getText());
        boolean datesReady = startMonthCombo.getValue() != null && startDayCombo.getValue() != null
                && endMonthCombo.getValue() != null && endDayCombo.getValue() != null;
        confirmCustomPlanButton.setDisable(!(daysValid && datesReady && customBasePlan != null));
    }

    private boolean isPositiveInteger(String text) {
        if (text == null || text.isBlank()) return false;
        try {
            return Integer.parseInt(text.trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void confirmCustomPlan() {
        if (customBasePlan == null || !isPositiveInteger(remainingDaysField.getText())) {
            return;
        }

        int remainingDays = Integer.parseInt(remainingDaysField.getText().trim());
        int year = LocalDate.now().getYear();
        LocalDate startDate = LocalDate.of(year, startMonthCombo.getValue(), startDayCombo.getValue());
        LocalDate endDate = LocalDate.of(year, endMonthCombo.getValue(), endDayCombo.getValue());

        if (!endDate.isAfter(startDate)) {
            showTransientAlert(I18nService.get("End_date_must_be_after_the_start_date"));
            return;
        }

        customPlan = new CustomPlan(startDate,  endDate,remainingDays);
        // The custom plan is billed/assigned against whichever real plan was
        // picked in this card, not the "Custom" placeholder from planPage.
        selectedPlan = customBasePlan;

        customPlanPage.setVisible(false);
        customPlanPage.setManaged(false);

        completeRegistrationFlow();
    }

    private void completeRegistrationFlow() {
        dialogTitleLabel.setText(I18nService.get("All_Set"));
        dialogSubtitleLabel.setText(I18nService.get("Enrollment_complete"));
        footerNoteLabel.setText(I18nService.get("Finishing_up"));

        resizeDialogToContent();

        PauseTransition pause = new PauseTransition(POSE_ADVANCE_PAUSE);
        pause.setOnFinished(e -> closeDialog());
        pause.play();
        enrollmentCameraPanel.close();
    }

    /** Releases only the in-progress single-shot capture — used between poses,
     *  where middleFaceMat (captured at pose 0) must survive. */
    private void discardPendingPhoto() {
        pendingPhoto = null;
        if (pendingFaceMat != null) {
            pendingFaceMat.release();
            pendingFaceMat = null;
        }
        if (liveMat != null) {
            liveMat.release();
            liveMat = null;
        }
    }

    /** Full teardown — abandons the whole enrollment attempt, including the
     *  already-captured middleFaceMat. Only call this when the session is
     *  actually ending (cancel/close, or prepareForShow() starting a fresh one). */
    private void discardPending() {
        discardPendingPhoto();
        if (middleFaceMat != null) {
            middleFaceMat.release();
            middleFaceMat = null;
        }
    }

    // ---------------------------------------------------------------
    // Transient alert banner (no-face / already-registered) - debounced so
    // repeated clicks reset the timer instead of stacking alerts, and the
    // Take Photo button is disabled for the duration so it can't be spammed.
    // ---------------------------------------------------------------

    private void showTransientAlert(String message) {
        statusAlertLabel.setText(message);
        statusAlertLabel.setStyle(
                "-fx-background-color: #fdecea; -fx-text-fill: #b3261e;"
                        + "-fx-background-radius: 8; -fx-padding: 8 14; -fx-font-weight: bold;"
        );
        statusAlertLabel.setVisible(true);
        statusAlertLabel.setManaged(true);
        takePhotoButton.setDisable(true);

        if (statusAlertHideTransition == null) {
            statusAlertHideTransition = new PauseTransition(ALERT_DURATION);
            statusAlertHideTransition.setOnFinished(e -> hideTransientAlert());
        }
        statusAlertHideTransition.stop();
        statusAlertHideTransition.playFromStart();
    }

    private void hideTransientAlert() {
        statusAlertLabel.setVisible(false);
        statusAlertLabel.setManaged(false);
        takePhotoButton.setDisable(false);
    }


    /**
     * Shared exit path for both the success flow (finishRegistration -> pause ->
     * closeDialog) and the cancel/X flow. It must NOT release middleFaceMat here:
     * on success, HomeController.openRegisterMemberDialog() reads
     * getMiddleFaceMat() right after registerMemberDialogStage.showAndWait()
     * returns, which only happens once dialogStage.hide() below fires — releasing
     * the Mat in this method would null it out before the caller ever sees it.
     * Callers that mean to abandon the in-progress capture (Cancel, the X
     * button) call discardPending() themselves before invoking this.
     */
    private void closeDialog() {
        if (cameraManager != null) {
            secondaryPanel().ifPresent(SecondaryCameraPanel::resumeLiveFeed);
            cameraManager.mainView();
            enrollmentCameraPanel.close();
        }
        if (statusAlertHideTransition != null) {
            statusAlertHideTransition.stop();
        }
        hideTransientAlert();
        if (dialogStage != null) {
            dialogStage.hide();
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
        cameraManager.attachExtraView(enrollmentCameraPanel);
    }

    public void prepareForShow(String first,String last,String phone) {
        prepareForShowHelper();
        showFormStep(first,last,phone);
    }

    public void prepareForShow(){
        prepareForShowHelper();
        showFormStep("","","");
    }

    private void prepareForShowHelper(){
        formResult = null;
        currentPoseIndex = 0;
        enrollmentEmbeddings.clear();
        selectedPlan = null;
        customPlan = null;
        customBasePlan = null;
        photosSkipped = false;
        canceled=false;
        discardPending();

        firstNameField.clear();
        lastNameField.clear();
        phoneField.clear();
        maleToggle.setSelected(true);
        skipPhotosCheckBox.setSelected(false);

        customPlanPage.setVisible(false);
        customPlanPage.setManaged(false);
    }
    public Optional<RegistrationData> getResult() {
        return Optional.ofNullable(formResult);
    }



    public List<float[]> getCapturedEmbeddings() {
        return List.copyOf(enrollmentEmbeddings);
    }

    public Optional<Plan> getSelectedPlan() {
        return Optional.ofNullable(selectedPlan);
    }
    public Optional<CustomPlan> getSelectedCustomPlan(){
        return Optional.ofNullable(customPlan);
    }
    public Optional<Mat> getMiddleFaceMat(){
        return Optional.ofNullable(middleFaceMat);
    }

    /** True if this registration was completed via the "Skip Photos"
     *  checkbox (no face enrollment or plan selection was performed). */
    public boolean isPhotosSkipped() {
        return photosSkipped;
    }

    /** True while this controller is driving a new-member registration;
     *  false once markAsUpdate() has been called for an update flow. */
    public boolean isRegistrationFlow() {
        return registrationFlow;
    }

    /** Called by the code that reuses this dialog to update an existing
     *  member instead of registering a new one. */
    public void markAsUpdate() {
        this.registrationFlow = false;
        this.skipPlanSelection =true;
    }


    public boolean isCanceled(){return canceled;}

}