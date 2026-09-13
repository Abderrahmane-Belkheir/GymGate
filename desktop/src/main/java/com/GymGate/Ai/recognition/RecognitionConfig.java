package com.GymGate.Ai.recognition;

import  com.GymGate.bussines.util.AppPaths;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Single switch for which face-recognition model runs and the matching
 * thresholds that go with it.
 *
 * <p>Pick the model by file name in {@code Recognition.properties} (created
 * automatically in the data directory on first run):
 *
 * <pre>
 *   recognizer.model = w600k_mbf.onnx   # or: arcface_w600k_r50.onnx
 * </pre>
 *
 * <p>Each known model ships with its own tuned {@link Profile} (threshold,
 * margin, near-miss band, confident-non-match band, plus the consistency /
 * majority-vote / enrollment policy) — selecting the model selects the
 * profile, no other edits. Any field can still be overridden per run with an
 * optional {@code recognizer.*} key in the same file. An unknown model name
 * falls back to conservative defaults and a warning.
 *
 * <p>The {@code .onnx} file itself must be present as a
 * {@code /models/<name>} classpath resource (it is extracted to the data
 * directory on first use, same as the detector).
 *
 * <p>Read once at startup; change the file and restart to switch.
 */
public final class RecognitionConfig {

    /** Tuning that must travel with a given recognizer model. */
    public record Profile(
            String modelFile,
            float matchThreshold,          // FaceRecognitionService: absolute cosine bar
            float matchMargin,             // FaceRecognitionService: lead over the runner-up
            float nearMissBand,            // FaceProcessor: "probably an enrolled member in a bad frame"
            float confidentNonMatchMargin, // FaceProcessor: fast-path "definitely a stranger"

            // ---- consistency gate (FaceRecognitionService.findBestMatch) ----
            // A single stored angle matching the probe is not enough to commit:
            // a stranger or a sibling can flukingly line up with one of a
            // member's enrollment shots. When the winning member has at least
            // this many embeddings on file, the match also needs either this
            // many of them within `supportSlack` of the threshold, OR the
            // averaged template (centroid) within `centroidSlack`. Set to 1 to
            // disable the gate entirely.
            int minSupportingEmbeddings,
            float supportSlack,            // how far below threshold a supporting angle may sit
            float centroidSlack,           // how far below threshold the averaged template may sit

            // ---- confidence-adaptive majority vote (FaceProcessor.majorityMatch) ----
            // A "strong" attempt scores at least threshold + voteStrongMargin.
            // Commit to MATCHED once a member has voteStrongAgreeing attempts
            // with at least one strong, OR voteMarginalAgreeing attempts of any
            // strength. Set voteMarginalAgreeing == voteStrongAgreeing to make
            // the vote strength-agnostic (the old behaviour).
            int voteStrongAgreeing,
            int voteMarginalAgreeing,
            float voteStrongMargin,

            // Contested vote: if a SECOND member also collected this many
            // agreeing frames in the window, the frames are splitting between
            // two look-alikes (siblings, a parent and child) — the verdict is
            // held at AMBIGUOUS ("please re-present") instead of guessing which
            // one. Only bites when both are enrolled. Set high (e.g. 99) to
            // disable.
            int voteContestedThreshold,

            // ---- enrollment ----
            // "This person is already registered" during a NEW registration is
            // a hard block, so it uses a deliberately stricter bar than
            // recognition — a borderline resemblance must not stop a genuinely
            // new member from enrolling.
            float enrollmentDedupThreshold
    ) {}

    private static final String PROPERTIES_FILE = "Recognition.properties";
    private static final String KEY_MODEL = "recognizer.model";

