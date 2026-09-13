package com.GymGate.bussines.db;



import com.GymGate.Ai.recognition.Embeddings;
import com.GymGate.Ai.recognition.RecognitionConfig;
import com.GymGate.bussines.db.entities.MemberEmbedding;
import com.GymGate.bussines.util.AppPaths;
import com.GymGate.bussines.util.EmbeddingCodec;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.net.http.HttpResponse;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


public class DatabaseManager {

    private static final String DB_FILE_NAME = "gymgate.db";
    private static DatabaseManager instance;

    private final Connection connection;

    private DatabaseManager() {
        try {
            File dbFile = resolveDbFile();

            boolean alreadyExisted = dbFile.exists();


            Class.forName("org.sqlite.JDBC");
            // SQLite creates the file automatically on connect if it doesn't exist yet —
            // no manual file-creation step needed beyond making sure the parent folder exists.
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            configureConnection();
            initSchema();
            loadMemberEmbeddings();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }


    public synchronized Connection getConnection() {
        return connection;
    }


    public synchronized void withConnection(SqlWork work) throws SQLException {
        work.run(connection);
    }

    @FunctionalInterface
    public interface SqlWork {
        void run(Connection connection) throws SQLException;
    }


    private void configureConnection() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA busy_timeout=5000;");
            stmt.execute("PRAGMA foreign_keys=ON;");
        }
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                duration_months INTEGER NOT NULL,
                days_per_month INTEGER,
                sexe TEXT NOT NULL DEFAULT 'MALE',
                price REAL NOT NULL,
                cardio INTEGER NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0
            )
        """);

            // Single-session ("séance") drop-in prices, one row per
            // sexe × cardio-included combination. Seeded once with the four
            // combinations at price 0 for staff to fill in.
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS seance (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                price REAL NOT NULL DEFAULT 0,
                sexe TEXT NOT NULL DEFAULT 'MALE',
                cardio INTEGER NOT NULL DEFAULT 0,
                synced INTEGER NOT NULL DEFAULT 0
            )
        """);
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM seance")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("""
                        INSERT INTO seance (price, sexe, cardio) VALUES
                            (0, 'MALE',   1),
                            (0, 'MALE',   0),
                            (0, 'FEMALE', 1),
                            (0, 'FEMALE', 0)
                    """);
                }
            }

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS members (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                first_name TEXT NOT NULL,
                last_name TEXT NOT NULL,
                phone_number TEXT,
                sexe TEXT NOT NULL DEFAULT 'MALE',
                plan_id INTEGER,
                plan_name Text,
                start_date TEXT,
                end_date TEXT,
                remaining_days INTEGER,
                last_visit TEXT,
                created_at TEXT DEFAULT (datetime('now')),
                synced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (plan_id) REFERENCES plans(id)
            )
        """);

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS attendance (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                member_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
            )
        """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS face_embeddings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        member_id INTEGER NOT NULL,
                        embedding BLOB NOT NULL,
                        model_name TEXT,
                        created_at TEXT DEFAULT (datetime('now')),
                        synced INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS payments (
                         id INTEGER PRIMARY KEY AUTOINCREMENT,
                         member_id INTEGER,
                         plan_id INTEGER,
                         seance_id INTEGER,
                         amount INTEGER NOT NULL,
                         payment_date TEXT NOT NULL,
                         synced INTEGER NOT NULL DEFAULT 0,
                         FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
                         FOREIGN KEY (plan_id) REFERENCES plans(id),
                         FOREIGN KEY (seance_id) REFERENCES seance(id),
                         -- a payment is for exactly one of: a plan OR a single session
                         CHECK ((plan_id IS NOT NULL) + (seance_id IS NOT NULL) = 1)
                    )
                   """);
            // One JPEG per member; the id column IS members.id. Synced to
            // Supabase like every other table. The Supabase copy needs the same
            // FK ON DELETE CASCADE so deleting a member there drops the photo.
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS member_photos (
                        id INTEGER PRIMARY KEY,
                        image BLOB NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (id) REFERENCES members(id) ON DELETE CASCADE
                    )
                    """);

            // Outbox of rows deleted locally, replayed against Supabase by SyncingService.
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS pending_deletions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        table_name TEXT NOT NULL,
                        row_id INTEGER NOT NULL,
                        UNIQUE(table_name, row_id)
                    )
                   """);

            // Local-only "already reminded today" log — NEVER synced to Supabase
            // (not in SyncingService.SYNCED_TABLES). One row per member reminded;
            // `date` is the local day it was recorded. A member is expired XOR
            // inactive, never both, so member_id alone identifies the reminder —
            // no type is stored. Rows not dated today are purged on start, and the
            // presence of a row keeps that member out of the hourly sweep
            // (see ReminderScheduler).
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        member_id INTEGER,
                        date TEXT NOT NULL DEFAULT (date('now','localtime')),
                        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
                    )
                   """);

            migrateSchema(stmt);
            createIndexes(stmt);

        }catch (SQLException e){
            e.printStackTrace();
        }
    }

    /**
     * In-place column additions for existing databases. SQLite has no
     * "ALTER TABLE ... ADD COLUMN IF NOT EXISTS", so each is guarded by
     * {@link #columnExists}. Runs on every start; a no-op once applied.
     */
    private void migrateSchema(Statement stmt) throws SQLException {
        // Which recognizer produced each face vector. Both models' embeddings
        // can now live side by side in this table; startup loads only the
        // active model's (see loadMemberEmbeddings + RecognitionConfig).
        // Rows that predate this column were all made with w600k_r50.
        if (!columnExists(stmt, "face_embeddings", "model_name")) {
            stmt.execute("ALTER TABLE face_embeddings ADD COLUMN model_name TEXT");
            stmt.execute("UPDATE face_embeddings SET model_name = 'arcface_w600k_r50.onnx'"
                    + " WHERE model_name IS NULL");
        }

        // `last_visit` — date of the member's most recent check-in; powers the
        // inactivity reminder. Backfilled from `attendance` for existing members,
        // then every member is marked unsynced so the new field reaches Supabase.
        // Runs before the phone rebuild so that rebuild carries the column across.
        if (!columnExists(stmt, "members", "last_visit")) {
            stmt.execute("ALTER TABLE members ADD COLUMN last_visit TEXT");
            stmt.execute("""
                    UPDATE members SET last_visit = (
                        SELECT date(MAX(a.date)) FROM attendance a WHERE a.member_id = members.id
                    )
                    """);
            stmt.execute("UPDATE members SET synced = 0");
        }

        // phone_number was originally NOT NULL; it is optional at registration
        // now. SQLite can't drop a column constraint in place, so rebuild the
        // table once (standard 12-step ALTER — https://sqlite.org/lang_altertable.html).
        if (columnNotNull(stmt, "members", "phone_number")) {
            relaxMembersPhoneNullable(stmt);
        }

        // payments: `plan_id` and `member_id` are now nullable, and there is a
        // new `seance_id` (a payment is for a plan OR a single session, never
        // both). SQLite can't do these in place, so rebuild once.
        if (!columnExists(stmt, "payments", "seance_id")
                || columnNotNull(stmt, "payments", "member_id")) {
            rebuildPaymentsTable(stmt);
        }

        // `seance` gained a `synced` flag so its prices can push to Supabase.
        if (!columnExists(stmt, "seance", "synced")) {
            stmt.execute("ALTER TABLE seance ADD COLUMN synced INTEGER NOT NULL DEFAULT 0");
        }
    }

    /**
     * Rebuilds {@code payments} with nullable {@code member_id} and
     * {@code plan_id}, a {@code seance_id} (FK to {@code seance}), and a CHECK
     * that exactly one of {@code plan_id}/{@code seance_id} is set. Old rows are
     * all plan payments, so {@code seance_id} comes across NULL. Nothing
     * references {@code payments}; {@link #createIndexes} puts its indexes back.
     */
    private void rebuildPaymentsTable(Statement stmt) throws SQLException {
        System.out.println("Migrating payments: member_id/plan_id nullable, adding seance_id…");
        boolean hadSeanceId = columnExists(stmt, "payments", "seance_id");
        stmt.execute("PRAGMA foreign_keys=OFF");
        boolean priorAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            stmt.execute("""
                CREATE TABLE payments_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    member_id INTEGER,
                    plan_id INTEGER,
                    seance_id INTEGER,
                    amount INTEGER NOT NULL,
                    payment_date TEXT NOT NULL,
                    synced INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
                    FOREIGN KEY (plan_id) REFERENCES plans(id),
                    FOREIGN KEY (seance_id) REFERENCES seance(id),
                    CHECK ((plan_id IS NOT NULL) + (seance_id IS NOT NULL) = 1)
                )
            """);
            stmt.execute("INSERT INTO payments_new "
                    + "(id, member_id, plan_id, seance_id, amount, payment_date, synced) "
                    + "SELECT id, member_id, plan_id, "
                    + (hadSeanceId ? "seance_id" : "NULL")
                    + ", amount, payment_date, synced FROM payments");
            stmt.execute("DROP TABLE payments");
            stmt.execute("ALTER TABLE payments_new RENAME TO payments");
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check")) {
                if (rs.next()) {
                    throw new SQLException("foreign_key_check failed after payments rebuild");
                }
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(priorAutoCommit);
            stmt.execute("PRAGMA foreign_keys=ON");
        }
    }

    /**
     * Rebuilds {@code members} so {@code phone_number} is nullable, preserving
     * every row and id. The child-table foreign keys resolve by name, so they
     * re-point to the new table after the rename; {@link #createIndexes} (run
     * straight after) puts {@code idx_members_unsynced} back.
     */
    private void relaxMembersPhoneNullable(Statement stmt) throws SQLException {
        System.out.println("Migrating members: making phone_number optional…");
        stmt.execute("PRAGMA foreign_keys=OFF");
        boolean priorAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            stmt.execute("""
                CREATE TABLE members_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    first_name TEXT NOT NULL,
                    last_name TEXT NOT NULL,
                    phone_number TEXT,
                    sexe TEXT NOT NULL DEFAULT 'MALE',
                    plan_id INTEGER,
                    plan_name Text,
                    start_date TEXT,
                    end_date TEXT,
                    remaining_days INTEGER,
                    last_visit TEXT,
                    created_at TEXT DEFAULT (datetime('now')),
                    synced INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (plan_id) REFERENCES plans(id)
                )
            """);
            stmt.execute("""
                INSERT INTO members_new
                    (id, first_name, last_name, phone_number, sexe, plan_id, plan_name,
                     start_date, end_date, remaining_days, last_visit, created_at, synced)
                SELECT
                     id, first_name, last_name, phone_number, sexe, plan_id, plan_name,
                     start_date, end_date, remaining_days, last_visit, created_at, synced
                FROM members
            """);
            stmt.execute("DROP TABLE members");
            stmt.execute("ALTER TABLE members_new RENAME TO members");
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check")) {
                if (rs.next()) {
                    throw new SQLException("foreign_key_check failed after members rebuild");
                }
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(priorAutoCommit);
            stmt.execute("PRAGMA foreign_keys=ON");
        }
    }

    /**
     * Secondary indexes for the queries that run often or grow with the gym's
     * history. Without them SQLite full-scans the table every time:
     *
     * <ul>
     *   <li>{@code attendance(member_id, date)} — {@code existToday()} runs on
     *       every recognised check-in; the composite lets it seek straight to
     *       that member's rows and read the date from the index. Also covers the
     *       {@code ON DELETE CASCADE} scan when a member is removed.</li>
     *   <li>{@code payments(member_id)} / {@code face_embeddings(member_id)} —
     *       join and cascade lookups that otherwise scan a growing table.</li>
     *   <li>Partial {@code … WHERE synced = 0} indexes — SyncingService probes
     *       every table for unsynced rows once a minute, all day. With almost
     *       everything synced these become near-empty indexes, so an idle tick
     *       costs an index probe instead of six full table scans.</li>
     * </ul>
     *
     * All {@code IF NOT EXISTS}, so this is a one-time cost and a no-op on every
     * later start; existing databases pick the indexes up on next launch.
     */
    private void createIndexes(Statement stmt) throws SQLException {
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_attendance_member_date ON attendance(member_id, date)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_payments_member ON payments(member_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_embeddings_member ON face_embeddings(member_id)");

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_plans_unsynced ON plans(id) WHERE synced = 0");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_members_unsynced ON members(id) WHERE synced = 0");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_payments_unsynced ON payments(id) WHERE synced = 0");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_attendance_unsynced ON attendance(id) WHERE synced = 0");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_embeddings_unsynced ON face_embeddings(id) WHERE synced = 0");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_photos_unsynced ON member_photos(id) WHERE synced = 0");

        // The hourly sweep's NOT EXISTS(... reminders ...) check probes by member.
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_reminders_member ON reminders(member_id)");
    }

    /** True if the given table already has the given column (SQLite has no
     *  ALTER TABLE ... ADD COLUMN IF NOT EXISTS, so migrations check first). */
    private boolean columnExists(Statement stmt, String table, String column) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if the given column exists and carries a NOT NULL constraint. */
    private boolean columnNotNull(Statement stmt, String table, String column) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) {
                    return rs.getInt("notnull") == 1;
                }
            }
        }
        return false;
    }

    private void loadMemberEmbeddings() {

        // Read every frame by the recognition worker thread while the FX thread
        // adds/removes members — the map and its per-member lists must both be
        // safe for concurrent iteration.
        Map<Integer, List<float[]>> results = new ConcurrentHashMap<>();

        // Only the embeddings produced by the recognizer that is active this
        // run — a probe vector from one model is not comparable to a gallery
        // vector from another.
        String activeModel = RecognitionConfig.active().modelFile();
        String sql = "SELECT member_id, embedding FROM face_embeddings WHERE model_name = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, activeModel);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int memberId = rs.getInt("member_id");
                    float[] embedding = EmbeddingCodec.fromBytes(rs.getBytes("embedding"));
                    results.computeIfAbsent(memberId, k -> new CopyOnWriteArrayList<>())
                            .add(embedding);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load embeddings", e);
        }

        System.out.println("Loaded embeddings for " + results.size()
                + " member(s) enrolled with " + activeModel);

        Embeddings.knownEmbeddings = results;
    }

    /**
     * Re-reads {@code face_embeddings} into {@link Embeddings#knownEmbeddings}.
     * The constructor already calls {@link #loadMemberEmbeddings()} once, but on
     * a brand-new install that happens BEFORE {@code SyncingService.restoreFromCloud()}
     * has a chance to seed that table — so on a fresh install the initial load
     * always finds it empty. Call this once restore finishes so recognition
     * actually has the restored gallery in memory instead of waiting for a
     * second app launch to pick it up.
     */
    public void reloadMemberEmbeddings() {
        loadMemberEmbeddings();
    }


    /**
     * Resolves gymgate.db via the shared AppPaths helper, so everything the
     * app persists lives in one consistent place.
     */
    private File resolveDbFile() {
        return AppPaths.resolve(DB_FILE_NAME);
    }

    /**
     * True if the on-disk database file already exists. Call this BEFORE
     * {@link #getInstance()} — which creates the file (and schema) when it is
     * missing — to tell a first run apart from a normal start.
     */
    public static boolean databaseFileExists() {
        return AppPaths.resolve(DB_FILE_NAME).exists();
    }


    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}