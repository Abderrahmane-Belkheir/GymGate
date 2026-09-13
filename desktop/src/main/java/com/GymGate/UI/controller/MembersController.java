package com.GymGate.UI.controller;

import com.GymGate.Ai.recognition.Embeddings;
import com.GymGate.Camera.CameraManager;
import com.GymGate.UI.component.DialogEffects;
import com.GymGate.UI.component.MemberAvatar;
import com.GymGate.bussines.db.dao.AttendanceDao;
import com.GymGate.bussines.db.dao.MemberDao;
import com.GymGate.bussines.db.dao.PaymentDao;
import com.GymGate.bussines.db.dao.PlanDao;
import com.GymGate.bussines.db.dao.ReminderDao;
import com.GymGate.bussines.db.entities.Member;
import com.GymGate.bussines.db.entities.Payment;
import com.GymGate.bussines.db.entities.Plan;
import com.GymGate.bussines.models.RegistrationData;
import com.GymGate.bussines.models.Sexe;
import com.GymGate.bussines.services.AppEvents;
import com.GymGate.bussines.services.I18nService;
import com.GymGate.bussines.util.Converter;
import com.GymGate.bussines.util.Mapper;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static com.GymGate.Ai.recognition.Embeddings.knownEmbeddings;
import static com.GymGate.UI.controller.HomeController.updateStatValue;


import com.GymGate.bussines.db.dao.EmbeddingDao;
import com.GymGate.bussines.db.entities.MemberEmbedding;
import com.GymGate.bussines.services.PhotosService;
import javafx.scene.control.*;
import org.opencv.core.Mat;
import java.util.Optional;