    /** Built-in, pre-tuned profiles. Add a row here when a new model is introduced. */
    private static final Map<String, Profile> BUILT_IN = new HashMap<>();
    static {
        // ArcFace ResNet-50 (w600k_r50): sharp genuine/impostor separation, run
        // at a deliberately strict bar for check-in. The separation is clean
        // enough that the strength-agnostic 2-vote path is kept (marginal == 2).
        BUILT_IN.put("arcface_w600k_r50.onnx",
                new Profile("arcface_w600k_r50.onnx",
                        0.65f, 0.05f, 0.12f, 0.35f,
                        2, 0.18f, 0.10f,       // consistency: 2 of N angles above 0.47, or centroid above 0.55
                        2, 2, 0.08f, 2,        // vote: strength-agnostic 2, contested at 2
                        0.78f));               // enrollment dedup

        // ArcFace MobileFaceNet (w600k_mbf): genuine matches score ~0.10-0.15
        // lower than r50 for the same face quality, so the bar sits lower.
        //
        // A BALANCED starting point for a ~200-member gallery with cooperative
        // check-in and near-frontal enrollment (centre + gentle ~15-20deg
        // left/right). Still an estimate — re-fit from recognition-log.csv after
        // ~2 weeks of live use. How the pieces share the load:
        //   - matchThreshold 0.45: below the expected real-world genuine mean
        //     (~0.50) so a cooperative member clears it, but above the inflated
        //     max-over-600-comparisons a true stranger reaches (~0.34, tail to
        //     ~0.42). 0.42 sat inside that stranger tail; 0.48 would clip too
        //     many genuine bad-light frames.
        //   - nearMissBand 0.13 scales WITH the threshold: anyone >= 0.32 keeps
        //     the full 3.5s patience, so the higher bar costs latency on a poor
        //     frame, not a rejection.
        //   - matchMargin 0.12 + voteContestedThreshold 2: two defences against
        //     an enrolled look-alike pair — per-frame lead, and "both are
        //     racking up frames -> don't guess".
        //   - marginal vote = 3: a score sitting right on 0.45 needs three
        //     agreeing frames; a clean >= 0.49 match still commits on two.
        //   - consistency gate is near-inert with near-frontal enrollment (all
        //     three angles score alike) — kept as a cheap safety net for the odd
        //     single-angle anomaly and for members re-enrolled with varied shots.
        BUILT_IN.put("w600k_mbf.onnx",
                new Profile("w600k_mbf.onnx",
                        0.45f, 0.12f, 0.13f, 0.25f,
                        2, 0.18f, 0.10f,       // consistency: 2 of N angles above 0.27, or centroid above 0.35
                        2, 3, 0.04f, 2,        // vote: 2 if one is strong (>=0.49), else 3; contested at 2
                        0.58f));               // enrollment dedup
    }

    /** Used when {@code recognizer.model} names a model with no built-in profile. */
    private static Profile fallback(String modelFile) {
        System.err.println("RecognitionConfig: no built-in profile for '" + modelFile
                + "' — using conservative defaults. Add a profile in RecognitionConfig"
                + " or set recognizer.* overrides in " + PROPERTIES_FILE + ".");
        return new Profile(modelFile,
                0.55f, 0.05f, 0.10f, 0.30f,
                1, 0.15f, 0.12f,   // consistency gate OFF for an unknown model
                2, 2, 0.08f, 2,    // vote: strength-agnostic, contested at 2
                0.70f);
    }

    private static final String DEFAULT_MODEL = "w600k_mbf.onnx";

    private static volatile Profile active;

    private RecognitionConfig() {}

    /** The resolved profile for this run. Loaded once, then cached. */
    public static Profile active() {
        Profile p = active;
        if (p == null) {
            synchronized (RecognitionConfig.class) {
                p = active;
                if (p == null) {
                    p = load();
                    active = p;
                    System.out.println("Recognition model: " + p.modelFile()
                            + " (threshold=" + p.matchThreshold()
                            + ", margin=" + p.matchMargin()
                            + ", minSupport=" + p.minSupportingEmbeddings()
                            + ", vote=" + p.voteStrongAgreeing() + "/" + p.voteMarginalAgreeing() + ")");
                }
            }
        }
        return p;
    }

