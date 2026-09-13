package com.GymGate.bussines.db.dao;
import com.GymGate.bussines.db.DatabaseManager;
import com.GymGate.bussines.db.entities.Plan;
import com.GymGate.bussines.models.Sexe;
import com.GymGate.bussines.services.SyncingService;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PlanDao {

    private final Connection connection;
    private static PlanDao instance;

    public PlanDao() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }


    public Plan insert(Plan plan) {

        String sql = """
                INSERT INTO plans
                (name, duration_months, days_per_month,sexe, price, cardio)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement stmt =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            int index = 1;

            stmt.setString(index++, plan.getName());
            stmt.setInt(index++, plan.getDurationMonths());

            if (plan.getDaysPerMonth() == null) {
                stmt.setNull(index++, Types.INTEGER);
            } else {
                stmt.setInt(index++, plan.getDaysPerMonth());
            }
            stmt.setString(index++,plan.getSexe()==null? Sexe.MALE.name():plan.getSexe().name());
            stmt.setDouble(index++, plan.getPrice());
            stmt.setBoolean(index, plan.isCardioIncluded());

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    plan.setId(keys.getInt(1));
                }
            }

            SyncingService.getInstance().pushDirectly("plans");
            return plan;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert plan", e);
        }
    }

    public void update(Plan plan) {

        String sql = """
                UPDATE plans
                SET
                    name = ?,
                    duration_months = ?,
                    days_per_month = ?,
                    price = ?,
                    cardio = ?,
                    synced = 0
                WHERE id = ?
                """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            int index = 1;

            stmt.setString(index++, plan.getName());
            stmt.setInt(index++, plan.getDurationMonths());

            if (plan.getDaysPerMonth() == null) {
                stmt.setNull(index++, Types.INTEGER);
            } else {
                stmt.setInt(index++, plan.getDaysPerMonth());
            }

            stmt.setDouble(index++, plan.getPrice());
            stmt.setBoolean(index++, plan.isCardioIncluded());

            stmt.setInt(index, plan.getId());

            stmt.executeUpdate();
            SyncingService.getInstance().pushDirectly("plans");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update plan", e);
        }
    }

    public void delete(int id) {

        try (PreparedStatement stmt =
                     connection.prepareStatement("DELETE FROM plans WHERE id = ?")) {

            stmt.setInt(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete plan", e);
        }
        DeletionDao.getInstance().record("plans", id);
        SyncingService.getInstance().deleteDirectly("plans");
    }

    public Plan findById(int id) {

        try (PreparedStatement stmt =
                     connection.prepareStatement("SELECT * FROM plans WHERE id = ?")) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {

                if (!rs.next()){
                 return null;
                }

                return mapRow(rs);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find plan", e);
        }
    }

    public List<Plan> findAll(Sexe sexe) {

        List<Plan> plans = new ArrayList<>();

        try (PreparedStatement stmt =
                     connection.prepareStatement("SELECT * FROM plans WHERE sexe = ? ORDER BY id")) {
            stmt.setString(1,sexe.name());
            try (ResultSet rs=stmt.executeQuery()){


            while (rs.next()) {
                plans.add(mapRow(rs));
            }

            return plans;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch plans", e);
        }
    }

    public Plan mapRow(ResultSet rs) throws SQLException {

        Plan plan = new Plan();

        plan.setId(rs.getInt("id"));
        plan.setName(rs.getString("name"));
        plan.setDurationMonths(rs.getInt("duration_months"));

        int days = rs.getInt("days_per_month");

        plan.setDaysPerMonth(rs.wasNull()?null:days);

        plan.setPrice(rs.getInt("price"));
        plan.setCardioIncluded(rs.getBoolean("cardio"));
        plan.setSexe(Sexe.valueOf(rs.getString("sexe")));

        return plan;
    }

    public static synchronized PlanDao getInstance() {
        if (instance == null) {
            instance = new PlanDao();
        }
        return instance;
    }

}