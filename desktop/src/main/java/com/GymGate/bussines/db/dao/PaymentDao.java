package com.GymGate.bussines.db.dao;
import com.GymGate.bussines.db.DatabaseManager;
import com.GymGate.bussines.db.entities.Payment;
import com.GymGate.bussines.models.PaymentRecord;
import com.GymGate.bussines.services.SyncingService;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PaymentDao {

    private final Connection connection;
    private static PaymentDao instance;

    public PaymentDao() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }


    public Payment insert(Payment payment) {

        String sql = """
                INSERT INTO payments (
                    member_id,
                    plan_id,
                    amount,
                    payment_date
                )
                VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement stmt =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            int index = 1;

            stmt.setInt(index++, payment.getMemberId());
            stmt.setInt(index++, payment.getPlanId());
            stmt.setDouble(index++, payment.getAmount());
            stmt.setString(index++,payment.getDate());
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    payment.setId(keys.getInt(1));
                }
            }

            SyncingService.getInstance().pushDirectly("payments");
            return payment;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert payment", e);
        }
    }

    /** Records a single-session ("séance") sale — no member, no plan, just the
     *  {@code seance} row and its price. */
    public void insertSeance(int seanceId, int amount) {
        String sql = "INSERT INTO payments (seance_id, amount, payment_date) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, seanceId);
            stmt.setInt(2, amount);
            stmt.setString(3, LocalDateTime.now().toString());
            stmt.executeUpdate();
            SyncingService.getInstance().pushDirectly("payments");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert séance payment", e);
        }
    }

    public List<PaymentRecord> find(
            Integer year,
            Integer month,
            Integer day) {

        List<Object> params = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
        SELECT
            p.member_id,
            p.seance_id,
            p.amount,
            p.payment_date,
            m.first_name,
            m.last_name,
            pl.name,
            sc.sexe   AS seance_sexe,
            sc.cardio AS seance_cardio
        FROM payments p
        LEFT JOIN members m ON m.id = p.member_id
        LEFT JOIN plans   pl ON pl.id = p.plan_id
        LEFT JOIN seance  sc ON sc.id = p.seance_id
        WHERE 1 = 1
        """);

        if (year != null) {
            sql.append(" AND strftime('%Y', p.payment_date) = ?");
            params.add(String.format("%04d", year));
        }

        if (month != null) {
            sql.append(" AND strftime('%m', p.payment_date) = ?");
            params.add(String.format("%02d", month));
        }

        if (day != null) {
            sql.append(" AND strftime('%d', p.payment_date) = ?");
            params.add(String.format("%02d", day));
        }

        sql.append(" ORDER BY p.payment_date DESC");

        List<PaymentRecord> result = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapPayOrSeance(rs));
                }
            }

            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch payments", e);
        }
    }

    public int sumAmount(
            Integer year,
            Integer month,
            Integer day) {

        List<Object> params = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
        SELECT
            COALESCE(SUM(amount), 0)
        FROM payments
        WHERE 1 = 1
        """);

        if (year != null) {
            sql.append(" AND strftime('%Y', payment_date) = ?");
            params.add(String.format("%04d", year));
        }

        if (month != null) {
            sql.append(" AND strftime('%m', payment_date) = ?");
            params.add(String.format("%02d", month));
        }

        if (day != null) {
            sql.append(" AND strftime('%d', payment_date) = ?");
            params.add(String.format("%02d", day));
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to sum payments", e);
        }
    }


    /** Revenue per day-of-month (1..31) for one month. Days with no payment are omitted. */
    public java.util.Map<Integer, Integer> revenueByDay(int year, int month) {
        String sql = """
            SELECT CAST(strftime('%d', payment_date) AS INTEGER) AS d, COALESCE(SUM(amount), 0)
            FROM payments
            WHERE strftime('%Y', payment_date) = ? AND strftime('%m', payment_date) = ?
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
            throw new RuntimeException("Failed to sum revenue by day", e);
        }
    }

    /** Revenue per calendar month (1..12) for one year. Months with no payment are omitted. */
    public java.util.Map<Integer, Integer> revenueByMonth(int year) {
        String sql = """
            SELECT CAST(strftime('%m', payment_date) AS INTEGER) AS m, COALESCE(SUM(amount), 0)
            FROM payments
            WHERE strftime('%Y', payment_date) = ?
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
            throw new RuntimeException("Failed to sum revenue by month", e);
        }
    }

    /** Revenue per year across the whole history, oldest year first. */
    public java.util.SortedMap<Integer, Integer> revenueByYear() {
        String sql = """
            SELECT CAST(strftime('%Y', payment_date) AS INTEGER) AS y, COALESCE(SUM(amount), 0)
            FROM payments
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
            throw new RuntimeException("Failed to sum revenue by year", e);
        }
    }

    /** All payments made by one member, oldest first. */
    public List<PaymentRecord> findByMember(int memberId) {
        String sql = """
            SELECT
                p.member_id,
                p.amount,
                p.payment_date,
                m.first_name,
                m.last_name,
                pl.name
            FROM payments p
            JOIN members m ON m.id = p.member_id
            JOIN plans pl ON pl.id = p.plan_id
            WHERE p.member_id = ?
            ORDER BY p.payment_date ASC
            """;

        List<PaymentRecord> result = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, memberId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapPay(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch member payments", e);
        }
    }

    // ---- Reports: date-range aggregates ---------------------------------
    //
    // payment_date is an ISO LocalDateTime string, so a lexical
    // [from 00:00, to+1day 00:00) range works. "First payment" (registration)
    // vs a later payment (renewal) is decided per row against MIN(payment_date)
    // for that member. All figures exclude members deleted since (CASCADE).

    private static String lo(LocalDate from) {
        return from.toString();
    }

    private static String hi(LocalDate toInclusive) {
        return toInclusive.plusDays(1).toString();
    }

    /** Total revenue between two dates, inclusive. */
    public int sumBetween(LocalDate from, LocalDate to) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM payments "
                + "WHERE payment_date >= ? AND payment_date < ?";
        return oneInt(sql, lo(from), hi(to));
    }

    /** Revenue from single-session ("séance") sales between two dates, inclusive. */
    public int seanceRevenueBetween(LocalDate from, LocalDate to) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM payments "
                + "WHERE seance_id IS NOT NULL AND payment_date >= ? AND payment_date < ?";
        return oneInt(sql, lo(from), hi(to));
    }

    /** Number of payments (registrations + renewals) between two dates, inclusive. */
    public int paymentCountBetween(LocalDate from, LocalDate to) {
        String sql = "SELECT COUNT(*) FROM payments WHERE payment_date >= ? AND payment_date < ?";
        return oneInt(sql, lo(from), hi(to));
    }

    /** Members whose very first payment falls in the range — i.e. joined then. */
    public int newMemberCountBetween(LocalDate from, LocalDate to) {
        String sql = """
            SELECT COUNT(*) FROM (
                SELECT member_id, MIN(payment_date) AS first_pay
                FROM payments GROUP BY member_id
            ) WHERE first_pay >= ? AND first_pay < ?
            """;
        return oneInt(sql, lo(from), hi(to));
    }

    /** Payments in the range that are NOT the member's first — i.e. renewals. */
    public int renewalCountBetween(LocalDate from, LocalDate to) {
        String sql = """
            SELECT COUNT(*) FROM payments p
            WHERE p.payment_date >= ? AND p.payment_date < ?
              AND p.payment_date > (
                  SELECT MIN(p2.payment_date) FROM payments p2 WHERE p2.member_id = p.member_id
              )
            """;
        return oneInt(sql, lo(from), hi(to));
    }

    /** Revenue by plan name for the range, highest first. */
    public Map<String, Integer> revenueByPlanBetween(LocalDate from, LocalDate to) {
        String sql = """
            SELECT pl.name, COALESCE(SUM(p.amount), 0) AS rev
            FROM payments p JOIN plans pl ON pl.id = p.plan_id
            WHERE p.payment_date >= ? AND p.payment_date < ?
            GROUP BY pl.id
            ORDER BY rev DESC
            """;
        Map<String, Integer> out = new LinkedHashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, lo(from));
            stmt.setString(2, hi(to));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getInt(2));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sum revenue by plan", e);
        }
    }

    /**
     * Registration vs renewal revenue for the last {@code months} calendar
     * months (oldest first, every month present). {@code int[]} is
     * {@code {registrationRevenue, renewalRevenue}}.
     */
    public Map<YearMonth, int[]> revenueSplitByMonth(int months) {
        YearMonth start = YearMonth.now().minusMonths(months - 1L);
        String sql = """
            SELECT strftime('%Y-%m', p.payment_date) AS ym,
                   CASE WHEN p.payment_date = (
                            SELECT MIN(p2.payment_date) FROM payments p2 WHERE p2.member_id = p.member_id
                        ) THEN 0 ELSE 1 END AS is_renewal,
                   COALESCE(SUM(p.amount), 0) AS rev
            FROM payments p
            WHERE p.payment_date >= ?
            GROUP BY ym, is_renewal
            """;
        Map<YearMonth, int[]> out = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            out.put(start.plusMonths(i), new int[2]);
        }
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, start.atDay(1).toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    YearMonth ym = YearMonth.parse(rs.getString("ym"));
                    int[] cell = out.get(ym);
                    if (cell != null) {
                        cell[rs.getInt("is_renewal")] += rs.getInt("rev");
                    }
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to split revenue by month", e);
        }
    }

    private int oneInt(String sql, String... params) {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setString(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Query failed: " + sql, e);
        }
    }

    private  PaymentRecord mapPay(ResultSet rs) throws SQLException {
        return new PaymentRecord(
                rs.getInt("member_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getInt("amount"),
                rs.getString("name"),
                LocalDateTime.parse(
                        rs.getString("payment_date"))
        );
    }

    /** For the payments list, which also carries séance sales (no member/plan). */
    private PaymentRecord mapPayOrSeance(ResultSet rs) throws SQLException {
        rs.getInt("seance_id");
        if (!rs.wasNull()) {
            return new PaymentRecord(
                    rs.getInt("amount"),
                    com.GymGate.bussines.models.Sexe.valueOf(rs.getString("seance_sexe")),
                    rs.getInt("seance_cardio") == 1,
                    LocalDateTime.parse(rs.getString("payment_date")));
        }
        return mapPay(rs);
    }



    public static synchronized PaymentDao getInstance() {
        if (instance == null) {
            instance = new PaymentDao();
        }
        return instance;
    }

}