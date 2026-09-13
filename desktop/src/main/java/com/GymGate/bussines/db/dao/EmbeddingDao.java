package com.GymGate.bussines.db.dao;

import com.GymGate.Ai.recognition.Embeddings;
import com.GymGate.Ai.recognition.RecognitionConfig;
import com.GymGate.bussines.db.DatabaseManager;
import com.GymGate.bussines.db.entities.MemberEmbedding;
import com.GymGate.bussines.util.EmbeddingCodec;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EmbeddingDao {

    private final Connection connection;

    private static EmbeddingDao instance;

    public EmbeddingDao() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    public void insert(MemberEmbedding memberEmbedding) {
        String sql = "INSERT INTO face_embeddings (member_id, embedding, model_name) VALUES (?, ?, ?)";
        String model = RecognitionConfig.active().modelFile();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            for (float[] embedding : memberEmbedding.getEmbeddings()) {
                stmt.setInt(1, memberEmbedding.getId());
                stmt.setBytes(2, EmbeddingCodec.toBytes(embedding));
                stmt.setString(3, model);
                stmt.addBatch();
            }

            stmt.executeBatch();

            Embeddings.knownEmbeddings
                    .computeIfAbsent(memberEmbedding.getId(), k -> new CopyOnWriteArrayList<>())
                    .addAll(memberEmbedding.getEmbeddings());

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to store embeddings for member " + memberEmbedding.getId(), e);
        }
    }

    /**
     * Removes a member's embeddings for the <em>currently active</em> model
     * only — re-capturing a face while running one recognizer must not wipe
     * the enrollment made under the other one.
     */
    public void deleteAllForMember(int memberId) {
        String model = RecognitionConfig.active().modelFile();

        List<Integer> deletedIds = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM face_embeddings WHERE member_id = ? AND model_name = ?")) {
            select.setInt(1, memberId);
            select.setString(2, model);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    deletedIds.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read embeddings for member " + memberId, e);
        }

        String sql = "DELETE FROM face_embeddings WHERE member_id = ? AND model_name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, memberId);
            stmt.setString(2, model);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete embeddings for member " + memberId, e);
        }

        DeletionDao deletionDao = DeletionDao.getInstance();
        for (int id : deletedIds) {
            deletionDao.record("face_embeddings", id);
        }
    }

    public static synchronized EmbeddingDao getInstance() {
        if (instance == null) {
            instance = new EmbeddingDao();
        }
        return instance;
    }

}