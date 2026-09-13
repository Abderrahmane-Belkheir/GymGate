package com.GymGate.UI.component;
import com.GymGate.bussines.services.I18nService;
import com.GymGate.model.MemberInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

public class MemberInfoCard extends HBox {

    private final Label nameLabel = new Label();
    private final Badge statusBadge = new Badge(I18nService.get("SUCCESS"), "badge-active");
    private final Label phoneLabel = new Label();
    private final HBox phoneRow = new HBox(6);
    private final InfoChip planChip;
    private final InfoChip expiresChip;
    private final InfoChip lastCheckInChip;
    private final InfoChip daysLeftChip;
    private final Button renewButton = new Button();
    private int currentMemberId;
    private Consumer<Integer> onRenewAction;

    private final MemberAvatar avatar = new MemberAvatar("avatar-photo", "avatar-initials");
    private final ScrollPane chipsScroll;

    // ---- low-confidence "might be this member?" confirmation bar ----
    // Shown instead of renewButton while a POSSIBLE_MATCH candidate is on
    // display. lastMember/lastPhoto are whatever setMember/setPhoto last set
    // from a REAL (non-candidate) result — snapshotted so a reject can put
    // the screen back exactly as it was before the suggestion appeared.
    private final Label situationLabel = new Label();
    private final Button acceptCandidateButton = new Button();
    private final Button rejectCandidateButton = new Button();
    private final HBox confirmBar;
    private MemberInfo lastMember;
    private Image lastPhoto;
    private boolean lastRenewVisible;

    public MemberInfoCard(MemberInfo member) {
        this.currentMemberId = member.getId();
        getStyleClass().add("member-card");
        setSpacing(24);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(26, 30, 26, 30));
        setMinHeight(110);

        avatar.setInitials(member.getPhotoInitials());

        nameLabel.getStyleClass().add("member-name");
        nameLabel.setMinWidth(Region.USE_PREF_SIZE);
        HBox nameRow = new HBox(10, nameLabel, statusBadge);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        FontIcon idIcon = new FontIcon("fas-id-badge");
        idIcon.getStyleClass().add("meta-icon");

        FontIcon phoneIcon = new FontIcon("fas-phone-alt");
        phoneIcon.getStyleClass().add("meta-icon");
        phoneLabel.getStyleClass().add("meta-label");
        phoneRow.getChildren().setAll(phoneIcon, phoneLabel);
        phoneRow.setAlignment(Pos.CENTER_LEFT);

        HBox metaRow = new HBox(18, phoneRow);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        VBox infoBlock = new VBox(8, nameRow, metaRow);
        infoBlock.setAlignment(Pos.CENTER_LEFT);
        infoBlock.setMinWidth(Region.USE_PREF_SIZE);  // NEVER shrink this column either

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        planChip = new InfoChip("fas-clipboard-list", I18nService.get("PLAN"), member.getMembershipPlan());
        expiresChip = new InfoChip("fas-calendar-alt", I18nService.get("EXPIRES"), member.getExpirationDate());
        lastCheckInChip = new InfoChip("fas-clock", I18nService.get("LAST_CHECK_IN"), member.getLastCheckIn());
        daysLeftChip = new InfoChip("fas-hourglass-half", I18nService.get("DAYS_LEFT"), "");

        HBox chipsRow = new HBox(12, planChip, expiresChip, daysLeftChip, lastCheckInChip);
        chipsRow.setAlignment(Pos.CENTER_RIGHT);

        chipsScroll = new ScrollPane(chipsRow);
        chipsScroll.getStyleClass().add("chips-scroll");
        chipsScroll.setFitToHeight(true);
        chipsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chipsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        renewButton.setVisible(false);
        renewButton.setManaged(false);
        renewButton.setOnAction(e -> onRenewAction.accept(currentMemberId));
        configureRenewButton();

        confirmBar = buildConfirmBar();
        confirmBar.setVisible(false);
        confirmBar.setManaged(false);
        // avatar/infoBlock are pinned to their preferred size (never shrink,
        // see infoBlock above) and chipsScroll/renewButton are hidden while a
        // candidate is pending, so confirmBar must be the row's own flexible
        // element too — otherwise a tight window has nothing left to shrink
        // and the buttons (last in the row) render off the visible edge.
        HBox.setHgrow(confirmBar, Priority.ALWAYS);

