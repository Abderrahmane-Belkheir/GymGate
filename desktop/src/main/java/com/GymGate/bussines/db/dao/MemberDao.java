package com.GymGate.bussines.db.dao;

import com.GymGate.bussines.db.DatabaseManager;
import com.GymGate.bussines.db.entities.Member;
import com.GymGate.bussines.models.Sexe;
import com.GymGate.bussines.models.State;
import com.GymGate.bussines.services.SyncingService;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MemberDao {

    /** {@code created_at} is stored as {@code datetime('now')} text. Parsed on
     *  every row of {@code findAll} and on every {@code findById} (the
     *  recognition path), so the formatter is built once, not per call. */
    private static final DateTimeFormatter CREATED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Connection connection;
    private static MemberDao instance;

    private MemberDao() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }


    public Member insert(Member member) {
        String sql = """
        INSERT INTO members (
            first_name,
            last_name,
            phone_number,
            sexe,
            plan_id,
            start_date,
            end_date,
            remaining_days
        )
        VALUES (?, ?, ?, ?, ?, ? , ?, ?)
        """;

        try (PreparedStatement stmt =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            int index = 1;

            stmt.setString(index++, member.getFirstName());
            stmt.setString(index++, member.getLastName());
            stmt.setString(index++, nullIfBlank(member.getPhoneNumber()));
            stmt.setString(index++, (member.getSexe() == null ? Sexe.MALE : member.getSexe()).name());
            stmt.setInt(index++, member.getPlanId());
            stmt.setString(index++, String.valueOf(member.getStartDate()));
            stmt.setString(index++, String.valueOf(member.getEndDate()));
            if(member.getRemainingDays()==null){
                stmt.setNull(index,Types.INTEGER);}else{stmt.setInt(index, member.getRemainingDays());}

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    member.setId(keys.getInt(1));
                }
            }

            SyncingService.getInstance().pushDirectly("members");
            return member;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert member", e);
        }
    }

    public void update(Member member) {

        String sql = """
        UPDATE members
        SET
            first_name = ?,
            last_name = ?,
            phone_number = ?,
            plan_id = ?,
            start_date = ?,
            end_date = ?,
            remaining_days = ?,
            synced = 0
        WHERE id = ?
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            int index = 1;

            stmt.setString(index++, member.getFirstName());
            stmt.setString(index++, member.getLastName());
            stmt.setString(index++, nullIfBlank(member.getPhoneNumber()));

            if (member.getPlanId() == null) {stmt.setNull(index++, Types.INTEGER);} else {stmt.setInt(index++, member.getPlanId());}

            if(member.getStartDate()==null){stmt.setNull(index++,Types.VARCHAR);} else {stmt.setString(index++,String.valueOf(member.getStartDate()));}

            if(member.getEndDate()==null){stmt.setNull(index++,Types.VARCHAR);} else {stmt.setString(index++,String.valueOf(member.getEndDate()));}

            if (member.getRemainingDays() == null) {stmt.setNull(index++, Types.INTEGER);} else {stmt.setInt(index++, member.getRemainingDays());}

            stmt.setInt(index, member.getId());

            stmt.executeUpdate();
            SyncingService.getInstance().pushDirectly("members");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update member" + e.getMessage());
        }
    }

    public void onAttendance(int id){

        String sql = """
        UPDATE members
        SET
            remaining_days = remaining_days-1,
            synced = 0
        WHERE id = ? AND remaining_days IS NOT NULL
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setInt(1, id);

            stmt.executeUpdate();
            SyncingService.getInstance().pushDirectly("members");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to decrease the remaining days", e);
        }
    }

    /** Stamps the member's {@code last_visit} with {@code visitDate} (used on
     *  every check-in). Unconditional — unlike {@link #onAttendance}, this also
     *  covers members on an unlimited plan. */
    public void markVisited(int id, LocalDate visitDate) {
        String sql = "UPDATE members SET last_visit = ?, synced = 0 WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, visitDate.toString());
            stmt.setInt(2, id);
            stmt.executeUpdate();
            SyncingService.getInstance().pushDirectly("members");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to stamp last_visit for member " + id, e);
        }
    }

    public Optional<Member> findById(int id) {
        String sql = """
               SELECT
               m.*,
               pl.name
               FROM members m
               LEFT JOIN plans pl ON pl.id = m.plan_id
               WHERE m.id= ?
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find member " + id, e);
        }
    }



    public List<Member> findAll(Sexe sexe) {

        List<Member> members = new ArrayList<>();
        String sql = """
               SELECT
               m.*,
               pl.name
               FROM members m
               LEFT JOIN plans pl ON pl.id = m.plan_id
               WHERE m.sexe = ?
               ORDER BY m.created_at DESC;
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sexe.name());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    members.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load members", e);
        }
        return members;
    }

    public Member mapRow(ResultSet rs) throws SQLException {

        Member m = new Member();
        m.setId(rs.getInt("id"));
        m.setFirstName(rs.getString("first_name"));
        m.setLastName(rs.getString("last_name"));
        m.setPhoneNumber(rs.getString("phone_number"));
        String sexeStr = rs.getString("sexe");
        m.setSexe(sexeStr == null ? null : Sexe.valueOf(sexeStr));
        int planId=rs.getInt("plan_id");
        m.setPlanId(rs.wasNull()?null:planId);
        String startDateStr = rs.getString("start_date");
        m.setStartDate(startDateStr == null ? null : LocalDate.parse(startDateStr));
        String endDateStr=rs.getString("end_date");
        m.setEndDate(endDateStr==null?null:LocalDate.parse(endDateStr));
        int remainingDays=rs.getInt("remaining_days");
        m.setRemainingDays(rs.wasNull()?null:remainingDays);
        String lastVisitStr = rs.getString("last_visit");
        m.setLastVisit(lastVisitStr == null || lastVisitStr.isBlank() ? null
                : LocalDate.parse(lastVisitStr.length() >= 10 ? lastVisitStr.substring(0, 10) : lastVisitStr));
        m.setPlanName(rs.getString("name"));
        String createdDateStr=rs.getString("created_at");
        LocalDateTime createdDate = LocalDateTime.parse(createdDateStr, CREATED_AT_FORMAT);
        m.setCreatedDate(createdDate.toLocalDate());
        return m;

    }

    /** Phone number is optional; store a blank one as SQL NULL. */
    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    public int count(MemberCountType type) {

        StringBuilder sql = new StringBuilder("""
        SELECT COUNT(*)
        FROM members
        WHERE 1 = 1
    """);

        switch (type) {
            case NEW ->  sql.append("""
            AND date(created_at) = ?
        """);

            case RENEW -> sql.append("""
            AND date(start_date) = ? AND date(created_at)!=date(start_date)
        """);

            case EXPIRED -> sql.append("""
            AND date(end_date) = ? OR (remaining_days <= 0 AND EXISTS (
                SELECT 1 FROM attendance
                WHERE attendance.member_id = members.id
                  AND substr(attendance.date, 1, 10) = ?
            ))
      """);
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            stmt.setString(1,LocalDate.now().toString());
            if (type == MemberCountType.EXPIRED) {
                stmt.setString(2, LocalDate.now().toString());
            }
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to count members", e);
        }
    }
    /** How many members are currently assigned to the given plan (plan_id match).
     *  Cancelled members (plan_id NULL) and members who have since renewed onto a
     *  different plan are not counted. */
    public int countByPlan(int planId) {
        String sql = "SELECT COUNT(*) FROM members WHERE plan_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, planId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count members for plan " + planId, e);
        }
    }

    // ---- Reports ------------------------------------------------------

    /**
     * Live headcount by membership state, using the same valid / expiring /
     * expired rules as the Members-screen status pill (see
     * docs/member-stats-consistency.md §3). Returns
     * {@code {active, expiringSoon, expired, noPlan}}.
     */
    public int[] composition(int expiringSoonDays) {
        String today = LocalDate.now().toString();
        String soon = LocalDate.now().plusDays(expiringSoonDays).toString();
        String valid = "plan_id IS NOT NULL AND end_date >= ? "
                + "AND (remaining_days IS NULL OR remaining_days > 0)";
        String warn = "(end_date <= ? OR (remaining_days IS NOT NULL AND remaining_days <= ?))";
        String sql = "SELECT "
                + "SUM(CASE WHEN " + valid + " AND NOT " + warn + " THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN " + valid + " AND " + warn + " THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN plan_id IS NOT NULL AND (end_date < ? "
                + "     OR (remaining_days IS NOT NULL AND remaining_days <= 0)) THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN plan_id IS NULL THEN 1 ELSE 0 END) "
                + "FROM members";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int i = 1;
            stmt.setString(i++, today); stmt.setString(i++, soon); stmt.setInt(i++, expiringSoonDays); // active
            stmt.setString(i++, today); stmt.setString(i++, soon); stmt.setInt(i++, expiringSoonDays); // expiring
            stmt.setString(i, today);                                                                  // expired
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)};
                }
                return new int[4];
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute member composition", e);
        }
    }

    /**
     * Members whose membership runs out within {@code withinDays} (by end date)
     * or has {@code <= lowRemaining} visit-days left, and isn't already expired.
     * Ordered by end date (soonest first).
     */
    public List<Member> expiringSoon(int withinDays, int lowRemaining) {
        String today = LocalDate.now().toString();
        String limit = LocalDate.now().plusDays(withinDays).toString();
        String sql = """
            SELECT m.*, pl.name
            FROM members m LEFT JOIN plans pl ON pl.id = m.plan_id
            WHERE m.plan_id IS NOT NULL
              AND m.end_date >= ?
              AND (m.remaining_days IS NULL OR m.remaining_days > 0)
              AND ( m.end_date <= ?
                    OR (m.remaining_days IS NOT NULL AND m.remaining_days <= ?) )
            ORDER BY m.end_date ASC
            """;
        List<Member> out = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, today);
            stmt.setString(2, limit);
            stmt.setInt(3, lowRemaining);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load expiring members", e);
        }
    }

    // ---- Home stat-card drill-downs --------------------------------------
    //
    // Each of these returns the actual member rows behind one "Today's
    // Statistics" card, using the exact same rule as the matching
    // count(MemberCountType) branch — the card's number and the length of
    // this list are always the same figure.

    /** The members counted by {@link #count}({@code EXPIRED}): plan ends today,
     *  or remaining days have hit zero and they checked in today. */
    public List<Member> expiringToday() {
        String today = LocalDate.now().toString();
        String sql = """
            SELECT m.*, pl.name
            FROM members m
            LEFT JOIN plans pl ON pl.id = m.plan_id
            WHERE date(m.end_date) = ?
               OR (m.remaining_days <= 0 AND EXISTS (
                     SELECT 1 FROM attendance
                     WHERE attendance.member_id = m.id
                       AND substr(attendance.date, 1, 10) = ?
                  ))
            ORDER BY m.end_date ASC
            """;
        return queryMembers(sql, today, today);
    }

    /** The members counted by {@link #count}({@code RENEW}): their current plan
     *  started today and that is not their registration day. */
    public List<Member> renewedToday() {
        String today = LocalDate.now().toString();
        String sql = """
            SELECT m.*, pl.name
            FROM members m
            LEFT JOIN plans pl ON pl.id = m.plan_id
            WHERE date(m.start_date) = ?
              AND date(m.created_at) != date(m.start_date)
            ORDER BY m.start_date DESC
            """;
        return queryMembers(sql, today);
    }

    /** The members counted by {@link #count}({@code NEW}): registered today. */
    public List<Member> newToday() {
        String today = LocalDate.now().toString();
        String sql = """
            SELECT m.*, pl.name
            FROM members m
            LEFT JOIN plans pl ON pl.id = m.plan_id
            WHERE date(m.created_at) = ?
            ORDER BY m.created_at DESC
            """;
        return queryMembers(sql, today);
    }

    private List<Member> queryMembers(String sql, String... params) {
        List<Member> out = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setString(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load members", e);
        }
    }

    public void delete(int id){
        String sql= """
                DELETE FROM members WHERE id= ?
                """;
        try(PreparedStatement stmt=connection.prepareStatement(sql)) {
            stmt.setInt(1,id);
            stmt.executeUpdate();
        }catch (SQLException ex){
            throw new RuntimeException("Failed to delete member", ex);
        }
        DeletionDao.getInstance().record("members", id);
        SyncingService.getInstance().deleteDirectly("members");
    }
    public static synchronized MemberDao getInstance() {
        if (instance == null) {
            instance = new MemberDao();
        }
        return instance;
    }



}