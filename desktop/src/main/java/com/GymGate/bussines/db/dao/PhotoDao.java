package com.GymGate.bussines.db.dao;

import com.GymGate.bussines.db.DatabaseManager;
import com.GymGate.bussines.services.SyncingService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Member photos live in the {@code member_photos} table, one row per member
 * (its {@code id} column IS the member id), synced to Supabase like every other
 * table. The stored bytes are a JPEG produced by {@link com.GymGate.bussines.services.PhotosService}.
 */
public class PhotoDao {

    private final Connection connection;

    private static PhotoDao instance;

    public PhotoDao() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    /** Inserts or replaces the member's photo and marks the row unsynced. */
    public void save(int memberId, byte[] jpeg) {
        String sql = "INSERT OR REPLACE INTO member_photos (id, image, synced) VALUES (?, ?, 0)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, memberId);
            stmt.setBytes(2, jpeg);
            stmt.executeUpdate();
            SyncingService.getInstance().pushDirectly("member_photos");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save photo for member " + memberId, e);
        }
    }

    public Optional<byte[]> find(int memberId) {
        String sql = "SELECT image FROM member_photos WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, memberId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getBytes("image")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load photo for member " + memberId, e);
        }
    }

    public void delete(int memberId) {
        String sql = "DELETE FROM member_photos WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, memberId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete photo for member " + memberId, e);
        }
    }

    public static synchronized PhotoDao getInstance() {
        if (instance == null) {
            instance = new PhotoDao();
        }
        return instance;
    }
}
