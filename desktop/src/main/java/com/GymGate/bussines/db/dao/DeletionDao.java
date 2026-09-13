package com.GymGate.bussines.db.dao;

import com.GymGate.bussines.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outbox of rows deleted locally, so {@code SyncingService} can replay those
 * deletes against Supabase. One entry per (table, primary-key id).
 */
public class DeletionDao {

    private final Connection connection;
    private static DeletionDao instance;

    private DeletionDao() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    /** Records that {@code rowId} was deleted from {@code table}; a no-op if already recorded. */
    public void record(String table, int rowId) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR IGNORE INTO pending_deletions(table_name, row_id) VALUES(?, ?)")) {
            stmt.setString(1, table);
            stmt.setInt(2, rowId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to record deletion of " + table + "#" + rowId, e);
        }
    }

    /** All outstanding deletions grouped by table, in the order they were recorded. */
    public Map<String, List<Integer>> pending() throws SQLException {
        Map<String, List<Integer>> byTable = new LinkedHashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT table_name, row_id FROM pending_deletions ORDER BY id")) {
            while (rs.next()) {
                byTable.computeIfAbsent(rs.getString("table_name"), k -> new ArrayList<>())
                        .add(rs.getInt("row_id"));
            }
        }
        return byTable;
    }

    /** Drops the tombstones for {@code rowIds} of {@code table} once the remote delete has succeeded. */
    public void clear(String table, List<Integer> rowIds) throws SQLException {
        if (rowIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(rowIds.size(), "?"));
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM pending_deletions WHERE table_name = ? AND row_id IN (" + placeholders + ")")) {
            stmt.setString(1, table);
            for (int i = 0; i < rowIds.size(); i++) {
                stmt.setInt(i + 2, rowIds.get(i));
            }
            stmt.executeUpdate();
        }
    }

    public static synchronized DeletionDao getInstance() {
        if (instance == null) {
            instance = new DeletionDao();
        }
        return instance;
    }
}
