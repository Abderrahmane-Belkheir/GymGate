
package com.GymGate.UI.component;

import javafx.beans.binding.Bindings;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
import javafx.stage.Window;

/**
 * Reusable circular avatar: shows initials by default, an optional photo
 * (center-cropped to a square, "cover" style, so a wide source frame isn't
 * stretched) when one is set, and clicking a set photo opens it full-size in
 * a dismissible overlay with the rest of the app blurred behind it.
 *
 * <p>Style classes are supplied by the caller rather than hardcoded, so the
 * same behavior can be reused at different sizes/colors — e.g. the large
 * gray "avatar-photo"/"avatar-initials" avatar on the home check-in card vs.
 * the smaller blue "attendance-avatar"/"attendance-avatar-label" avatar used
 * in list rows — without either needing new CSS.
 */
public class MemberAvatar extends StackPane {

    private final StackPane circle = new StackPane();
    private final Label initialsLabel = new Label();
    private final ImageView photoView = new ImageView();

    public MemberAvatar(String circleStyleClass, String initialsStyleClass) {
        circle.getStyleClass().add(circleStyleClass);
        initialsLabel.getStyleClass().add(initialsStyleClass);

        // Clip + size bound to circle's own rendered size (set via CSS, e.g.
        // -fx-min-width/-fx-pref-width) rather than a hardcoded number, so this
        // keeps matching whatever the stylesheet defines for circleStyleClass.
        Circle clip = new Circle();
        clip.radiusProperty().bind(Bindings.min(circle.widthProperty(), circle.heightProperty()).divide(2));
        clip.centerXProperty().bind(circle.widthProperty().divide(2));
        clip.centerYProperty().bind(circle.heightProperty().divide(2));
        photoView.setClip(clip);
        photoView.fitWidthProperty().bind(circle.widthProperty());
        photoView.fitHeightProperty().bind(circle.heightProperty());
        photoView.setPreserveRatio(true);
        photoView.setSmooth(true);
        photoView.setVisible(false); // hidden until setPhoto(...) provides an image
        photoView.setManaged(false); // sits on top of the circle without affecting its layout

        circle.getChildren().addAll(initialsLabel, photoView);
        circle.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY
                    && photoView.isVisible() && photoView.getImage() != null) {
                showFullPhotoOverlay(photoView.getImage());
            }
        });

        getChildren().add(circle);
    }

    public void setInitials(String initials) {
        initialsLabel.setText(initials);
    }

    /**
     * Sets (or clears, with null) the photo. When present it's shown
     * center-cropped to a square so a wide source frame fills the circle
     * without stretching or letterboxing. Passing null falls back to initials.
     */
    public void setPhoto(Image photo) {
        if (photo == null || photo.isError()) {
            photoView.setImage(null);
            photoView.setVisible(false);
            initialsLabel.setVisible(true);
            circle.setCursor(Cursor.DEFAULT);
            return;
        }

        double imgW = photo.getWidth();
        double imgH = photo.getHeight();
        double side = Math.min(imgW, imgH);
        double x = (imgW - side) / 2;
        double y = (imgH - side) / 2;
        photoView.setViewport(new Rectangle2D(x, y, side, side));

        photoView.setImage(photo);
        photoView.setVisible(true);
        initialsLabel.setVisible(false);
        circle.setCursor(Cursor.HAND);
    }

    /**
     * Shows the photo full-size (uncropped — the whole stored frame, not the
     * center-cropped square used in the avatar itself) in a dismissible
     * overlay, with the rest of the app blurred behind it. Dismiss by
     * clicking anywhere, pressing Escape, or clicking outside (auto-hide).
     */
    private void showFullPhotoOverlay(Image fullImage) {
        Scene scene = getScene();
        if (scene == null) {
            return;
        }
        Window window = scene.getWindow();
        Parent root = scene.getRoot();

        Effect previousEffect = root.getEffect();
        root.setEffect(new GaussianBlur(20));

        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        overlay.prefWidthProperty().bind(window.widthProperty());
        overlay.prefHeightProperty().bind(window.heightProperty());

        ImageView fullView = new ImageView(fullImage);
        fullView.setPreserveRatio(true);
        fullView.setSmooth(true);
        fullView.fitWidthProperty().bind(overlay.widthProperty().multiply(0.6));
        fullView.fitHeightProperty().bind(overlay.heightProperty().multiply(0.6));

        // Rounded corners: rather than reading the rendered size back off the
        // ImageView (boundsInLocal behavior with preserveRatio can be
        // unreliable across JavaFX versions and was the likely reason the
        // clip wasn't taking effect), the "contain" size is computed directly
        // from the image's own aspect ratio and the fit box — same math as a
        // manual object-fit: contain.
        double imgAspect = fullImage.getWidth() / fullImage.getHeight();

        Rectangle imageClip = new Rectangle();
        imageClip.setArcWidth(48);
        imageClip.setArcHeight(48);

        javafx.beans.binding.DoubleBinding renderedWidth = Bindings.createDoubleBinding(() -> {
            double boxW = overlay.getWidth() * 0.6;
            double boxH = overlay.getHeight() * 0.6;
            if (boxH <= 0) {
                return 0.0;
            }
            double boxAspect = boxW / boxH;
            return imgAspect > boxAspect ? boxW : boxH * imgAspect;
        }, overlay.widthProperty(), overlay.heightProperty());

        javafx.beans.binding.DoubleBinding renderedHeight = Bindings.createDoubleBinding(() -> {
            double boxW = overlay.getWidth() * 0.6;
            double boxH = overlay.getHeight() * 0.6;
            if (boxW <= 0) {
                return 0.0;
            }
            double boxAspect = boxW / boxH;
            return imgAspect > boxAspect ? boxW / imgAspect : boxH;
        }, overlay.widthProperty(), overlay.heightProperty());

        imageClip.widthProperty().bind(renderedWidth);
        imageClip.heightProperty().bind(renderedHeight);

        Group clippedImage = new Group(fullView);
        clippedImage.setClip(imageClip);

        overlay.getChildren().add(clippedImage);

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.getContent().add(overlay);
        // Runs regardless of how the popup closes (click, Escape, auto-hide on
        // focus loss), so the blur can never get stuck on if dismissal happens
        // some way other than the explicit click handler below.
        popup.setOnHidden(e -> root.setEffect(previousEffect));

        overlay.setOnMouseClicked(e -> popup.hide());

        popup.show(window, window.getX(), window.getY());
    }
}