public class MembersController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Below this many remaining days, a still-valid membership is shown as "Expiring" (amber) instead of "Active" (green). Presentation-only threshold — does not affect stored data or business rules. */
    private static final int EXPIRING_SOON_DAYS = 7;

    @FXML private Label memberCountLabel;
    @FXML private Button refreshButton;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;
    @FXML private ToggleGroup genderToggleGroup;
    @FXML private ToggleGroup statusToggleGroup;
    @FXML private ToggleButton genderMaleButton;
    @FXML private ToggleButton genderFemaleButton;
    @FXML private ToggleButton statusActiveButton;
    @FXML private ToggleButton statusInactiveButton;

    @FXML private ScrollPane membersScroll;
    @FXML private StackPane pageStack;
    @FXML private VBox listView;

    @FXML private TableView<Member> membersTable;
    @FXML private TableColumn<Member, Member> memberColumn;
    @FXML private TableColumn<Member, Member> planColumn;
    @FXML private TableColumn<Member, Member> startColumn;
    @FXML private TableColumn<Member, Member> endColumn;
    @FXML private TableColumn<Member, Member> remainingColumn;
    @FXML private TableColumn<Member, Member> statusColumn;
    @FXML private TableColumn<Member, Member> actionsColumn;

    private final ObservableList<Member> tableItems = FXCollections.observableArrayList();

    private final MemberDao memberDao = MemberDao.getInstance();
    private final PaymentDao paymentDao=PaymentDao.getInstance();
    private final PlanDao planDao=PlanDao.getInstance();
    private final AttendanceDao attendanceDao=AttendanceDao.getInstance();
    private final EmbeddingDao embeddingDao = EmbeddingDao.getInstance();

    /** Reused across "Change Photo" requests — same pattern HomeController uses
     *  for registration, but driven in update mode. Lazily built once the
     *  first time the table's scene attaches (see initRegisterMemberDialog()). */
    private RegisterMemberController registerMemberController;
    private Stage registerMemberDialogStage;

    /** Per-member detail view, swapped in over the list. Built lazily on first use. */
    private Parent memberDetailRoot;
    private MemberDetailController memberDetailController;
    private boolean detailShowing;

    /** Set when a MEMBER_* event arrives while this screen is off-view, so the
     *  full reload + table rebuild is done once when it's shown again instead of
     *  on every gym check-in in the background. */
    private boolean pendingReload;

    @FXML
    public void initialize() {
        setupTable();
        setupSearch();
        loadMembers();
        refreshButton.setOnAction(e -> refreshMembers());

        // Keep the list live when a member is registered / renewed / checked in
        // from another screen (Home dialogs, the gate). NavigationManager keeps
        // this controller alive for the whole session, so a plain reload is enough.
        AppEvents.subscribe(AppEvents.Type.MEMBER_ADDED, this::onExternalDataChange);
        AppEvents.subscribe(AppEvents.Type.MEMBER_UPDATED, this::onExternalDataChange);
        AppEvents.subscribe(AppEvents.Type.MEMBER_DELETED, this::onExternalDataChange);

        // Each filter group is a real switch, not a deselectable set — without this,
        // clicking the already-selected option turns it off and leaves nothing chosen.
        preventEmptySelection(genderToggleGroup);
        preventEmptySelection(statusToggleGroup);

        // Every Gender/Status toggle click re-reads BOTH groups' current selection:
        // Gender goes to MemberDao#findAll(Sexe), Status is then applied to that
        // result via the existing isValid() check — see applyGenderStatusFilter().
        for (ToggleButton option : List.of(genderMaleButton, genderFemaleButton,
                statusActiveButton, statusInactiveButton)) {
            option.setOnAction(e -> applyGenderStatusFilter());
        }

        // The RegisterMember dialog (largest FXML + an enrollment camera panel)
        // is built lazily on the first "Edit info" click — visiting the Members
        // screen to search / renew / delete never pays for it.

        // Flush a deferred reload when the screen comes back into view; and if the
        // user navigates away while viewing a member's detail, snap back to the list
        // so returning to Members always lands on the list.
        membersTable.sceneProperty().addListener((obs, was, is) -> {
            if (is == null && detailShowing) {
                snapToList();
            }
            if (is != null && pendingReload) {
                pendingReload = false;
                onExternalDataChange();
            }
        });
    }

    /**
     * Loads the RegisterMemberDialog FXML/controller once and wires it up
     * exactly like HomeController.initRegisterMemberDialog() does. Guarded
     * so it only ever runs once — the table's scene can be reattached when
     * NavigationManager swaps pages, which would otherwise re-fire this
     * listener and rebuild the dialog/controller on every visit back here.
     */
    private void initRegisterMemberDialog() {
        if (registerMemberController != null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RegisterMemberDialog.fxml"));
            loader.setResources(I18nService.getBundle());
            Parent dialogRoot = loader.load();
            registerMemberController = loader.getController();
            registerMemberController.setCameraManager(CameraManager.getInstance());
            // This instance is only ever used from Members -> "Change Photo",
            // never for fresh registration, so lock these in once up front
            // instead of re-setting them on every updateInfo() call.
            registerMemberController.markAsUpdate();

            registerMemberDialogStage = new Stage(StageStyle.TRANSPARENT);
            registerMemberDialogStage.initOwner(membersTable.getScene().getWindow());
            registerMemberDialogStage.initModality(Modality.APPLICATION_MODAL);

            Scene dialogScene = new Scene(DialogEffects.wrapForTransparentScene(dialogRoot));
            dialogScene.setFill(Color.TRANSPARENT);
            dialogScene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            registerMemberDialogStage.setScene(dialogScene);
            DialogEffects.preventShowFlash(registerMemberDialogStage);

            registerMemberController.setDialogStage(registerMemberDialogStage);

            Parent membersRoot = membersTable.getScene().getRoot();
            registerMemberDialogStage.setOnShowing(e -> DialogEffects.applyBlurAndDim(membersRoot));
            registerMemberDialogStage.setOnHidden(e -> DialogEffects.clear(membersRoot));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Keeps exactly one toggle selected in a filter group at all times. */
    private static void preventEmptySelection(ToggleGroup group) {
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
    }

    private void refreshMembers() {
        firstNameField.clear();
        lastNameField.clear();

        membersTable.getSelectionModel().clearSelection();

        loadMembers();
    }

    private void setupTable() {
        membersTable.setItems(tableItems);
        membersTable.setPlaceholder(new Label(I18nService.get("No_members_registered_yet.")));

        memberColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        memberColumn.setCellFactory(col -> new MemberCell(this::handleMemberAction));

        planColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        planColumn.setCellFactory(col -> new PlanCell());

        startColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        startColumn.setCellFactory(col -> new DateCell(Member::getStartDate));

        endColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        endColumn.setCellFactory(col -> new DateCell(Member::getEndDate));

        remainingColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        remainingColumn.setCellFactory(col -> new RemainingCell());

        statusColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        statusColumn.setCellFactory(col -> new StatusCell());

        actionsColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        actionsColumn.setCellFactory(col -> new ActionCell(this::handleMemberAction));
    }

    private void setupSearch() {
        confirmButton.setOnAction(e -> onSearch());
        cancelButton.setOnAction(e -> onCancel());
    }

    /**
     * Loads members honoring the currently selected Gender/Status toggles —
     * MemberDao no longer exposes an unfiltered findAll(), so every load
     * (initial, refresh, cancel-search) goes through applyGenderStatusFilter().
     */
    private void loadMembers() {
        applyGenderStatusFilter();
    }

    /**
     * Fired on every Gender/Status toggle click (and by loadMembers()): MemberDao
     * only takes Sexe now, so Gender is the DAO-side fetch and Status is applied
     * afterward using isValid() — the same valid/expired check StatusCell and
     * MemberCell already rely on, not a new or duplicate filtering rule.
     */
    private void applyGenderStatusFilter() {
        Sexe sexe = genderMaleButton.isSelected() ? Sexe.MALE : Sexe.FEMALE;
        boolean activeOnly = statusActiveButton.isSelected();
        List<Member> members = memberDao.findAll(sexe).stream()
                .filter(member -> isValid(member) == activeOnly)
                .toList();
        applyItems(members);
    }

    /** Call after adding/editing/deleting a member elsewhere to refresh the list. */
    public void refresh() {
        loadMembers();
    }

    /**
     * Handler for AppEvents member mutations fired from other screens. Reloads
     * from the DB, keeps the current name search applied, and re-selects the
     * previously selected member if it is still in view.
     */
    private void onExternalDataChange() {
        // Off-view: don't scan the members table and rebuild rows on every
        // background check-in — just remember to refresh on next show.
        if (membersTable.getScene() == null) {
            pendingReload = true;
            return;
        }

        Member previouslySelected = membersTable.getSelectionModel().getSelectedItem();
        Integer selectedId = previouslySelected == null ? null : previouslySelected.getId();

        loadMembers();

        boolean searching = !safeText(firstNameField).isBlank() || !safeText(lastNameField).isBlank();
        if (searching) {
            onSearch();
        }

        if (selectedId != null) {
            for (Member member : membersTable.getItems()) {
                if (member.getId() == selectedId) {
                    membersTable.getSelectionModel().select(member);
                    break;
                }
            }
        }
    }

    private static String safeText(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    /** Fired by Confirm — pushes the name filter down to SQL rather than filtering in Java. */
    private void onSearch() {
        String first = firstNameField.getText() == null ? "" : firstNameField.getText().trim();
        String last = lastNameField.getText() == null ? "" : lastNameField.getText().trim();

        List<Member> results =membersTable.getItems().stream().filter(member -> member.getFirstName().toLowerCase().contains(first.toLowerCase()) && member.getLastName().toLowerCase().contains(last.toLowerCase())).toList();

        membersTable.setPlaceholder(new Label(I18nService.get("No_members_match_your_search.")));
        applyItems(results);
    }

    /** Clears the search fields and restores the full member list. */
    private void onCancel() {
        firstNameField.clear();
        lastNameField.clear();
        membersTable.setPlaceholder(new Label(I18nService.get("No_members_registered_yet.")));
        loadMembers();
    }

    private void applyItems(List<Member> members) {
        tableItems.setAll(members);
        updateCountLabel();
    }

    private void updateCountLabel() {
        int count = tableItems.size();
        memberCountLabel.setText(count + " " + I18nService.get(count == 1 ? "Member" : "Members"));
    }

    /**
     * Single source of truth for whether a member's membership is currently
     * valid — used to decide StatusCell's "Expired" label, MemberCell's
     * Renew/Cancel choice and the Active/Inactive filter. Kept as the exact
     * inverse of the gate's {@link com.GymGate.bussines.services.MemberValidationService}
     * checks: a plan, an end date not yet passed (valid through the end date),
     * and — when a day count is known — more than zero days left. A null
     * remaining-days figure is an unlimited plan and stays valid.
     */
    private static boolean isValid(Member member) {
        if (member.getPlanId() == null) {
            return false;
        }
        boolean dateValid = member.getEndDate() != null && !member.getEndDate().isBefore(LocalDate.now());
        boolean daysValid = member.getRemainingDays() == null || member.getRemainingDays() > 0;
        return dateValid && daysValid;
    }

    private static String initialsOf(String firstName, String lastName) {
        String a = firstName != null && !firstName.isBlank() ? firstName.trim().substring(0, 1) : "";
        String b = lastName != null && !lastName.isBlank() ? lastName.trim().substring(0, 1) : "";
        return (a + b).toUpperCase();
    }


    private void handleMemberAction(String action, Member member) {
        switch (action){
            case "DELETE"->confirmAndDo(member,"DELETE");
            case "RENEW"->renewMembership(member);
            case "CANCEL_MEMBERSHIP"-> confirmAndDo(member,"CANCEL_MEMBERSHIP");
            case "EDIT_INFO"-> updateInfo(member);
            case "VIEW_DETAILS"-> openMemberDetail(member);
        }
    }

    // ---- Member detail view (per-member stats, swapped in over the list) ----

    private void initMemberDetail() {
        if (memberDetailController != null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MemberDetailView.fxml"));
            loader.setResources(I18nService.getBundle());
            memberDetailRoot = loader.load();
            memberDetailController = loader.getController();
            memberDetailController.setActionHandler(this::handleDetailAction);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Swaps the member list for the detail panel. Only the detail panel is
     * animated (a short fade + rise); the list simply sits opaque beneath it,
     * and the detail panel is bitmap-cached for the animation so a large
     * members table / full history table swaps without dropping frames. The
     * animation is scheduled on the next pulse so the detail view's first
     * (heavier) layout has already settled.
     */
    private void openMemberDetail(Member member) {
        initMemberDetail();
        if (memberDetailRoot == null || detailShowing) {
            return;
        }

        memberDetailController.showMember(member.getId());
        detailShowing = true;
        if (!pageStack.getChildren().contains(memberDetailRoot)) {
            pageStack.getChildren().add(memberDetailRoot);
        }
        membersScroll.setVvalue(0);

        memberDetailRoot.setVisible(true);
        memberDetailRoot.setManaged(true);
        memberDetailRoot.setOpacity(0);
        memberDetailRoot.setTranslateY(12);
        memberDetailRoot.setCache(true);
        memberDetailRoot.setCacheHint(CacheHint.SPEED);

        Platform.runLater(() -> {
            if (!detailShowing) {
                return; // navigated away before the pulse
            }
            FadeTransition fade = new FadeTransition(Duration.millis(180), memberDetailRoot);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition rise = new TranslateTransition(Duration.millis(180), memberDetailRoot);
            rise.setFromY(12);
            rise.setToY(0);
            rise.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition transition = new ParallelTransition(fade, rise);
            transition.setOnFinished(e -> {
                memberDetailRoot.setCache(false);
                memberDetailRoot.setTranslateY(0);
                if (detailShowing) {
                    listView.setVisible(false);
                    listView.setManaged(false);
                }
            });
            transition.play();
        });
    }

    private void closeMemberDetail() {
        if (!detailShowing) {
            return;
        }
        detailShowing = false;

        listView.setVisible(true);
        listView.setManaged(true);
        listView.setOpacity(1);
        membersScroll.setVvalue(0);

        memberDetailRoot.setCache(true);
        memberDetailRoot.setCacheHint(CacheHint.SPEED);

        FadeTransition fade = new FadeTransition(Duration.millis(150), memberDetailRoot);
        fade.setFromValue(1);
        fade.setToValue(0);

        TranslateTransition sink = new TranslateTransition(Duration.millis(150), memberDetailRoot);
        sink.setFromY(0);
        sink.setToY(10);

        ParallelTransition transition = new ParallelTransition(fade, sink);
        transition.setOnFinished(e -> {
            if (detailShowing) {
                return; // re-opened during the close animation
            }
            memberDetailRoot.setCache(false);
            memberDetailRoot.setVisible(false);
            memberDetailRoot.setManaged(false);
            memberDetailRoot.setOpacity(1);
            memberDetailRoot.setTranslateY(0);
        });
        transition.play();
    }

    /** Instant reset to the list — used when navigating away mid-detail. */
    private void snapToList() {
        detailShowing = false;
        if (memberDetailRoot != null) {
            memberDetailRoot.setVisible(false);
            memberDetailRoot.setManaged(false);
            memberDetailRoot.setTranslateY(0);
            memberDetailRoot.setOpacity(1);
        }
        listView.setVisible(true);
        listView.setManaged(true);
        listView.setOpacity(1);
    }

    /**
     * Back / Renew / Cancel Membership / Delete from the detail view, routed
     * through the same operations as before. After a change the detail
     * re-renders from the DB; a delete returns to the list.
     */
    private void handleDetailAction(String action, Member member) {
        switch (action) {
            case "BACK" -> closeMemberDetail();
            case "RENEW" -> {
                renewMembership(member);
                refreshDetail(member.getId());
            }
            case "CANCEL_MEMBERSHIP" -> {
                confirmAndDo(member, "CANCEL_MEMBERSHIP");
                refreshDetail(member.getId());
            }
            case "DELETE" -> {
                confirmAndDo(member, "DELETE");
                if (memberDao.findById(member.getId()).isEmpty()) {
                    closeMemberDetail();
                } else {
                    refreshDetail(member.getId());
                }
            }
        }
    }

    private void refreshDetail(int memberId) {
        if (memberDetailController != null && detailShowing) {
            memberDetailController.showMember(memberId);
        }
    }



    /**
     * Reuses the RegisterMemberController dialog to re-capture an existing
     * member's enrollment photo, instead of registering a new member.
     * Update-mode and skipped plan selection are already set once in
     * initRegisterMemberDialog() (this instance never does fresh registration),
     * so "Skip Photos" or a completed capture both go straight to "All Set".
     */
    private void updateInfo(Member member) {
        initRegisterMemberDialog();
        if (registerMemberController == null || registerMemberDialogStage == null) {
            return;
        }

        registerMemberController.prepareForShow(member.getFirstName(),member.getLastName(),member.getPhoneNumber());

        registerMemberDialogStage.showAndWait();

        Optional<RegistrationData> result = registerMemberController.getResult();
        if (result.isEmpty()||registerMemberController.isCanceled()) {
            return;
        }
        Optional<Mat> middleFace=registerMemberController.getMiddleFaceMat();
        List<float[]> embeddings = registerMemberController.getCapturedEmbeddings();



        Member updatedMember= Mapper.toMember(result.get());
        member.setFirstName(updatedMember.getFirstName());
        member.setLastName(updatedMember.getLastName());
        member.setPhoneNumber(updatedMember.getPhoneNumber());
        memberDao.update(member);

        if (embeddings != null && !embeddings.isEmpty()) {
            try {
                embeddingDao.deleteAllForMember(member.getId());
                embeddingDao.insert(new MemberEmbedding(member.getId(), embeddings));
                knownEmbeddings.put(member.getId(), embeddings);
            }catch (Throwable e){
              //
            }
        }
        middleFace.ifPresent(mat -> PhotosService.uploadPhoto(mat, member.getId()));
        membersTable.refresh();
    }

    private void cancelMembership(Member member){
        if(!member.getCreatedDate().equals(LocalDate.now())&&member.getStartDate().equals(LocalDate.now())){
            updateStatValue("Renewed_Subs",-1);
        }
        member.setPlanId(null);
        member.setRemainingDays(null);
        member.setStartDate(null);
        member.setEndDate(null);
        memberDao.update(member);
        AppEvents.publish(AppEvents.Type.MEMBER_UPDATED);
    }

    private void renewMembership(Member member){
        try {


            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RenewPlanDialog.fxml"));
            loader.setResources(I18nService.getBundle());
            Parent dialogRoot = loader.load();
            RenewPlanController controller = loader.getController();

            Stage dialogStage = new Stage(StageStyle.TRANSPARENT);
            dialogStage.initOwner(membersTable.getScene().getWindow());
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            Scene dialogScene = new Scene(DialogEffects.wrapForTransparentScene(dialogRoot));
            dialogScene.setFill(Color.TRANSPARENT);
            dialogScene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            dialogStage.setScene(dialogScene);
            DialogEffects.preventShowFlash(dialogStage);
            controller.setDialogStage(dialogStage);

            Parent homeRoot = membersTable.getScene().getRoot();
            dialogStage.setOnShowing(e -> DialogEffects.applyBlurAndDim(homeRoot));
            dialogStage.setOnHidden(e -> DialogEffects.clear(homeRoot));

            String fullName = Converter.capitalize(member.getFirstName()) + " " + Converter.capitalize(member.getLastName());
            controller.prepareForShow(fullName, member.getPlanId(), member.getSexe());

            dialogStage.showAndWait();

            controller.getResult().ifPresent(plan -> renew(member,plan));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void confirmAndDo(Member member,String action){
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DeleteConfirmDialog.fxml"));
            loader.setResources(I18nService.getBundle());
            Parent dialogRoot = loader.load();
            DeleteConfirmController controller = loader.getController();
            String fullName = Converter.capitalize(member.getFirstName()) + " " + Converter.capitalize(member.getLastName());
            controller.setMessage(I18nService.get(action.equals("DELETE")?"Delete_Member?":"Cancel_Membership?"),
                    I18nService.get(action.equals("DELETE")?"This_will_permanently_delete":"This_will_permanently_cancel_the_membership_of") +" "+ fullName + I18nService.get("This_cannot_be_undone."));

            Stage dialogStage = new Stage(StageStyle.TRANSPARENT);
            dialogStage.initOwner(membersTable.getScene().getWindow());
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            Scene dialogScene = new Scene(DialogEffects.wrapForTransparentScene(dialogRoot));
            dialogScene.setFill(Color.TRANSPARENT);
            dialogScene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            dialogStage.setScene(dialogScene);
            DialogEffects.preventShowFlash(dialogStage);

            controller.setDialogStage(dialogStage);

            Parent membersRoot = membersTable.getScene().getRoot();
            DialogEffects.applyBlurAndDim(membersRoot);
            dialogStage.setOnHidden(e -> DialogEffects.clear(membersRoot));

            dialogStage.showAndWait();

            if (controller.isConfirmed()) {
                if(action.equals("DELETE"))  deleteMember(member);
                else cancelMembership(member);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private void deleteMember(Member member) {
        memberDao.delete(member.getId());
        knownEmbeddings.remove(member.getId());
        tableItems.remove(member);
        updateCountLabel();
        if(member.getCreatedDate().isEqual(LocalDate.now())){
            updateStatValue(I18nService.get("New_Members"),-1);

        } else if (member.getStartDate() != null && member.getStartDate().equals(LocalDate.now())) {
            updateStatValue("Renewed_Subs",-1);
        }

        if(attendanceDao.existToday(member.getId())){
            updateStatValue(I18nService.get("Check-ins"),-1);
        }

        AppEvents.publish(AppEvents.Type.MEMBER_DELETED);
    }



    private void renew(Member member,Plan plan){
        boolean wasExpiring = countsAsExpiring(member);
        member.setPlanId(plan.getId());
        member.setStartDate(LocalDate.now());
        member.setEndDate(LocalDate.now().plusMonths(plan.getDurationMonths()));
        member.setRemainingDays(plan.getTotalDays());
        memberDao.update(member);
        // renewed — clear today's renewal reminder if it was logged
        ReminderDao.getInstance().deleteForMember(member.getId());
        paymentDao.insert(new Payment(member.getId(),plan.getId(),plan.getPrice()));
        updateStatValue(I18nService.get("Renewed_Subs"),1);
        updateStatValue(I18nService.get("Revenue"),plan.getPrice());
        if (wasExpiring) {
            updateStatValue(I18nService.get("Expires_Today"), -1);
        }
        AppEvents.publish(AppEvents.Type.MEMBER_UPDATED, AppEvents.Type.PAYMENT_ADDED);
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

    /**
     * IDENTITY group cell (memberColumn): photo, first/last name and phone \u2014
     * everything that identifies who this member is. Plan/dates/remaining
     * time live in StatusCell (MEMBERSHIP + STATUS groups); actions live in
     * ActionCell. A trailing hairline divider (pinned to the column's right
     * edge via a growing spacer) marks the boundary with the Membership
     * group in the next column. Right-click still opens the same action
     * menu as before, for users used to the old gesture.
     */
    private static class MemberCell extends TableCell<Member, Member> {
        private final BiConsumer<String, Member> onAction;

        MemberCell(BiConsumer<String, Member> onAction) {
            this.onAction = onAction;
            setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Member member, boolean empty) {
            super.updateItem(member, empty);
            if (empty || member == null) {
                setGraphic(null);
                return;
            }

            MemberAvatar avatar = new MemberAvatar("attendance-avatar", "attendance-avatar-label");
            avatar.setInitials(initialsOf(member.getFirstName(), member.getLastName()));
            // Photo comes from PhotosService's shared LRU cache — decoded and
            // queried at most once per member regardless of how often the row
            // is re-rendered while scrolling.
            PhotosService.loadPhoto(member.getId())
                    .ifPresentOrElse(avatar::setPhoto, () -> avatar.setPhoto(null));

            Label name = new Label(Converter.capitalize(member.getFirstName()) + " " + Converter.capitalize(member.getLastName()));
            name.getStyleClass().add("member-row-name");

            FontIcon phoneIcon = new FontIcon("fas-phone-alt");
            phoneIcon.getStyleClass().add("meta-icon");
            Label phone = new Label(member.getPhoneNumber());
            phone.getStyleClass().add("meta-label");

            HBox metaRow = new HBox(5, phoneIcon, phone);
            metaRow.setAlignment(Pos.CENTER_LEFT);
            // Phone is optional — drop the row entirely when there is none.
            boolean hasPhone = member.getPhoneNumber() != null && !member.getPhoneNumber().isBlank();
            metaRow.setVisible(hasPhone);
            metaRow.setManaged(hasPhone);

            VBox infoBlock = new VBox(3, name, metaRow);
            infoBlock.setAlignment(Pos.CENTER_LEFT);

            HBox content = new HBox(13, avatar, infoBlock);
            content.setAlignment(Pos.CENTER_LEFT);

            // Handler on the cell (not the content box) so the whole Member
            // cell stays right-clickable, same as before the column split.
            setOnContextMenuRequested(event -> {
                getTableView().getSelectionModel().select(getIndex());
                MemberActionMenu.show(member, this, event.getScreenX(), event.getScreenY(), onAction);
            });

            setGraphic(content);
        }
    }

    /** MEMBERSHIP group \u2014 the plan name with a small membership-card icon
     *  (or a plain "No Plan" when there's none). */
    private static class PlanCell extends TableCell<Member, Member> {
        PlanCell() {
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(Member member, boolean empty) {
            super.updateItem(member, empty);
            if (empty || member == null) {
                setGraphic(null);
                return;
            }

            if (member.getPlanName() != null && !member.getPlanName().isEmpty()) {
                FontIcon icon = new FontIcon("fas-id-card");
                icon.getStyleClass().add("members-cell-icon");
                Label label = new Label(member.getPlanName());
                label.getStyleClass().add("members-plan-name");
                HBox row = new HBox(6, icon, label);
                row.setAlignment(Pos.CENTER);
                setGraphic(row);
            } else {
                Label noPlan = new Label(I18nService.get("No_Plan"));
                noPlan.getStyleClass().add("member-no-plan-label");
                setGraphic(noPlan);
            }
        }
    }

    /**
     * MEMBERSHIP group \u2014 a single membership date (start or end), extracted by
     * the supplied accessor. Shows an em dash when the member has no plan or
     * the date is unset, so the column always has a value to align to.
     */
    private static class DateCell extends TableCell<Member, Member> {
        private final java.util.function.Function<Member, LocalDate> accessor;

        DateCell(java.util.function.Function<Member, LocalDate> accessor) {
            this.accessor = accessor;
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(Member member, boolean empty) {
            super.updateItem(member, empty);
            if (empty || member == null) {
                setGraphic(null);
                return;
            }

            LocalDate date = member.getPlanId() == null ? null : accessor.apply(member);
            Label label = new Label(date != null ? date.format(DATE_FORMAT) : "\u2014");
            label.getStyleClass().add(date != null ? "members-cell-value" : "members-cell-muted");
            setGraphic(label);
        }
    }

    /**
     * STATUS group \u2014 remaining days. Blank (em dash) for an unlimited plan,
     * no plan, or an already-expired membership, where a day count would be
     * misleading.
     */
    private static class RemainingCell extends TableCell<Member, Member> {
        RemainingCell() {
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(Member member, boolean empty) {
            super.updateItem(member, empty);
            if (empty || member == null) {
                setGraphic(null);
                return;
            }

            boolean showCount = member.getPlanId() != null
                    && isValid(member)
                    && member.getRemainingDays() != null;

            if (!showCount) {
                Label dash = new Label("\u2014");
                dash.getStyleClass().add("members-cell-muted");
                setGraphic(dash);
                return;
            }

            Label label = new Label(member.getRemainingDays() + " " + I18nService.get("days_left"));
            label.getStyleClass().add("member-remaining-label");
            setGraphic(label);
        }
    }

    /**
     * STATUS group \u2014 the color-coded Active / Expiring / Expired / No-Plan
     * pill. Same branching the old combined StatusCell used, so the label a
     * member shows is unchanged.
     */
    private static class StatusCell extends TableCell<Member, Member> {
        StatusCell() {
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(Member member, boolean empty) {
            super.updateItem(member, empty);
            if (empty || member == null) {
                setGraphic(null);
                return;
            }

            Label pill = new Label();
            pill.getStyleClass().add("badge");

            if (member.getPlanId() == null) {
                pill.setText(I18nService.get("No_Active_Plan"));
                pill.getStyleClass().add("badge-no");
            } else if (!isValid(member)) {
                pill.setText(I18nService.get("Expired"));
                pill.getStyleClass().add("badge-danger");
            } else {
                // "Expiring" (amber) covers both ways a still-valid membership
                // runs out soon: few visits left, OR the end date is within the
                // window — so a member whose plan ends today (and is therefore
                // in today's "Expiring" stat card) never shows as plain Active.
                LocalDate today = LocalDate.now();
                boolean expiringSoon =
                        (member.getRemainingDays() != null && member.getRemainingDays() <= EXPIRING_SOON_DAYS)
                        || (member.getEndDate() != null
                            && !member.getEndDate().isAfter(today.plusDays(EXPIRING_SOON_DAYS)));
                pill.setText(I18nService.get(expiringSoon ? "Expiring" : "Active"));
                pill.getStyleClass().add(expiringSoon ? "badge-warning" : "badge-active");
            }

            setGraphic(pill);
        }
    }

    /**
     * ACTIONS group cell (actionsColumn): a minimal, always-visible "..."
     * trigger that opens the same {@link MemberActionMenu} used by
     * MemberCell's right-click \u2014 so the available actions are discoverable
     * without requiring the user to already know about right-click.
     */
    private static class ActionCell extends TableCell<Member, Member> {
        private final Button trigger;

        ActionCell(BiConsumer<String, Member> onAction) {
            setAlignment(Pos.CENTER);

            FontIcon icon = new FontIcon("fas-ellipsis-h");
            icon.getStyleClass().add("row-action-icon");

            trigger = new Button();
            trigger.setGraphic(icon);
            trigger.getStyleClass().add("row-action-button");
            trigger.setFocusTraversable(false);
            trigger.setOnAction(e -> {
                Member member = getItem();
                if (member == null) {
                    return;
                }
                getTableView().getSelectionModel().select(getIndex());
                Bounds bounds = trigger.localToScreen(trigger.getBoundsInLocal());
                MemberActionMenu.show(member, trigger, bounds.getMinX(), bounds.getMaxY() + 6, onAction);
            });
        }

        @Override
        protected void updateItem(Member member, boolean empty) {
            super.updateItem(member, empty);
            setGraphic(empty || member == null ? null : trigger);
        }
    }

    /**
     * The member row-action popup (Edit / Renew-or-Cancel / Delete), shared
     * by MemberCell's right-click and ActionCell's visible "..." button so
     * both triggers open the exact same menu with the exact same behavior.
     * A single static Popup reference (shared across every cell) ensures
     * opening one closes any other that's already showing, instead of
     * stacking popups on top of each other.
     */
    private static class MemberActionMenu {
        private static Popup openPopup;

        static void show(Member member, Node owner, double screenX, double screenY, BiConsumer<String, Member> onAction) {
            if (openPopup != null && openPopup.isShowing()) {
                openPopup.hide();
            }

            VBox card = new VBox(2);
            card.getStyleClass().add("member-context-card");

            Popup popup = new Popup();
            popup.setAutoHide(true);
            popup.setHideOnEscape(true);
            popup.setOnHidden(e -> {
                if (openPopup == popup) {
                    openPopup = null;
                }
            });

            // Only navigation actions here. Renew / Cancel Membership / Delete
            // live on the member detail screen (opened via "Details").
            card.getChildren().add(buildActionRow("fas-chart-line", I18nService.get("Details"), false,
                    () -> runAction(onAction, "VIEW_DETAILS", member, popup)));

            card.getChildren().add(buildActionRow("fas-image", I18nService.get("Edit"), false,
                    () -> runAction(onAction, "EDIT_INFO", member, popup)));

            popup.getContent().add(card);
            openPopup = popup;
            popup.show(owner, screenX, screenY);
        }

        private static void runAction(BiConsumer<String, Member> onAction, String action, Member member, Popup popup) {
            onAction.accept(action, member);
            popup.hide();
        }

        /**
         * One row in the action card: icon + label over a highlight that
         * fades in/out on hover (JavaFX CSS ":hover" alone doesn't animate a
         * background-color change, so the fade is driven explicitly here).
         */
        private static Node buildActionRow(String iconLiteral, String text, boolean destructive, Runnable onClick) {
            Region highlight = new Region();
            highlight.getStyleClass().add(destructive ? "member-context-row-highlight-danger" : "member-context-row-highlight");
            highlight.setOpacity(0);

            FontIcon icon = new FontIcon(iconLiteral);
            icon.getStyleClass().add(destructive ? "member-context-icon-danger" : "member-context-icon");
            Label label = new Label(text);
            label.getStyleClass().add(destructive ? "member-context-label-danger" : "member-context-label");

            HBox contentRow = new HBox(10, icon, label);
            contentRow.setAlignment(Pos.CENTER_LEFT);
            contentRow.setPadding(new Insets(9, 16, 9, 12));

            StackPane row = new StackPane(highlight, contentRow);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setCursor(Cursor.HAND);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(120), highlight);
            fadeIn.setToValue(1);
            FadeTransition fadeOut = new FadeTransition(Duration.millis(120), highlight);
            fadeOut.setToValue(0);

            row.setOnMouseEntered(e -> {
                fadeOut.stop();
                fadeIn.playFromStart();
            });
            row.setOnMouseExited(e -> {
                fadeIn.stop();
                fadeOut.playFromStart();
            });
            row.setOnMouseClicked(e -> onClick.run());

            return row;
        }
    }
}