    private static Profile load() {
        Properties props = new Properties();
        var file = AppPaths.resolve(PROPERTIES_FILE);

        if (!file.exists()) {
            writeDefault(file.toPath());
        }

        try {
            props.load(Files.newInputStream(file.toPath()));
        } catch (IOException e) {
            System.err.println("RecognitionConfig: could not read " + file
                    + " (" + e.getMessage() + ") — using defaults.");
        }

        String model = props.getProperty(KEY_MODEL, DEFAULT_MODEL).trim();
        if (model.isEmpty()) {
            model = DEFAULT_MODEL;
        }

        Profile base = BUILT_IN.getOrDefault(model, null);
        if (base == null) {
            base = fallback(model);
        }

        // Optional per-run overrides — anything not set keeps the profile value.
        return new Profile(
                model,
                floatProp(props, "recognizer.matchThreshold", base.matchThreshold()),
                floatProp(props, "recognizer.matchMargin", base.matchMargin()),
                floatProp(props, "recognizer.nearMissBand", base.nearMissBand()),
                floatProp(props, "recognizer.confidentNonMatchMargin", base.confidentNonMatchMargin()),
                intProp(props, "recognizer.minSupportingEmbeddings", base.minSupportingEmbeddings()),
                floatProp(props, "recognizer.supportSlack", base.supportSlack()),
                floatProp(props, "recognizer.centroidSlack", base.centroidSlack()),
                intProp(props, "recognizer.vote.strongAgreeing", base.voteStrongAgreeing()),
                intProp(props, "recognizer.vote.marginalAgreeing", base.voteMarginalAgreeing()),
                floatProp(props, "recognizer.vote.strongMargin", base.voteStrongMargin()),
                intProp(props, "recognizer.vote.contestedThreshold", base.voteContestedThreshold()),
                floatProp(props, "recognizer.enrollmentDedupThreshold", base.enrollmentDedupThreshold())
        );
    }

    private static float floatProp(Properties props, String key, float fallback) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            System.err.println("RecognitionConfig: '" + key + "' is not a number ('" + raw
                    + "') — using " + fallback);
            return fallback;
        }
    }

    private static int intProp(Properties props, String key, int fallback) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            System.err.println("RecognitionConfig: '" + key + "' is not an integer ('" + raw
                    + "') — using " + fallback);
            return fallback;
        }
    }

    private static void writeDefault(java.nio.file.Path path) {
        String template = """
                # GymGate face-recognition model selection.
                #
                # Pick ONE model by its .onnx file name. The matching thresholds
                # for it are applied automatically (see RecognitionConfig).
                # Switching models means re-enrolling every member — the two
                # models produce different, non-comparable embeddings.
                #
                #   w600k_mbf.onnx          - MobileFaceNet. ~13 MB, ~10x faster,
                #                             the default. Accuracy fine for
                #                             cooperative check-in; tuned for a
                #                             gallery of a few hundred members.
                #   arcface_w600k_r50.onnx  - ResNet-50. More accurate, ~166 MB,
                #                             much slower on CPU.
                #
                recognizer.model = w600k_mbf.onnx

                # ---- optional overrides ---------------------------------------------
                # Leave every line below commented to use the selected model's tuned
                # profile. Uncomment only what you want to change, then restart.
                # The values shown are the w600k_mbf defaults.
                #
                # Absolute cosine bar a candidate must clear:
                # recognizer.matchThreshold = 0.45
                # Lead the best candidate needs over the runner-up member:
                # recognizer.matchMargin = 0.12
                # "Near miss" band below the threshold — keep waiting for a better frame.
                # Keep this roughly proportional to the threshold if you change it.
                # recognizer.nearMissBand = 0.13
                # Fast "definitely a stranger" margin below the threshold:
                # recognizer.confidentNonMatchMargin = 0.25
                #
                # ---- consistency gate ----
                # A match also needs this many of the member's stored angles near the
                # probe (or the averaged template — see centroidSlack). Near-inert
                # with near-frontal enrollment. Set to 1 to switch the gate off.
                # recognizer.minSupportingEmbeddings = 2
                # recognizer.supportSlack = 0.18
                # recognizer.centroidSlack = 0.10
                #
                # ---- confidence-adaptive majority vote ----
                # Commit after `strongAgreeing` frames agree if >=1 scored
                # threshold + strongMargin, otherwise after `marginalAgreeing` frames.
                # Set marginalAgreeing = strongAgreeing for the old strength-agnostic vote
                # (faster, slightly weaker on look-alikes).
                # recognizer.vote.strongAgreeing = 2
                # recognizer.vote.marginalAgreeing = 3
                # recognizer.vote.strongMargin = 0.04
                # If a SECOND member also reaches this many agreeing frames, hold the
                # verdict at AMBIGUOUS instead of guessing between two look-alikes.
                # Set to 99 to disable.
                # recognizer.vote.contestedThreshold = 2
                #
                # ---- enrollment ----
                # Stricter bar for the "already registered" block during a new
                # registration (a borderline resemblance must not block a new member).
                # recognizer.enrollmentDedupThreshold = 0.58
                """;
        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(template.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + path, e);
        }
    }
}
