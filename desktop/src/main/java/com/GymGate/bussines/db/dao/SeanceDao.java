package com.GymGate.bussines.db.dao;

import com.GymGate.bussines.db.DatabaseManager;
import com.GymGate.bussines.db.entities.Seance;
import com.GymGate.bussines.models.Sexe;
import com.GymGate.bussines.services.SyncingService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code seance} table — single-session ("séance") drop-in prices, one row
 * per {@link Sexe} × cardio combination. The four rows are seeded once by
 * {@code DatabaseManager}; only the price is ever changed.
 */
public class SeanceDao {

    private final Connection connection;
    private static SeanceDao instance;

    private SeanceDao() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    public static synchronized SeanceDao getInstance() {
        if (instance == null) {
            instance = new SeanceDao();
        }
        return instance;
    }

    /** All séance prices, id order (MALE+cardio, MALE, FEMALE+cardio, FEMALE). */
    public List<Seance> findAll() {
        List<Seance> out = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT id, price, sexe, cardio FROM seance ORDER BY id");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                out.add(new Seance(
                        rs.getInt("id"),
                        rs.getDouble("price"),
                        Sexe.valueOf(rs.getString("sexe")),
                        rs.getInt("cardio") == 1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load séance prices", e);
        }
        return out;
    }

    /** Sets the price of one séance row and flags it for push — mirrors every
     *  other DAO's write path (see e.g. PlanDao.update): mark synced=0 so the
     *  periodic cycle would pick it up even if the direct push below fails or
     *  is skipped (offline), then also try to send it immediately. */
    public void updatePrice(int id, double price) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE seance SET price = ?, synced = 0 WHERE id = ?")) {
            stmt.setDouble(1, price);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            SyncingService.getInstance().pushDirectly("seance");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update séance price for row " + id, e);
        }
    }
}
