package com.GymGate.UI.component;

import com.GymGate.bussines.services.I18nService;
import com.GymGate.bussines.services.PhotosService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * The drill-down view for a single Home "Today's Statistics" card: swapped
 * in over the whole stats panel when a {@link StatCard} is clicked. Shows a
 * back arrow + the card's title + a running count, then a scrollable list of
 * the members / payments behind that number. Purely presentational — the
 * rows are supplied ready-made by {@link com.GymGate.UI.controller.HomeController}.
 */
public class StatDetailPanel extends VBox {

    /**
     * One list entry. {@code memberId} is used only to pull the avatar photo
     * from {@link PhotosService}'s cache; {@code trailing} may be null/blank
     * (e.g. a check-in row has no trailing figure beyond its time).
     */
    public record Row(int memberId, String initials, String title,
                      String subtitle, String trailing) { }

    public StatDetailPanel(String title, List<Row> rows, Runnable onBack) {
        getStyleClass().add("stat-detail-panel");
        setSpacing(14);

        getChildren().addAll(buildHeader(title, rows.size(), onBack), buildList(rows));
    }

    private Node buildHeader(String title, int count, Runnable onBack) {
        FontIcon backIcon = new FontIcon("fas-arrow-left");
        backIcon.getStyleClass().add("stat-detail-back-icon");

        Button backButton = new Button();
        backButton.setGraphic(backIcon);
        backButton.getStyleClass().add("stat-detail-back-button");
        backButton.setFocusTraversable(false);
        backButton.setOnAction(e -> onBack.run());

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label countLabel = new Label(String.valueOf(count));
        countLabel.getStyleClass().add("stat-detail-count");

        HBox header = new HBox(12, backButton, titleLabel, spacer, countLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("stat-detail-header");
        return header;
    }

    private Node buildList(List<Row> rows) {
        VBox list = new VBox();
        list.getStyleClass().add("stat-detail-list");

        if (rows.isEmpty()) {
            Label empty = new Label(I18nService.get("Nothing_here_yet"));
            empty.getStyleClass().add("stat-detail-empty");
            list.getChildren().add(empty);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) {
                    Region divider = new Region();
                    divider.getStyleClass().add("stat-detail-divider");
                    list.getChildren().add(divider);
                }
                list.getChildren().add(buildRow(rows.get(i)));
            }
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("stat-detail-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private Node buildRow(Row row) {
        MemberAvatar avatar = new MemberAvatar("attendance-avatar", "attendance-avatar-label");
        avatar.setInitials(row.initials());
        PhotosService.loadPhoto(row.memberId())
                .ifPresentOrElse(avatar::setPhoto, () -> avatar.setPhoto(null));

        Label titleLabel = new Label(row.title());
        titleLabel.getStyleClass().add("member-row-name");

        VBox info;
        if (row.subtitle() != null && !row.subtitle().isBlank()) {
            Label subtitleLabel = new Label(row.subtitle());
            subtitleLabel.getStyleClass().add("meta-label");
            info = new VBox(3, titleLabel, subtitleLabel);
        } else {
            info = new VBox(titleLabel);
        }
        info.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox rowBox = new HBox(13, avatar, info, spacer);
        rowBox.setAlignment(Pos.CENTER_LEFT);
        rowBox.getStyleClass().add("stat-detail-row");

        if (row.trailing() != null && !row.trailing().isBlank()) {
            Label trailingLabel = new Label(row.trailing());
            trailingLabel.getStyleClass().add("stat-detail-trailing");
            rowBox.getChildren().add(trailingLabel);
        }
        return rowBox;
    }
}
