package com.GymGate.bussines.services;

import com.GymGate.UI.component.ReminderReviewDialog;
import com.GymGate.bussines.db.dao.ReminderDao;
import com.GymGate.bussines.db.dao.ReminderType;
import com.GymGate.bussines.models.Settings;
import javafx.application.Platform;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Startup: purge every reminder not dated today (once). Then, at a fixed rate of
 * one hour, sweep the members table for members due a reminder — expired today,
 * lapsed 5 days, lapsed 10 days, or 10-day-inactive — who have not been reminded
 * today, and present them all in one review (in that order). No per-reminder
 * timers.
 */
public final class ReminderScheduler {

    /** Wait for the main window (and the slower init tasks) before the first
     *  sweep. If the window still isn't up, that sweep is skipped and the next
     *  one (an hour later) retries. */
    private static final long FIRST_SWEEP_DELAY_SEC = 60;
    private static final long SWEEP_PERIOD_SEC = 3600;

    private static ReminderScheduler instance;

    public static synchronized ReminderScheduler getInstance() {
        if (instance == null) {
            instance = new ReminderScheduler();
        }
        return instance;
    }

    /** Stops the sweep only if it was ever started (shutdown path). */
    public static synchronized void stopIfRunning() {
        if (instance != null) {
            instance.exec.shutdownNow();
        }
    }

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gymgate-reminders");
        t.setDaemon(true);
        return t;
    });
    private final ReminderDao reminderDao = ReminderDao.getInstance();
    private boolean started;

    /** Call once, from startup. Purges stale rows immediately, then arms the
     *  hourly sweep. */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        try {
            int purged = reminderDao.purgeStale();
            if (purged > 0) {
                System.out.println("Removed " + purged + " reminder(s) from a previous day");
            }
        } catch (RuntimeException e) {
            System.err.println("Reminder purge failed (non-fatal): " + e.getMessage());
        }
        exec.scheduleAtFixedRate(this::sweep, FIRST_SWEEP_DELAY_SEC, SWEEP_PERIOD_SEC, TimeUnit.SECONDS);
    }

    /** One hourly pass. Runs on the scheduler thread; must never throw (a
     *  {@code scheduleAtFixedRate} task that throws is silently cancelled). */
    private void sweep() {
        try {
            if (!Settings.isWhatsappEnabled()) {
                return;
            }
            // The review is useless offline (WhatsApp Web won't load), so only
            // fetch/queue members when Supabase — hence the internet — answers.
            if (!SupabaseClient.isReachable()) {
                return;
            }
            List<ReminderDao.Due> batch = new ArrayList<>();
            for (int id : reminderDao.expiredToRemind()) {
                batch.add(new ReminderDao.Due(id, ReminderType.EXPIRY));
            }
            for (int id : reminderDao.lapsed5DaysToRemind()) {
                batch.add(new ReminderDao.Due(id, ReminderType.LAPSED_5D));
            }
            for (int id : reminderDao.lapsed10DaysToRemind()) {
                batch.add(new ReminderDao.Due(id, ReminderType.LAPSED_10D));
            }
            for (int id : reminderDao.inactiveToRemind()) {
                batch.add(new ReminderDao.Due(id, ReminderType.INACTIVITY));
            }
            if (!batch.isEmpty()) {
                Platform.runLater(() -> present(batch));
            }
        } catch (Throwable t) {
            System.err.println("Reminder sweep failed — " + t.getMessage());
        }
    }

    /** On the FX thread: if nothing else has the user, open the review. Each
     *  member is logged only when staff hit <b>Send</b> on their card, so a
     *  review interrupted part-way leaves the rest to the next hourly sweep. If
     *  the window/dialog isn't ready this whole sweep is skipped and retried. */
    private void present(List<ReminderDao.Due> batch) {
        Stage owner = RestartAppService.mainStage;
        boolean ready = owner != null && owner.isShowing()
                && !ReminderReviewDialog.isActive()
                && !otherModalOpen();
        if (!ready) {
            return;
        }
        try {
            ReminderReviewDialog.show(owner, batch);
        } catch (RuntimeException e) {
            System.err.println("Reminder review failed to open — " + e.getMessage());
        }
    }

    /** True if some other modal dialog (register, renew, a confirm) currently
     *  has the user — don't pop a reminder card on top of it. */
    private static boolean otherModalOpen() {
        for (Window w : Window.getWindows()) {
            if (w.isShowing() && w instanceof Stage s
                    && s.getModality() != Modality.NONE
                    && s != RestartAppService.mainStage
                    && s != RestartAppService.secondaryStage) {
                return true;
            }
        }
        return false;
    }
}