        getChildren().addAll(avatar, infoBlock, spacer, chipsScroll, confirmBar, renewButton);

        setMember(member);
    }

    private HBox buildConfirmBar() {
        situationLabel.getStyleClass().add("confirm-situation-label");
        situationLabel.setWrapText(true);
        situationLabel.setMaxWidth(220);
        // The explanatory text is what should give way under a tight window —
        // never the buttons. Without an explicit minWidth a Label refuses to
        // shrink below its unwrapped content width, which was pushing the
        // buttons (laid out after it) past the visible edge of the card on a
        // real (narrower-than-my-test-harness) window: the badge/text showed,
        // the buttons did not. minWidth(0) + hgrow makes it the one element in
        // this row that actually yields space instead of demanding it.
        situationLabel.setMinWidth(0);

        FontIcon acceptIcon = new FontIcon("fas-check");
        acceptIcon.getStyleClass().add("confirm-accept-icon");
        acceptCandidateButton.getStyleClass().add("confirm-accept-button");
        acceptCandidateButton.setGraphic(acceptIcon);
        acceptCandidateButton.setFocusTraversable(false);
        acceptCandidateButton.setTooltip(new Tooltip(I18nService.get("Confirm_Match")));

        FontIcon rejectIcon = new FontIcon("fas-times");
        rejectIcon.getStyleClass().add("confirm-reject-icon");
        rejectCandidateButton.getStyleClass().add("confirm-reject-button");
        rejectCandidateButton.setGraphic(rejectIcon);
        rejectCandidateButton.setFocusTraversable(false);
        rejectCandidateButton.setTooltip(new Tooltip(I18nService.get("Reject_Match")));

        // Fixed size, never shrunk — mirrors infoBlock's USE_PREF_SIZE pattern
        // above, so these two controls are always fully rendered.
        HBox buttons = new HBox(8, acceptCandidateButton, rejectCandidateButton);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setMinWidth(Region.USE_PREF_SIZE);

        HBox bar = new HBox(14, situationLabel, buttons);
        bar.getStyleClass().add("confirm-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(situationLabel, Priority.ALWAYS);
        return bar;
    }

    /**
     * Displays a low-confidence candidate (see MemberValidationService /
     * ValidationResult.POSSIBLE_MATCH) in place of the normal card content,
     * with an explanation and accept/reject controls for staff. Whatever the
     * card showed before this call — the last real recognition — is
     * snapshotted so {@code onReject} can put it straight back.
     *
     * @param candidate the closest member, to display for visual comparison
     * @param photo     the candidate's enrollment photo, or null
     * @param onAccept  runs when staff confirm the candidate IS this person —
     *                  the caller is responsible for the actual check-in
     * @param onReject  runs when staff dismiss the suggestion; the card itself
     *                  already reverts to the prior display before this runs
     */
    public void showPendingCandidate(MemberInfo candidate, Image photo, Runnable onAccept, Runnable onReject) {
        MemberInfo previousMember = lastMember;
        Image previousPhoto = lastPhoto;
        boolean previousRenewVisible = lastRenewVisible;

        situationLabel.setText(I18nService.get("Possible_match_hint"));
        setMember(candidate);
        setPhoto(photo);
        statusBadge.getStyleClass().removeAll("badge-active", "badge-danger");
        statusBadge.getStyleClass().add("badge-warning");
        statusBadge.setText(I18nService.get("POSSIBLE_MATCH"));
        daysLeftChip.setValue("");

        // The chips row and the confirm bar are both wide; showing both at
        // once starves the situation text down to a sliver (each letter
        // wrapping onto its own line). Not needed for a yes/no identity call.
        chipsScroll.setVisible(false);
        chipsScroll.setManaged(false);
        renewButton.setVisible(false);
        renewButton.setManaged(false);
        confirmBar.setVisible(true);
        confirmBar.setManaged(true);

        Runnable restorePrevious = () -> {
            hideConfirmBar();
            chipsScroll.setVisible(true);
            chipsScroll.setManaged(true);
            if (previousMember != null) {
                setMember(previousMember);
            }
            setPhoto(previousPhoto);
            renewButton.setVisible(previousRenewVisible);
            renewButton.setManaged(previousRenewVisible);
        };

        acceptCandidateButton.setOnAction(e -> {
            // Put the card back to whatever it showed before the suggestion
            // FIRST. If this turns into a real check-in, the async result that
            // follows (SUCCESS/PLAN_EXPIRED/etc.) redraws it again with the
            // confirmed member's own info via the normal updateMemberCard
            // path. If the member already checked in today, onRecognition
            // only pops the usual "Welcome Again" toast and deliberately never
            // touches the card — so restoring here is what keeps the screen
            // on the previous person instead of getting stuck on the
            // candidate. Either way this call is fire-and-forget; the result
            // arrives later via RecognitionResultPresenter.
            restorePrevious.run();
            onAccept.run();
        });
        rejectCandidateButton.setOnAction(e -> {
            restorePrevious.run();
            onReject.run();
        });
    }

    private void hideConfirmBar() {
        confirmBar.setVisible(false);
        confirmBar.setManaged(false);
    }

    private void configureRenewButton() {
        renewButton.getStyleClass().add("renew-button");
        renewButton.setFocusTraversable(false);

        FontIcon icon = new FontIcon("fas-sync-alt");
        icon.getStyleClass().add("renew-button-icon");

        Label text = new Label(I18nService.get("Renew"));
        text.getStyleClass().add("renew-button-label");

        HBox content = new HBox(8, icon, text);
        content.setAlignment(Pos.CENTER);
        renewButton.setGraphic(content);
    }

    /**
     * Sets (or clears, with null) the member's photo — delegates to
     * MemberAvatar, which handles the center-crop-to-square display and the
     * click-to-expand full-size/blurred-background overlay.
     */
    public void setPhoto(Image photo) {
        this.lastPhoto = photo;
        avatar.setPhoto(photo);
    }

    public void setMember(MemberInfo member) {
        this.lastMember = member;
        hideConfirmBar();
        // A real result (this call) always wins over a still-pending
        // candidate prompt — put back whatever showPendingCandidate hid.
        chipsScroll.setVisible(true);
        chipsScroll.setManaged(true);
        this.currentMemberId = member.getId();
        nameLabel.setText(member.getFullName());
        String phone = member.getPhoneNumber();
        boolean hasPhone = phone != null && !phone.isBlank();
        phoneLabel.setText(hasPhone ? phone : "");
        phoneRow.setVisible(hasPhone);
        phoneRow.setManaged(hasPhone);
        statusBadge.setText(member.getMembershipStatus());
        planChip.setValue(member.getMembershipPlan());

        statusBadge.getStyleClass().removeAll("badge-active", "badge-danger");

        if (member.getMembershipStatus().equals(I18nService.get("SUCCESS"))) {
            statusBadge.getStyleClass().add("badge-active");
        } else {
            statusBadge.getStyleClass().add("badge-danger");
        }

        statusBadge.applyCss();

        expiresChip.setValue("");
        if(member.getExpirationDate()!=null&&!member.getExpirationDate().equals("null")){
            expiresChip.setValue(member.getExpirationDate());
        }
        lastCheckInChip.setValue(member.getLastCheckIn());
        avatar.setInitials(member.getPhotoInitials());
        daysLeftChip.setValue("");
    }

    public void setRemainingDays(long days) {
        daysLeftChip.setValue(String.valueOf(days));
    }

    public void setRenewVisible(boolean visible) {
        this.lastRenewVisible = visible;
        // A pending candidate hides the renew button regardless (see
        // showPendingCandidate) — don't let a stat-card-style call re-show it
        // out from under an active confirmation prompt.
        if (confirmBar.isVisible()) {
            return;
        }
        renewButton.setVisible(visible);
        renewButton.setManaged(visible);
    }

    public void setOnRenew(Consumer<Integer> action) {
        this.onRenewAction = action;
    }
}
