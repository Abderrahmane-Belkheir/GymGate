package com.GymGate.Ai.recognition;

import com.GymGate.bussines.util.AppPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Append-only CSV of every recognition verdict, written to
 * {@code <data>/recognition-log.csv}.
 *
 * <p>The recognition thresholds ({@link RecognitionConfig}) are currently
 * tuned by simulation, not by data from a real gate. This log is what makes a
 * real re-fit possible: after a couple of weeks of live use, the {@code score}
 * column for {@code MATCHED} rows versus {@code NOT_FOUND} rows is a genuine
 * genuine/impostor distribution for this exact camera, lighting and member
 * set. Cross-referenced with the attendance table (a {@code NOT_FOUND} row
 * followed seconds later by a manual check-in of member X is a false reject),
 * it also yields labelled errors.
 *
 * <p>Never throws — a logging failure must not disturb the gate. Writes are
 * small (one line per person at the gate) and flushed immediately so a hard
 * power-off loses at most the current line.
 */
public final class RecognitionLog {

    private static final Path FILE = AppPaths.resolve("recognition-log.csv").toPath();
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final String HEADER =
            "timestamp,verdict,member_id,near_member_id,score,runner_up,attempts,evidence_ms,"
            + "min_score,max_score,mean_score,near_miss,face_width_px,sharpness,min_sharpness\n";

    private static volatile boolean enabled = true;
    private static volatile boolean headerChecked = false;

    private RecognitionLog() {}

    /** Turn logging off (e.g. from a setting) without touching call sites. */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    /**
     * Records one completed verdict.
     *
     * @param verdict      MATCHED / AMBIGUOUS / NOT_FOUND
     * @param memberId     matched member, or 0 when no identity was claimed
     * @param nearMemberId member this face came closest to, whatever the verdict.
     *                     On a NOT_FOUND this is the whole point of the row: it
     *                     turns "something scored 0.41" into "member 23 scored
     *                     0.41", which is what makes a repeatedly-failing member
     *                     visible instead of invisible.
     * @param score        final reported similarity
     * @param runnerUp     best similarity to any other member on the deciding attempt (0 if none)
     * @param attempts     the recognition attempts that fed this verdict
     * @param evidenceMs   wall-clock ms from the first attempt to the verdict
     * @param nearMiss     whether the near-miss (extended patience) path was active
     * @param faceWidthPx  detected face width at the deciding frame — separates
     *                     "this member embeds badly" from "this member stands too far back"
     */
    public static void verdict(String verdict, int memberId, int nearMemberId, float score,
                               float runnerUp, List<RecognitionResult> attempts, long evidenceMs,
                               boolean nearMiss, float faceWidthPx) {
        if (!enabled) {
            return;
        }
        try {
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE, sum = 0f;
            double bestScore = -1, sharpAtBest = 0, minSharp = Double.MAX_VALUE;
            int n = 0;
            for (RecognitionResult r : attempts) {
                float s = Math.max(r.getScore(), 0f);
                min = Math.min(min, s);
                max = Math.max(max, s);
                sum += s;
                n++;

                // Pair the focus score with the attempt it actually belongs to,
                // so a scatter of sharpness against score is meaningful.
                if (r.getScore() > bestScore) {
                    bestScore = r.getScore();
                    sharpAtBest = r.getSharpness();
                }
                minSharp = Math.min(minSharp, r.getSharpness());
            }
            if (n == 0) {
                min = max = 0f;
                minSharp = 0;
            }
            String line = String.format(Locale.ROOT,
                    "%s,%s,%d,%d,%.4f,%.4f,%d,%d,%.4f,%.4f,%.4f,%s,%.0f,%.1f,%.1f%n",
                    LocalDateTime.now().format(TS), verdict, memberId, nearMemberId, score, runnerUp,
                    n, evidenceMs, min, max, n == 0 ? 0f : sum / n, nearMiss,
                    faceWidthPx, sharpAtBest, minSharp);
            write(line);
        } catch (Throwable t) {
            // Logging must never break the gate.
            enabled = false;
            System.err.println("RecognitionLog disabled after write error: " + t.getMessage());
        }
    }

    private static synchronized void write(String line) throws IOException {
        if (!headerChecked) {
            headerChecked = true;
            prepareFile();
        }
        Files.writeString(FILE, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Makes sure the file we are about to append to actually has the columns we
     * are about to write.
     *
     * <p>When the column set changes, the existing file is renamed aside rather
     * than deleted or appended to: appending new-format rows under an old header
     * silently shifts every column, which would corrupt a threshold re-fit in a
     * way that is very hard to notice afterwards. The old rows stay readable on
     * their own terms as {@code recognition-log-<timestamp>.csv}, which matters
     * because they are the only baseline of how this camera behaved before.
     */
    private static void prepareFile() throws IOException {
        if (Files.exists(FILE) && Files.size(FILE) > 0) {
            String existingHeader;
            try (var lines = Files.lines(FILE, StandardCharsets.UTF_8)) {
                existingHeader = lines.findFirst().orElse("");
            }
            if (existingHeader.equals(HEADER.strip())) {
                return; // same columns — keep appending
            }

            Path archived = FILE.resolveSibling("recognition-log-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT))
                    + ".csv");
            Files.move(FILE, archived);
            System.out.println("RecognitionLog: column set changed — previous log archived as "
                    + archived.getFileName());
        }

        Files.writeString(FILE, HEADER, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }
}
