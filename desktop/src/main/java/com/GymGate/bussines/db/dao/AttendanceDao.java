package com.GymGate.bussines.db.dao;

import com.GymGate.bussines.db.DatabaseManager;
import com.GymGate.bussines.db.entities.Attendance;
import com.GymGate.bussines.models.AttendanceRecord;
import com.GymGate.bussines.models.LatestAttendance;
import com.GymGate.bussines.services.SyncingService;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AttendanceDao {

    private final Connection connection;
    private static AttendanceDao instance;

    private AttendanceDao() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }
        public Attendance insert(Attendance attendance) {

        String sql = """
                INSERT INTO attendance(member_id, date)
                VALUES (?, ?)
                """;

        try (PreparedStatement stmt =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, attendance.getMemberId());
            stmt.setString(2, String.valueOf(attendance.getDate()));

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    attendance.setId(keys.getInt(1));
                }
            }

            SyncingService.getInstance().pushDirectly("attendance");
            return attendance;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert attendance", e);
        }
    }
    public List<LocalDateTime> findMemberAttendance(int id,Integer year,Integer month){
        List<Object> params=new ArrayList<>();
        StringBuilder sql = buildAttendanceQuery(
                params,
                """
                a.date
                """,
                year,
                month,
                null);
        sql.append("WHERE a.member_id= ? ");

        List<LocalDateTime> result=new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            for (int i=0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            stmt.setInt(params.size(),id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(LocalDateTime.parse(rs.getString(1)));
                }
            }

            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch attendance", e);
        }
    }

    private StringBuilder buildAttendanceQuery(
            List<Object> params,
            String selectClause,
            Integer year,
            Integer month,
            Integer day) {

        StringBuilder sql = new StringBuilder("""
        SELECT
    """);

        sql.append(selectClause);

        sql.append("""
        FROM attendance a
        JOIN members m ON m.id = a.member_id
        WHERE 1 = 1
    """);

        if (year != null) {
            sql.append(" AND strftime('%Y', a.date) = ?");
            params.add(String.format("%04d", year));
        }

        if (month != null) {
            sql.append(" AND strftime('%m', a.date) = ?");
            params.add(String.format("%02d", month));
        }

        if (day != null) {
            sql.append(" AND strftime('%d', a.date) = ?");
            params.add(String.format("%02d", day));
        }

        return sql;
    }



    public List<AttendanceRecord> find(
            Integer year,
            Integer month,
            Integer day) {

        List<Object> params = new ArrayList<>();

        StringBuilder sql = buildAttendanceQuery(
                params,
                """
                a.date,
                m.id,
                m.first_name,
                m.last_name
                """,
                year,
                month,
                day);

        sql.append(" ORDER BY a.date DESC");

        List<AttendanceRecord> result = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapAtt(rs));
                }
            }

            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch attendance", e);
        }
    }

    public int count(
            Integer year,
            Integer month,
            Integer day) {

        List<Object> params = new ArrayList<>();

        StringBuilder sql = buildAttendanceQuery(
                params,
                "COUNT(*)",
                year,
                month,
                day);

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to count attendance", e);
        }
    }

    /** Total check-ins between two dates, inclusive. {@code date} is an ISO
     *  LocalDateTime string, so a lexical [from, to+1day) range works. */
    public int countBetween(java.time.LocalDate from, java.time.LocalDate to) {
        String sql = "SELECT COUNT(*) FROM attendance WHERE date >= ? AND date < ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, from.toString());
            stmt.setString(2, to.plusDays(1).toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count attendance in range", e);
        }
    }

    /** Distinct members who checked in at least once between two dates, inclusive. */
    public int distinctVisitorsBetween(java.time.LocalDate from, java.time.LocalDate to) {
        String sql = "SELECT COUNT(DISTINCT member_id) FROM attendance WHERE date >= ? AND date < ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, from.toString());
            stmt.setString(2, to.plusDays(1).toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count distinct visitors", e);
        }
    }

    /** Every check-in timestamp within one calendar month, oldest first. */
    public List<LocalDateTime> findTimestamps(int year, int month) {
        String sql = """
            SELECT date FROM attendance
            WHERE strftime('%Y', date) = ? AND strftime('%m', date) = ?
            ORDER BY date ASC
            """;
        List<LocalDateTime> result = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, String.format("%04d", year));
            stmt.setString(2, String.format("%02d", month));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(LocalDateTime.parse(rs.getString(1)));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch attendance timestamps", e);
        }
    }

    /** Check-ins per day-of-month (1..31) for one month. Days with none are omitted. */
    public java.util.Map<Integer, Integer> countByDay(int year, int month) {
        String sql = """
            SELECT CAST(strftime('%d', date) AS INTEGER) AS d, COUNT(*)
            FROM attendance
            WHERE strftime('%Y', date) = ? AND strftime('%m', date) = ?
            GROUP BY d
            """;
        java.util.Map<Integer, Integer> result = new java.util.HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, String.format("%04d", year));
            stmt.setString(2, String.format("%02d", month));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt(1), rs.getInt(2));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count attendance by day", e);
        }
    }

    /** Check-ins per calendar month (1..12) for one year. Months with none are omitted. */
    public java.util.Map<Integer, Integer> countByMonth(int year) {
        String sql = """
            SELECT CAST(strftime('%m', date) AS INTEGER) AS m, COUNT(*)
            FROM attendance
            WHERE strftime('%Y', date) = ?
            GROUP BY m
            """;
        java.util.Map<Integer, Integer> result = new java.util.HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, String.format("%04d", year));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt(1), rs.getInt(2));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count attendance by month", e);
        }
    }

    /** Check-ins per year across the whole history, oldest year first. */
    public java.util.SortedMap<Integer, Integer> countByYear() {
        String sql = """
            SELECT CAST(strftime('%Y', date) AS INTEGER) AS y, COUNT(*)
            FROM attendance
            GROUP BY y
            ORDER BY y
            """;
        java.util.SortedMap<Integer, Integer> result = new java.util.TreeMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getInt(1), rs.getInt(2));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count attendance by year", e);
        }
    }

    private AttendanceRecord mapAtt(ResultSet rs) throws SQLException {
       return new AttendanceRecord(
               rs.getInt("id"),
               rs.getString("first_name"),
               rs.getString("last_name"),
               LocalDateTime.parse(rs.getString("date"))
        );
    }



    /** Every check-in timestamp for one member, oldest first. */
    public List<LocalDateTime> findDatesForMember(int memberId) {
        String sql = "SELECT date FROM attendance WHERE member_id = ? ORDER BY date ASC";
        List<LocalDateTime> result = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, memberId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(LocalDateTime.parse(rs.getString(1)));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch member attendance", e);
        }
    }

    public static synchronized AttendanceDao getInstance() {
        if (instance == null) {
            instance = new AttendanceDao();
        }
        return instance;
    }

    public boolean existToday(int memberId) {

        String sql = """
        SELECT 1
        FROM attendance
        WHERE member_id = ?
          AND substr(date, 1, 10) = ?
        LIMIT 1
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setInt(1, memberId);
            stmt.setString(2, LocalDate.now().toString()); // yyyy-MM-dd

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to check today's attendance", e);
        }
    }

    public Optional<LatestAttendance>  findLatest(){
        String sql= """
                SELECT
                a.date,
                a.member_id
                FROM attendance a
                WHERE date(a.date)= ?
                ORDER BY a.date DESC
                LIMIT 1
                """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1,LocalDate.now().toString());
           ResultSet rs = stmt.executeQuery();
                    return rs.next()?Optional.of(mapLatestAtt(rs)):Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch attendance", e);
        }

    }
    private LatestAttendance mapLatestAtt(ResultSet rs) throws SQLException {
        return new LatestAttendance(
                rs.getInt("member_id"),
                LocalDateTime.parse(rs.getString("date"))
        );
    }



}

