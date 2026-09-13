package com.GymGate.bussines.db.dao;

import com.GymGate.bussines.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The local-only {@code reminders} table — a per-day "already reminded" log,
 * never synced to Supabase. One row per member ({@code member_id}, {@code date});
 * a member's {@code end_date} can only match one of the four categories below at
 * a time, so no reminder type is stored. The {@link ReminderType} carried on
 * {@link Due} only tells the review which card / message to render.
 *
 * <p>Flow (see {@code ReminderScheduler}): once at startup {@link #purgeStale()}
 * drops every row not dated today; then every hour {@link #expiredToRemind()},
 * {@link #lapsed5DaysToRemind()}, {@link #lapsed10DaysToRemind()} and
 * {@link #inactiveToRemind()} each return the members who qualify and have no row
 * yet. Each is shown in the review, and {@link #record} logs a member only when
 * staff hit Send on their card — so an interrupted review leaves the rest for the
 * next sweep. A renewal / check-in removes the row via {@link #deleteForMember}.
 */
public class ReminderDao {

    private final Connection connection;
    private static ReminderDao instance;

    private ReminderDao() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    public static synchronized ReminderDao getInstance() {
        if (instance == null) {
            instance = new ReminderDao();
        }
        return instance;
    }

    /** A member to remind + why. */
    public record Due(int memberId, ReminderType type) {
    }

    /**
     * Drops every reminder not dated today (local). Run once on startup, before
     * the first hourly sweep — not on a schedule.
     *
     * @return how many rows were removed
     */
    public int purgeStale() {
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM reminders WHERE date <> date('now','localtime')")) {
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to purge stale reminders", e);
        }
    }

    /**
     * Members whose plan ends <b>today</b> — their last valid day — who still
     * have visit-days left and a phone number, and who have not been reminded
     * today. Reminded once, on the expiry day only. Ordered by id.
     */
    public List<Integer> expiredToRemind() {
        String today = LocalDate.now().toString();
        return ids("""
            SELECT m.id
            FROM members m
            WHERE m.plan_id IS NOT NULL
              AND date(m.end_date) = ?
              AND (m.remaining_days IS NULL OR m.remaining_days > 0)
              AND m.phone_number IS NOT NULL AND TRIM(m.phone_number) NOT IN ('', '0')
              AND NOT EXISTS (SELECT 1 FROM reminders r WHERE r.member_id = m.id)
            ORDER BY m.id
            """, "Failed to load expiring members", today);
    }

    /**
     * Members whose plan ended <b>exactly 5 days ago</b> and who still haven't
     * renewed ({@code plan_id} stays set until a renewal reassigns it or a
     * cancellation clears it — see {@code SyncingService.MemberChange}), who have
     * a phone number and have not been reminded today. A first, softer win-back
     * follow-up after {@link #expiredToRemind()}. Ordered by id.
     */
    public List<Integer> lapsed5DaysToRemind() {
        String fiveDaysAgo = LocalDate.now().minusDays(5).toString();
        return ids("""
            SELECT m.id
            FROM members m
            WHERE m.plan_id IS NOT NULL
              AND date(m.end_date) = ?
              AND m.phone_number IS NOT NULL AND TRIM(m.phone_number) NOT IN ('', '0')
              AND NOT EXISTS (SELECT 1 FROM reminders r WHERE r.member_id = m.id)
            ORDER BY m.id
            """, "Failed to load 5-day-lapsed members", fiveDaysAgo);
    }

    /**
     * Members whose plan ended <b>exactly 10 days ago</b> and who still haven't
     * renewed — a more urgent follow-up for members the 5-day nudge didn't bring
     * back. Same eligibility rules as {@link #lapsed5DaysToRemind()}. Ordered by id.
     */
    public List<Integer> lapsed10DaysToRemind() {
        String tenDaysAgo = LocalDate.now().minusDays(10).toString();
        return ids("""
            SELECT m.id
            FROM members m
            WHERE m.plan_id IS NOT NULL
              AND date(m.end_date) = ?
              AND m.phone_number IS NOT NULL AND TRIM(m.phone_number) NOT IN ('', '0')
              AND NOT EXISTS (SELECT 1 FROM reminders r WHERE r.member_id = m.id)
            ORDER BY m.id
            """, "Failed to load 10-day-lapsed members", tenDaysAgo);
    }

    /**
     * Members whose subscription is still active with time left (plan set, end
     * date <b>after today</b>, visit-days left) but whose {@code last_visit} is
     * <b>exactly 10 days ago</b>, who have a phone number and have not been
     * reminded today. End-date == today is left to {@link #expiredToRemind()} so
     * a member is never in both lists. Ordered by id.
     */
    public List<Integer> inactiveToRemind() {
        String today = LocalDate.now().toString();
        String tenDaysAgo = LocalDate.now().minusDays(10).toString();
        return ids("""
            SELECT m.id
            FROM members m
            WHERE m.plan_id IS NOT NULL
              AND date(m.end_date) > ?
              AND (m.remaining_days IS NULL OR m.remaining_days > 0)
              AND m.last_visit IS NOT NULL AND date(m.last_visit) = ?
              AND m.phone_number IS NOT NULL AND TRIM(m.phone_number) NOT IN ('', '0')
              AND NOT EXISTS (SELECT 1 FROM reminders r WHERE r.member_id = m.id)
            ORDER BY m.id
            """, "Failed to load inactive members", today, tenDaysAgo);
    }

    private List<Integer> ids(String sql, String err, String... params) {
        List<Integer> out = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setString(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(err, e);
        }
        return out;
    }

    /** Logs that {@code memberId} was reminded today ({@code date} defaults to
     *  the local day) — keeps them out of the rest of the day's sweeps. */
    public void record(int memberId) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO reminders(member_id) VALUES (?)")) {
            stmt.setInt(1, memberId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to record reminder for member " + memberId, e);
        }
    }

    /** Removes the member's reminder — on a renewal (their expiry reminder) or a
     *  check-in (their inactivity reminder). A member has at most one row, so
     *  member_id alone identifies it. Returns rows deleted. */
    public int deleteForMember(int memberId) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM reminders WHERE member_id = ?")) {
            stmt.setInt(1, memberId);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete reminder for member " + memberId, e);
        }
    }
}
