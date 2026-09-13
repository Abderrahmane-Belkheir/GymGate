package com.GymGate.UI.component;

import com.GymGate.bussines.db.dao.MemberDao;
import com.GymGate.bussines.db.dao.ReminderDao;
import com.GymGate.bussines.db.dao.ReminderType;
import com.GymGate.bussines.db.entities.Member;
import com.GymGate.bussines.models.Settings;
import com.GymGate.bussines.services.I18nService;
import com.GymGate.bussines.services.PhotosService;
import com.GymGate.bussines.util.Converter;
import com.GymGate.bussines.util.SoundUtil;
import com.GymGate.bussines.util.WhatsAppWindow;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sequential "review the members to remind" flow. Shows one card at a time —
 * member photo, name, a type badge + note, the details (phone, plan, expiry),
 * and <b>Send</b> / <b>Skip</b> buttons — and advances to the next member on each
 * click until the list is exhausted, then closes. The background is not blurred.
 *
 * <p>The badge, the note line and the pre-filled WhatsApp message all adapt to
 * the entry's {@link ReminderType} — each one tells staff plainly what the card
 * is for ({@code EXPIRY} → renewal today, {@code LAPSED_5D}/{@code LAPSED_10D} →
 * how many days overdue, {@code INACTIVITY} → win-back wording) — and the member
 * gets an equally explicit WhatsApp message naming how long it's been.
 *
 * <p><b>Send</b> opens WhatsApp for that member and logs them in {@code reminders}
 * ({@link ReminderDao#record}) so they are not shown again today. <b>Skip</b>
 * (or closing the review early) does neither, so the member returns on the next
 * hourly sweep. The message is never sent automatically.
 */
public final class ReminderReviewDialog {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Pre-filled WhatsApp text — always Arabic (the members are Algerian).
     *  One template per {@link ReminderType}, each explicit about how long it's
     *  been so the member isn't left guessing. When no gym name is set: the
     *  expiry/lapsed lines drop {@code "في {gym} "} entirely, the inactivity line
     *  falls back to {@code النادي} ("the gym"). */
    private static final String WA_MESSAGE_EXPIRY =
            "مرحباً {name}، ينتهي اشتراكك في {gym} بتاريخ {date}. يرجى المرور إلى النادي للتجديد.";
    private static final String WA_MESSAGE_LAPSED_5D =
            "مرحباً {name}، لقد مرت 5 أيام على انتهاء اشتراكك في {gym} ولم تجدد بعد. "
            + "يسعدنا عودتك — مرّ لتجديد اشتراكك في أقرب وقت!";
    private static final String WA_MESSAGE_LAPSED_10D =
            "مرحباً {name}، مرت 10 أيام على انتهاء اشتراكك في {gym} ولم تجدد اشتراكك بعد. "
            + "لا تفوّت فرصة العودة، جدد اشتراكك اليوم!";
    private static final String WA_MESSAGE_INACTIVITY =
            "مرحباً {name}، لاحظنا أنك لم تزر {gym} منذ مدة. اشتراكك لا يزال ساري المفعول ونتطلّع لرؤيتك قريباً!";

    /** True while a review is on screen — the scheduler waits rather than
     *  stacking a second card on top. */
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    public static boolean isActive() {
        return ACTIVE.get();
    }

    private ReminderReviewDialog() {
    }

    /** Runs the review modally over {@code owner}. Each entry carries the member
     *  and why they were queued ({@link ReminderType}). */
    public static void show(Window owner, List<ReminderDao.Due> reminders) {
        if (reminders == null || reminders.isEmpty()) {
            return;
        }
        new ReminderReviewDialog().run(owner, List.copyOf(reminders));
    }

    private final MemberDao memberDao = MemberDao.getInstance();
    private List<ReminderDao.Due> items;
    private int index;
    private int currentMemberId;
    private ReminderType currentType = ReminderType.EXPIRY;
    private String currentPhone;
    private String currentFirstName;
    private String currentEndDate;

    private Stage stage;
    private VBox card;
    private Window owner;

    private void run(Window owner, List<ReminderDao.Due> items) {
        this.items = items;
        this.owner = owner;

        card = new VBox(14);
        card.getStyleClass().add("reminder-review-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(380);
        card.setPadding(new Insets(30, 30, 24, 30));

        StackPane wrapper = new StackPane(card);
        wrapper.setStyle("-fx-background-color: transparent;");
        wrapper.setPadding(new Insets(36));

        Scene scene = new Scene(wrapper);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        stage = new Stage(StageStyle.TRANSPARENT);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(scene);
        DialogEffects.preventShowFlash(stage);

        if (!renderCurrent()) {
            return;                       // nothing resolvable to show
        }
        ACTIVE.set(true);
        try {
            SoundUtil.playWhatsappWhistle();
        } catch (RuntimeException ignored) {
            // a missing/failed sound must not hold up the reminder
        }
        // Give the whistle a clear head start so it is well underway before the
        // card fades in, instead of both landing at once.
        PauseTransition lead = new PauseTransition(Duration.millis(500));
        lead.setOnFinished(e -> Platform.runLater(() -> {
            try {
                stage.showAndWait();     // runLater: showAndWait can't run in the animation pulse
            } finally {
                ACTIVE.set(false);
            }
        }));
        lead.play();
    }

    /** Renders the next resolvable member; returns false when the list is done. */
    private boolean renderCurrent() {
        Member member = null;
        while (index < items.size()) {
            Optional<Member> m = memberDao.findById(items.get(index).memberId());
            if (m.isPresent()) {
                member = m.get();
                break;
            }
            index++;                       // member gone — skip its slot
        }
        if (member == null) {
            return false;
        }

        currentMemberId = member.getId();
        currentType = items.get(index).type();
        currentPhone = member.getPhoneNumber();
        currentFirstName = Converter.capitalize(safe(member.getFirstName()).trim());
        currentEndDate = member.getEndDate() != null ? member.getEndDate().format(DATE) : "";
        String fullName = (safe(member.getFirstName()) + " " + safe(member.getLastName())).trim();

        card.getChildren().setAll(
                photo(currentMemberId, fullName),
                name(fullName),
                whatsappBadge(),
                note(),
                details(member),
                progress(),
                actions());

        // The card's height changes with the details; re-fit the (already
        // shown) window so nothing on a later card is clipped.
        if (stage.isShowing()) {
            stage.sizeToScene();
            stage.centerOnScreen();
        }
        return true;
    }

    private Node photo(int memberId, String fullName) {
        StackPane holder = new StackPane();
        holder.setMinSize(92, 92);
        holder.setMaxSize(92, 92);
        holder.setClip(new Circle(46, 46, 46));

        Optional<Image> pic = PhotosService.loadPhoto(memberId);
        if (pic.isPresent()) {
            holder.getStyleClass().add("reminder-photo");
            Image img = pic.get();
            ImageView iv = new ImageView(img);
            iv.setFitWidth(92);
            iv.setFitHeight(92);
            double side = Math.min(img.getWidth(), img.getHeight());
            if (side > 0) {
                iv.setViewport(new Rectangle2D(
                        (img.getWidth() - side) / 2, (img.getHeight() - side) / 2, side, side));
            }
            holder.getChildren().add(iv);
        } else {
            holder.getStyleClass().add("avatar-photo");
            Label initials = new Label(initials(fullName));
            initials.getStyleClass().add("avatar-initials");
            holder.getChildren().add(initials);
        }

        // WhatsApp logo badge on the photo's bottom-right corner.
        FontIcon waIcon = new FontIcon("fab-whatsapp");
        waIcon.setIconSize(14);
        waIcon.setIconColor(Color.WHITE);
        StackPane mark = new StackPane(waIcon);
        mark.getStyleClass().add("reminder-wa-mark");

        StackPane framed = new StackPane(holder, mark);
        framed.setMaxSize(96, 96);
        StackPane.setAlignment(mark, Pos.BOTTOM_RIGHT);
        return framed;
    }

    private Label name(String fullName) {
        Label l = new Label(fullName.isEmpty() ? I18nService.get("Member") : fullName);
        l.getStyleClass().add("reminder-name");
        l.setWrapText(true);
        l.setMaxWidth(320);
        l.setAlignment(Pos.CENTER);
        l.setTextAlignment(TextAlignment.CENTER);
        return l;
    }

    private Node whatsappBadge() {
        FontIcon icon = new FontIcon("fab-whatsapp");
        icon.setIconSize(15);
        icon.setIconColor(Color.web("#25D366"));            // WhatsApp brand green
        String key = switch (currentType) {
            case EXPIRY -> "Reminder_expiry_title";
            case LAPSED_5D -> "Reminder_lapsed5_title";
            case LAPSED_10D -> "Reminder_lapsed10_title";
            case INACTIVITY -> "Reminder_inactivity_title";
        };
        Label l = new Label(I18nService.get(key), icon);
        l.setGraphicTextGap(7);
        l.getStyleClass().add("reminder-wa-badge");
        HBox box = new HBox(l);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** One-line explanation of why this member is in the review, per type. */
    private Label note() {
        String key = switch (currentType) {
            case EXPIRY -> "Reminder_expiry_note";
            case LAPSED_5D -> "Reminder_lapsed5_note";
            case LAPSED_10D -> "Reminder_lapsed10_note";
            case INACTIVITY -> "Reminder_inactivity_note";
        };
        Label l = new Label(I18nService.get(key));
        l.getStyleClass().add("reminder-note");
        l.setWrapText(true);
        l.setMaxWidth(320);
        l.setAlignment(Pos.CENTER);
        l.setTextAlignment(TextAlignment.CENTER);
        return l;
    }

    private Node details(Member m) {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("reminder-details");
        box.setPadding(new Insets(2, 0, 2, 0));

        String phone = safe(m.getPhoneNumber()).trim();
        if (!phone.isEmpty() && !phone.equals("0")) {
            box.getChildren().add(detailRow("fas-phone-alt", phone));
        }
        String plan = m.getPlanName();
        if (plan != null && !plan.isBlank()) {
            box.getChildren().add(detailRow("fas-tag", plan));
        }
        if (m.getEndDate() != null) {
            box.getChildren().add(detailRow("fas-calendar-times",
                    I18nService.get("Card_expires") + " " + m.getEndDate().format(DATE)));
        }
        Integer rd = m.getRemainingDays();
        if (rd != null) {
            box.getChildren().add(detailRow("fas-hourglass-half",
                    rd + " " + I18nService.get("Remaining_Days").toLowerCase()));
        }
        return box;
    }

    private HBox detailRow(String iconLiteral, String text) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(13);
        icon.getStyleClass().add("reminder-detail-icon");
        Label l = new Label(text);
        l.getStyleClass().add("reminder-detail-label");
        l.setWrapText(true);
        l.setMaxWidth(280);
        HBox row = new HBox(9, icon, l);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label progress() {
        Label l = new Label((index + 1) + " / " + items.size());
        l.getStyleClass().add("reminder-progress");
        return l;
    }

    private Node actions() {
        Button send = new Button(I18nService.get("Send"));
        send.getStyleClass().add("reminder-send-button");
        send.setMaxWidth(Double.MAX_VALUE);
        send.setDefaultButton(true);
        send.setOnAction(e -> {
            openWhatsApp();
            try {
                ReminderDao.getInstance().record(currentMemberId);
            } catch (RuntimeException ex) {
                System.err.println("Failed to record reminder for member "
                        + currentMemberId + ": " + ex.getMessage());
            }
            advance();
        });

        Button skip = new Button(I18nService.get("Skip"));
        skip.getStyleClass().add("reminder-skip-button");
        skip.setMaxWidth(Double.MAX_VALUE);
        skip.setCancelButton(true);   // Esc = skip
        // No WhatsApp, no reminders row — the member returns on the next sweep.
        skip.setOnAction(e -> advance());

        VBox box = new VBox(8, send, skip);
        box.setFillWidth(true);
        return box;
    }

    /** Move to the next card, closing the review when the list is done. */
    private void advance() {
        index++;
        if (!renderCurrent()) {
            stage.close();
        }
    }

    /** Hands the current member's chat to the single reusable WhatsApp window
     *  (see {@link WhatsAppWindow}) — the same window for every card in the
     *  review, message pre-filled. */
    private void openWhatsApp() {
        String num = normalizePhone(currentPhone);
        if (num.isEmpty()) {
            return;
        }
        String text = buildMessage();
        // Centre over the GymGate window — read here on the FX thread; the open
        // runs off it (HTTP/WebSocket) so a slow browser can't freeze the card.
        int[] pos = centeredOverOwner(WhatsAppWindow.WIN_W, WhatsAppWindow.WIN_H);
        Thread t = new Thread(() -> WhatsAppWindow.openChat(num, text, pos), "whatsapp-open");
        t.setDaemon(true);
        t.start();
    }

    /** The pre-filled Arabic message for the current member, per {@link ReminderType}. */
    private String buildMessage() {
        String gym = Settings.getGymName();
        gym = gym == null ? "" : gym.trim();
        String tmpl = switch (currentType) {
            case EXPIRY -> WA_MESSAGE_EXPIRY;
            case LAPSED_5D -> WA_MESSAGE_LAPSED_5D;
            case LAPSED_10D -> WA_MESSAGE_LAPSED_10D;
            case INACTIVITY -> WA_MESSAGE_INACTIVITY;
        };
        if (gym.isEmpty()) {
            tmpl = currentType == ReminderType.INACTIVITY
                    ? tmpl.replace("{gym}", "النادي")   // "the gym"
                    : tmpl.replace("في {gym} ", "");    // EXPIRY / LAPSED_5D / LAPSED_10D
        } else {
            tmpl = tmpl.replace("{gym}", gym);
        }
        return tmpl
                .replace("{name}", currentFirstName == null ? "" : currentFirstName)
                .replace("{date}", currentEndDate == null ? "" : currentEndDate);
    }

    /** Top-left corner for a {@code w×h} popup centred on the owner window, or
     *  {@code null} if the owner geometry isn't known yet. */
    private int[] centeredOverOwner(int w, int h) {
        if (owner == null) {
            return null;
        }
        double ox = owner.getX(), oy = owner.getY(), ow = owner.getWidth(), oh = owner.getHeight();
        if (Double.isNaN(ox) || Double.isNaN(oy) || ow <= 0 || oh <= 0) {
            return null;
        }
        int x = (int) Math.round(ox + (ow - w) / 2);
        int y = (int) Math.round(oy + (oh - h) / 2);
        return new int[] {Math.max(0, x), Math.max(0, y)};
    }

    /** Algerian phone → E.164 digits (no '+'), e.g. 0770 99 00 11 → 213770990011. */
    private static String normalizePhone(String raw) {
        if (raw == null) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return "";
        }
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        } else if (digits.startsWith("0")) {
            digits = "213" + digits.substring(1);
        } else if (!digits.startsWith("213")) {
            digits = "213" + digits;
        }
        return digits;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String initials(String fullName) {
        StringBuilder sb = new StringBuilder();
        for (String part : fullName.trim().split("\\s+")) {
            if (!part.isEmpty() && sb.length() < 2) {
                sb.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }
}
