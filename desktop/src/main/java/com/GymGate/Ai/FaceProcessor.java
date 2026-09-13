package com.GymGate.Ai;

import ai.onnxruntime.OrtException;
import com.GymGate.Ai.detection.FaceBox;
import com.GymGate.Ai.detection.FaceBoxUtils;
import com.GymGate.Ai.detection.FaceDetectionResult;
import com.GymGate.Ai.detection.FaceDetectionService;
import com.GymGate.Ai.recognition.FaceRecognitionService;
import com.GymGate.Ai.recognition.RecognitionLog;
import com.GymGate.Ai.recognition.RecognitionResult;
import com.GymGate.Ai.recognition.RecognitionStatus;
import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides WHO is in front of the camera and WHEN there is enough evidence to
 * announce a verdict about them.
 *
 * <p>The detection stage upstream is deliberately permissive about where and
 * how far away a person stands (see FaceDetectionService) — which means more
 * frames reach this class, including mediocre ones. This class is what keeps
 * that from turning into false accepts or premature rejections:
 *
 * <ul>
 *   <li>MATCHED requires several independent attempts to agree on the SAME
 *       member (majority vote over a sliding window).</li>
 *   <li>"Not found" is treated as a real, expensive claim: it can only be made
 *       after a minimum amount of continuous observation time AND a minimum
 *       number of attempts, and it is deferred further if the scores suggest a
 *       near miss (i.e. probably an enrolled member in a bad frame).</li>
 *   <li>Anything short of that stays SILENT rather than showing the user a
 *       negative result they'd have to retry out of.</li>
 * </ul>
 *
 * <h2>Why this is per-person and not per-frame</h2>
 *
 * All of that evidence is held <b>per tracked person</b>, not in one global
 * slot. That is not an optimisation, it fixes two failures that a single-face
 * pipeline cannot avoid at a real gate:
 *
 * <ol>
 *   <li><b>Queue hand-off.</b> A single "this face has been recognised" latch
 *       keyed on the last bounding box gets inherited by whoever steps into
 *       that spot next: the second person is silently never recognised at all —
 *       no match, no "not found", nothing in the log. The latch here belongs to
 *       a track and carries the matched {@code memberId}, and a resolved track
 *       is <em>re-verified</em> at {@link #RESOLVED_RECHECK_MS} so an inherited
 *       track re-arms itself instead of swallowing the next person.</li>
 *   <li><b>Two people at similar distance.</b> Picking one face per frame by
 *       area makes the winner alternate between two people standing side by
 *       side; each switch looks like "a different face replaced the one we were
 *       tracking" and wipes the evidence, so the vote window never fills and
 *       NEITHER person is ever recognised. Separate tracks accumulate in
 *       parallel; the pipeline resolves the nearest person first and then moves
 *       on to the next.</li>
 * </ol>
 *
 * <p>Cost is unchanged: at most ONE embedding is computed per frame no matter
 * how many people are in view. Tracking is pure box arithmetic.
 *
 * <h2>Threading</h2>
 *
 * The detector, the recognizer and the low-light enhancer all reuse internal
 * scratch buffers and are explicitly not thread-safe, yet enrollment
 * (FX thread) and the gate (face-processing worker) share this singleton. Every
 * public entry point that touches a model therefore holds {@link #inferenceLock}
 * for the whole detect → align → embed sequence.
 */
public class FaceProcessor {

    // ---- presence & tracking ----
    // How long a face must be continuously present before we're willing to
    // spend a recognition attempt on it. Filters out faces that merely swept
    // through the frame.
    private static final long REQUIRED_STABLE_MS = 80;

    // How long a track survives without being seen before we accept that the
    // person has actually left. Sized so a single missed SCRFD detection — or a
    // couple in a row — never counts as "gone". A track that has already been
    // identified is kept longer: dropping it early would re-recognise and
    // re-announce the same person who simply blinked out for a frame.
    private static final long UNRESOLVED_TRACK_TTL_MS = 400;
    private static final long RESOLVED_TRACK_TTL_MS = 700;

    // Frame-to-frame association: a detection is the continuation of an
    // existing track if it overlaps that track's last box by at least this
    // much. Deliberately loose (0.2), because with the wide distance/centering
    // gates a person legitimately moves a fair amount between frames while
    // walking up — too strict a value here would keep starting new tracks for
    // the SAME person and the pipeline would never reach a verdict at all.
    // See FaceBoxUtils#iou.
    private static final float MIN_CONTINUITY_IOU = 0.2f;

    // Safety valve: a busy lobby behind the person at the gate should not grow
    // this list without bound. Faces are already NMS'd and gated by the time
    // they get here, so this only ever bites on a genuine crowd; the
    // least-recently-seen track is evicted to make room.
    private static final int MAX_TRACKS = 8;

    // ---- majority-vote recognition ----
    // A single recognition attempt is one noisy sample. Instead of trusting
    // the first attempt outright, we keep a sliding window of the most recent
    // MAX_RECOGNITION_ATTEMPTS attempts (each already past the same
    // distance/pose/centering gates) and only commit to MATCHED once enough
    // of them land on the SAME member (see majorityMatch — the count is
    // confidence-adaptive). A real match tends to consistently resolve to the
    // same identity across attempts; noise/near-misses don't reliably repeat
    // against the same wrong identity.
    private static final int MAX_RECOGNITION_ATTEMPTS = 6;

    // Confidence-adaptive agreement (set from the active model's profile — see
    // RecognitionConfig). A "strong" attempt scores at least matchThreshold +
    // voteStrongMargin. Commit once a member has voteStrongAgreeing attempts
    // with at least one strong, OR voteMarginalAgreeing attempts of any
    // strength. With the two counts equal this is the old flat vote.
    private final int voteStrongAgreeing;
    private final int voteMarginalAgreeing;
    private final float voteStrongMargin;
    // If a second member also reaches this many agreeing frames, the window is
    // splitting between two look-alikes — hold at AMBIGUOUS rather than commit.
    private final int voteContestedThreshold;

    // ---- how long before we're allowed to say "not found" ----
    // A negative verdict requires a sustained look: both the attempt count AND
    // a real observation window must be satisfied. Without it, at ~6fps a hard
    // "member not found" could be announced a fraction of a second after the
    // very first usable frame — i.e. while the person was still walking up and
    // turning toward the camera. Positive matches are NOT slowed down by this:
    // they fire as soon as the vote agrees.
    private static final long MIN_EVIDENCE_MS = 1500;

    // If the best similarity seen so far is within nearMissBand of the match
    // threshold, this is much more likely an enrolled member in a poor frame
    // than a stranger, so we keep looking for a better frame up to
    // EXTENDED_EVIDENCE_MS before giving up on them.
    private static final long EXTENDED_EVIDENCE_MS = 3500;
    /** Set from the active model's profile (see RecognitionConfig). */
    private final float nearMissBand;

    // ---- confident-negative fast path ----
    // A genuine stranger scores nowhere near the match threshold against the
    // whole gallery; only an enrolled member in a poor frame sits just under
    // it. Once this many attempts have ALL landed well below the bar — and the
    // score is not climbing — there is no near-miss member to keep waiting
    // for, so the "not found" does not need the full MIN_EVIDENCE_MS window.
    // This is purely a latency win on non-members: it can only make a negative
    // verdict arrive sooner, never turn one into a match (majorityMatch is
    // untouched).
    private static final int FAST_NEGATIVE_MIN_ATTEMPTS = 3;
    private static final long FAST_NEGATIVE_MIN_MS = 700;
    // How far below the match threshold every attempt must score to count as
    // a confident non-match. Set from the active model's profile.
    private final float confidentNonMatchMargin;
    // Skip the fast path if the score rose by more than this from the first
    // attempt to the latest — the person may be settling into a better frame.
    private static final float CONFIDENT_NON_MATCH_CLIMB_EPS = 0.05f;

    // After a negative verdict has been delivered for a track, stay quiet about
    // that track for this long even if the same face is still in front of the
    // camera, then start a completely fresh evidence round. Without this, one
    // unrecognized person standing at the gate would re-trigger the same
    // notification every window.
    private static final long NEGATIVE_VERDICT_SILENCE_MS = 4000;

    // ---- resolved-track re-verification ----
    // An identified track is not trusted forever. Whenever there is no new
    // person to work on, a spare frame is spent re-checking that the person
    // still standing on a resolved track is who we said they were. This is the
    // backstop for a fast queue hand-off, where the next person's box can
    // legitimately overlap the previous person's enough to inherit the track.
    // Costs nothing when someone new is waiting: unresolved tracks always win
    // the frame.
    private static final long RESOLVED_RECHECK_MS = 1200;
    // Re-arm at once if a re-check MATCHES someone else. If it merely fails to
    // confirm (blur, a half-turn, a non-member who inherited the track), give
    // it this many consecutive tries before re-arming, so one bad frame doesn't
    // re-announce a member who is simply still standing there.
    private static final int VERIFY_MISMATCH_LIMIT = 2;

    // ---- low-confidence "might be this member?" hint ----
    // A plain rejection can still be worth a second look from staff instead of
    // turning the person away outright — but only when the closest candidate
    // came genuinely close, not merely within the much looser nearMissBand
    // (which exists to buy a slow face more time, not to accuse a stranger of
    // being someone). Deliberately tighter than nearMissBand for that reason.
    private static final float UI_HINT_BAND = 0.05f;

    /**
     * Everything known about one person currently in front of the camera.
     * Evidence is per-track, which is what keeps two people from overwriting
     * each other's recognition attempts.
     */
    private static final class Track {
        FaceBox box;
        /** Reset when a different person takes over this track — see rearm(). */
        long firstSeenMs;
        long lastSeenMs;
        /** Pose was acceptable in the most recent frame this track was seen in. */
        boolean recognizableNow;

        final List<RecognitionResult> attempts = new ArrayList<>();
        long firstAttemptMs = -1;
        /** Set after a negative verdict: say nothing about this track until then. */
        long silentUntilMs = 0;

        /** Latched on a MATCHED verdict — bound to the member, not to the box. */
        boolean resolved;
        int resolvedMemberId;
        long lastVerifyMs;
        int verifyMismatches;

        Track(FaceBox box, long now) {
            this.box = box;
            this.firstSeenMs = now;
            this.lastSeenMs = now;
        }

        float area() {
            return box.width() * box.height();
        }
    }

    private final List<Track> tracks = new ArrayList<>();

    /**
     * True if the most recent detection pass found at least one face candidate
     * anywhere in frame — not whether anyone was recognized, or even whether
     * the pose gate would accept them, just "something face-shaped is there".
     * Written every {@code process()} call (the face-processing worker thread),
     * read from the camera capture thread by {@link com.GymGate.Camera.CameraCapturingService}
     * to drive the secondary screen's live-feed/logo toggle — {@code volatile}
     * so that cross-thread read actually observes the latest write. Purely an
     * observer field: nothing in the tracking/voting logic above reads it.
     */
    private volatile boolean facePresent = false;

    /**
     * Guards every model call. The detector, recognizer and low-light enhancer
     * all reuse scratch buffers across calls, so two threads inside them at
     * once corrupts an embedding at best and crashes the native side at worst.
     * Enrollment runs on the FX thread and the gate on the worker thread, so
     * this is a real, reachable overlap — not a theoretical one.
     */
    private final Object inferenceLock = new Object();

    private static FaceProcessor instance;
    private final FaceRecognitionService recognitionService;
    private final FaceDetectionService detectionService;

    public FaceProcessor() throws OrtException {
        this.detectionService = new FaceDetectionService();
        this.recognitionService = new FaceRecognitionService();
        com.GymGate.Ai.recognition.RecognitionConfig.Profile profile =
                com.GymGate.Ai.recognition.RecognitionConfig.active();
        this.nearMissBand = profile.nearMissBand();
        this.confidentNonMatchMargin = profile.confidentNonMatchMargin();
        this.voteStrongAgreeing = profile.voteStrongAgreeing();
        this.voteMarginalAgreeing = profile.voteMarginalAgreeing();
        this.voteStrongMargin = profile.voteStrongMargin();
        this.voteContestedThreshold = profile.voteContestedThreshold();
    }

    /**
     * One camera frame in, at most one verdict out.
     *
     * <p>With several people in view this returns a verdict for one of them per
     * frame — the nearest unresolved person first. At ~6fps that is fast enough
     * that a two-person arrival is fully resolved well inside the time it takes
     * the first person to walk through.
     */
    public Optional<RecognitionResult> process(Mat frame) throws OrtException {
        synchronized (inferenceLock) {
            long now = System.currentTimeMillis();

            // SCRFD keeps running every frame; a miss on its own means nothing,
            // which is why tracks age out on a timer rather than on one empty
            // detection.
            List<FaceDetectionService.Candidate> candidates = detectionService.detectCandidates(frame);
            // Diagnostics/UI only (see isFacePresent()) — "a face is somewhere in
            // frame this pass", independent of pose/recognizability and of
            // whether any track ever resolves. Never read by matching logic.
            facePresent = !candidates.isEmpty();
            associate(candidates, now);
            expireTracks(now);

            if (tracks.isEmpty()) {
                return Optional.empty();
            }
            return spendFrame(frame, now);
        }
    }

    // ---------------------------------------------------------------- tracking

    /**
     * Matches this frame's faces onto the existing tracks, greedily and
     * largest-face-first so the person at the gate wins any contested
     * association over someone further back. Anything that doesn't overlap an
     * existing track becomes a new one.
     */
    private void associate(List<FaceDetectionService.Candidate> candidates, long now) {
        for (Track t : tracks) {
            t.recognizableNow = false;
        }
        if (candidates.isEmpty()) {
            return;
        }

        List<FaceDetectionService.Candidate> ordered = new ArrayList<>(candidates);
        ordered.sort((a, b) -> Float.compare(
                b.box().width() * b.box().height(),
                a.box().width() * a.box().height()));

        // Identity-based: two tracks are never "equal", we mean this exact one.
        Set<Track> claimed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

        for (FaceDetectionService.Candidate candidate : ordered) {
            Track best = null;
            float bestIou = MIN_CONTINUITY_IOU;
            for (Track t : tracks) {
                if (claimed.contains(t)) {
                    continue;
                }
                float iou = FaceBoxUtils.iou(candidate.box(), t.box);
                if (iou >= bestIou) {
                    bestIou = iou;
                    best = t;
                }
            }

            if (best == null) {
                best = newTrack(candidate.box(), now);
            } else {
                best.box = candidate.box();
            }

            best.lastSeenMs = now;
            best.recognizableNow = candidate.recognizable();
            claimed.add(best);
        }
    }

    private Track newTrack(FaceBox box, long now) {
        if (tracks.size() >= MAX_TRACKS) {
            Track stalest = tracks.get(0);
            for (Track t : tracks) {
                if (t.lastSeenMs < stalest.lastSeenMs) {
                    stalest = t;
                }
            }
            tracks.remove(stalest);
        }
        Track track = new Track(box, now);
        tracks.add(track);
        return track;
    }

    private void expireTracks(long now) {
        tracks.removeIf(t -> (now - t.lastSeenMs)
                > (t.resolved ? RESOLVED_TRACK_TTL_MS : UNRESOLVED_TRACK_TTL_MS));
    }

    // ------------------------------------------------------------- frame spend

    /**
     * Picks the one track worth an inference this frame and runs it.
     *
     * <p>Priority is: the nearest person we haven't identified yet, and only if
     * nobody is waiting, a re-check of somebody we already have. That ordering
     * is what makes the re-verification free — it never delays a new arrival.
     */
    private Optional<RecognitionResult> spendFrame(Mat frame, long now) throws OrtException {
        Track target = null;
        for (Track t : tracks) {
            if (t.resolved || !t.recognizableNow) continue;
            if ((now - t.firstSeenMs) < REQUIRED_STABLE_MS) continue;
            if (now < t.silentUntilMs) continue;                 // negative just delivered
            if (target == null || t.area() > target.area()) target = t;
        }

        boolean verification = false;
        if (target == null) {
            for (Track t : tracks) {
                if (!t.resolved || !t.recognizableNow) continue;
                if ((now - t.lastVerifyMs) < RESOLVED_RECHECK_MS) continue;
                if (target == null || t.area() > target.area()) target = t;
            }
            verification = target != null;
        }

        if (target == null) {
            return Optional.empty();
        }

        Optional<FaceDetectionResult> detection =
                detectionService.buildRecognitionResult(frame, target.box);
        if (detection.isEmpty()) {
            return Optional.empty();
        }

        RecognitionResult attempt;
        FaceDetectionResult detected = detection.get();
        try {
            attempt = recognizeUnlocked(detected.alignedFaceMat, false);
            // Focus score of the exact crop this attempt was computed from, so
            // the log can pair sharpness with the score it produced.
            attempt.setSharpness(detected.sharpness);
        } finally {
            // The crops are clones owned by us (see FaceDetectionResult) and
            // nothing downstream holds a reference to them past this point —
            // release them here so the worker loop doesn't grow native memory
            // for every frame it looks at.
            detected.release();
        }

        return verification
                ? verifyResolved(target, attempt, now)
                : accumulate(target, attempt, now);
    }

    /**
     * Re-check of a track that already has a verdict. Nothing is ever announced
     * from here directly: either the identity still holds (stay silent), or the
     * track is handed back to the normal evidence path as if the person had
     * just walked up — which is exactly what has happened when a queue hand-off
     * let someone inherit this track.
     */
    private Optional<RecognitionResult> verifyResolved(Track track, RecognitionResult attempt, long now) {
        track.lastVerifyMs = now;

        boolean confirmed = attempt.getStatus() == RecognitionStatus.MATCHED
                && attempt.getMemberId() == track.resolvedMemberId;
        if (confirmed) {
            track.verifyMismatches = 0;
            return Optional.empty();
        }

        boolean matchedSomeoneElse = attempt.getStatus() == RecognitionStatus.MATCHED;
        track.verifyMismatches++;

        if (matchedSomeoneElse || track.verifyMismatches >= VERIFY_MISMATCH_LIMIT) {
            rearm(track, now);
            // This attempt is the first sample of the new person's round rather
            // than being thrown away.
            return accumulate(track, attempt, now);
        }
        return Optional.empty();
    }

    /** Hands a resolved track back to the evidence path for a fresh identification. */
    private void rearm(Track track, long now) {
        track.resolved = false;
        track.resolvedMemberId = 0;
        track.verifyMismatches = 0;
        track.attempts.clear();
        track.firstAttemptMs = -1;
        track.silentUntilMs = 0;
        track.firstSeenMs = now;
    }

    /**
     * Adds one recognition attempt to a track's sliding window and decides
     * whether that window now justifies a verdict.
     */
    private Optional<RecognitionResult> accumulate(Track track, RecognitionResult attempt, long now) {
        if (track.firstAttemptMs == -1) {
            track.firstAttemptMs = now;
        }
        track.attempts.add(attempt);

        Optional<RecognitionResult> agreed = majorityMatch(track.attempts);
        if (agreed.isPresent()) {
            // Positive verdicts are never delayed by the evidence window —
            // agreement between attempts IS the evidence.
            return Optional.of(finalizeResult(track, agreed.get(), now, false));
        }

        // No agreement yet. Decide whether we've genuinely given this person a
        // fair chance, or whether we should keep looking at them.
        boolean enoughAttempts = track.attempts.size() >= MAX_RECOGNITION_ATTEMPTS;
        long requiredWindowMs = isNearMiss(track.attempts) ? EXTENDED_EVIDENCE_MS : MIN_EVIDENCE_MS;
        boolean observedLongEnough = (now - track.firstAttemptMs) >= requiredWindowMs;

        // Nothing in the gallery is close to this face and the score isn't
        // climbing — no member to keep waiting for, so a negative verdict can
        // skip the rest of the evidence window.
        boolean fastNegative = track.attempts.size() >= FAST_NEGATIVE_MIN_ATTEMPTS
                && (now - track.firstAttemptMs) >= FAST_NEGATIVE_MIN_MS
                && isConfidentNonMatch(track.attempts);

        if ((enoughAttempts && observedLongEnough) || fastNegative) {
            // Fully attempted over a real observation window with no member
            // reaching agreement — this is a completed verdict, and the one
            // case that should surface as a genuine "not found".
            return Optional.of(finalizeResult(track, inconclusiveResult(track.attempts), now, true));
        }

        if (enoughAttempts) {
            // Attempts are full but we haven't watched long enough yet: slide
            // the window forward (drop the oldest sample) and keep collecting
            // fresher ones. This is what lets a member who arrived at a bad
            // angle still be matched a second later, instead of being judged on
            // their first few frames.
            track.attempts.remove(0);
        }

        // Still gathering evidence — no verdict yet, stay silent this frame
        // rather than showing anything.
        return Optional.empty();
    }

    private RecognitionResult finalizeResult(Track track, RecognitionResult result, long now, boolean negative) {
        if (negative && result.getStatus() == RecognitionStatus.UNKNOWN_FACE) {
            applyLowConfidenceHint(track, result);
        }
        logVerdict(track, result, now, negative);

        track.attempts.clear();
        track.firstAttemptMs = -1;

        if (negative) {
            track.silentUntilMs = now + NEGATIVE_VERDICT_SILENCE_MS;
        } else if (result.getStatus() == RecognitionStatus.MATCHED) {
            // Successful identification — latch it against the MEMBER, so a
            // different person inheriting this track can't hide behind it, and
            // schedule the first re-check.
            track.resolved = true;
            track.resolvedMemberId = result.getMemberId();
            track.verifyMismatches = 0;
            track.lastVerifyMs = now;
            track.silentUntilMs = 0; // a stale negative-silence deadline must not outlive the match
        }

        return result;
    }

    /**
     * When a face is rejected outright — not merely deferred, not AMBIGUOUS —
     * but the closest candidate across this track's attempts scored within
     * {@link #UI_HINT_BAND} of the bar, attaches that candidate to the result
     * so the UI can offer staff a "might be this member?" confirmation instead
     * of a bare rejection. Must run before {@code track.attempts} is cleared.
     */
    private void applyLowConfidenceHint(Track track, RecognitionResult result) {
        float bestScore = -1f;
        int nearMemberId = 0;
        for (RecognitionResult r : track.attempts) {
            if (r.getScore() > bestScore) {
                bestScore = r.getScore();
                nearMemberId = r.getNearestMemberId();
            }
        }
        if (nearMemberId != 0 && bestScore >= (recognitionService.getMatchThreshold() - UI_HINT_BAND)) {
            result.setNearestMemberId(nearMemberId);
            result.setNearMiss(true);
        }
    }

    /**
     * Records the completed verdict to recognition-log.csv (see RecognitionLog).
     * Runs before the track's attempts are cleared. Never throws.
     */
    private void logVerdict(Track track, RecognitionResult result, long now, boolean negative) {
        String verdict = negative
                ? (result.getStatus() == RecognitionStatus.AMBIGUOUS ? "AMBIGUOUS" : "NOT_FOUND")
                : result.getStatus().name();
        long evidenceMs = track.firstAttemptMs == -1 ? 0 : now - track.firstAttemptMs;

        // Runner-up on the strongest attempt — the genuine/impostor gap that a
        // future matchMargin re-fit is fitted against. The strongest attempt
        // also carries the most trustworthy "who was this closest to", which is
        // what makes a repeatedly-rejected member identifiable in the log.
        float runnerUp = 0f;
        float bestScore = -1f;
        int nearMemberId = 0;
        for (RecognitionResult r : track.attempts) {
            if (r.getScore() > bestScore) {
                bestScore = r.getScore();
                runnerUp = r.getRunnerUpScore();
                nearMemberId = r.getNearestMemberId();
            }
        }
        if (result.getMemberId() != 0) {
            nearMemberId = result.getMemberId();
        }

        RecognitionLog.verdict(verdict, result.getMemberId(), nearMemberId, result.getScore(),
                runnerUp, track.attempts, evidenceMs, isNearMiss(track.attempts),
                track.box == null ? 0f : track.box.width());
    }

    // ------------------------------------------------------------ vote & bands

    /**
     * True if the best similarity seen across the attempts so far is close
     * enough to the match threshold that this is more plausibly an enrolled
     * member in a poor frame than an actual stranger. Used to give that person
     * more time before any "not found" is announced.
     */
    private boolean isNearMiss(List<RecognitionResult> attempts) {
        float best = 0f;
        for (RecognitionResult r : attempts) {
            if (r.getScore() > best) best = r.getScore();
        }
        return best >= (recognitionService.getMatchThreshold() - nearMissBand);
    }

    /**
     * True if EVERY attempt so far scored well below the match threshold (by at
     * least confidentNonMatchMargin) AND the score is not trending upward. That
     * means the gallery has nothing close to this face and the person isn't
     * just settling into a better frame, so a "not found" can be delivered
     * without the full evidence window.
     *
     * <p>Only ever shortens the wait before a negative verdict. If any attempt
     * comes even close to the threshold, or the score is climbing, this returns
     * false and the normal MIN_EVIDENCE_MS / EXTENDED_EVIDENCE_MS logic applies
     * unchanged.
     */
    private boolean isConfidentNonMatch(List<RecognitionResult> attempts) {
        float ceiling = recognitionService.getMatchThreshold() - confidentNonMatchMargin;

        for (RecognitionResult r : attempts) {
            if (Math.max(r.getScore(), 0f) >= ceiling) {
                return false; // one attempt got close — stay patient
            }
        }

        float firstScore = Math.max(attempts.get(0).getScore(), 0f);
        float lastScore = Math.max(attempts.get(attempts.size() - 1).getScore(), 0f);
        boolean climbing = (lastScore - firstScore) > CONFIDENT_NON_MATCH_CLIMB_EPS;

        return !climbing;
    }

    /**
     * Confidence-adaptive majority vote. The member with the most agreeing
     * MATCHED attempts is committed to MATCHED once their count reaches either:
     * <ul>
     *   <li>voteStrongAgreeing, with at least one attempt scoring
     *       matchThreshold + voteStrongMargin ("strong"), or</li>
     *   <li>voteMarginalAgreeing, at any strength.</li>
     * </ul>
     * A clearly-recognised member produces strong scores and matches on the
     * fast path; a member sitting right on the bar — or a look-alike picking up
     * a couple of lucky frames — needs the larger consensus.
     *
     * <p>If a SECOND member also reached voteContestedThreshold agreeing frames,
     * the window is splitting between two look-alikes: return empty (keep
     * collecting — more frames may separate them), and let the window-close
     * path report AMBIGUOUS rather than guess. Reported score is the highest
     * among the winner's agreeing attempts.
     */
    private Optional<RecognitionResult> majorityMatch(List<RecognitionResult> attempts) {
        float strongBar = recognitionService.getMatchThreshold() + voteStrongMargin;

        Map<Integer, Integer> counts = new HashMap<>();
        Map<Integer, Integer> strongCounts = new HashMap<>();
        Map<Integer, Float> bestScores = new HashMap<>();
        Map<Integer, Float> runnerUps = new HashMap<>();

        for (RecognitionResult r : attempts) {
            if (r.getStatus() != RecognitionStatus.MATCHED) continue;
            int id = r.getMemberId();
            counts.merge(id, 1, Integer::sum);
            if (r.getScore() >= strongBar) {
                strongCounts.merge(id, 1, Integer::sum);
            }
            bestScores.merge(id, r.getScore(), Math::max);
            runnerUps.merge(id, r.getRunnerUpScore(), Math::max);
        }
        if (counts.isEmpty()) {
            return Optional.empty();
        }

        int winner = -1;
        int winnerVotes = 0;
        int runnerUpVotes = 0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            int v = e.getValue();
            if (v > winnerVotes) {
                runnerUpVotes = winnerVotes;
                winnerVotes = v;
                winner = e.getKey();
            } else if (v > runnerUpVotes) {
                runnerUpVotes = v;
            }
        }

        int strong = strongCounts.getOrDefault(winner, 0);
        boolean enough = (winnerVotes >= voteStrongAgreeing && strong >= 1)
                || winnerVotes >= voteMarginalAgreeing;
        if (!enough || runnerUpVotes >= voteContestedThreshold) {
            return Optional.empty();
        }

        RecognitionResult out =
                new RecognitionResult(winner, bestScores.get(winner), RecognitionStatus.MATCHED);
        out.setRunnerUpScore(runnerUps.get(winner));
        return Optional.of(out);
    }

    /**
     * Called once the evidence window has closed with no member committed. Stay
     * cautious — report AMBIGUOUS rather than a hard "not found" — if any
     * attempt was AMBIGUOUS, or if two members each drew voteContestedThreshold
     * agreeing frames (a look-alike split). Otherwise it's a genuine,
     * fully-attempted non-match.
     */
    private RecognitionResult inconclusiveResult(List<RecognitionResult> attempts) {
        Map<Integer, Integer> matchedCounts = new HashMap<>();
        for (RecognitionResult r : attempts) {
            if (r.getStatus() == RecognitionStatus.AMBIGUOUS) {
                return r;
            }
            if (r.getStatus() == RecognitionStatus.MATCHED) {
                matchedCounts.merge(r.getMemberId(), 1, Integer::sum);
            }
        }
        long contestedMembers = matchedCounts.values().stream()
                .filter(c -> c >= voteContestedThreshold)
                .count();
        if (contestedMembers >= 2) {
            return new RecognitionResult(RecognitionStatus.AMBIGUOUS);
        }
        return new RecognitionResult(RecognitionStatus.UNKNOWN_FACE);
    }

    // ------------------------------------------------------------- entry points

    /**
     * Forgets everyone currently being tracked. Call when the frame source
     * changes underneath the pipeline (e.g. the camera dropped and reconnected)
     * so a stale box from the old feed can't be associated with a new arrival.
     */
    public void resetTracking() {
        synchronized (inferenceLock) {
            tracks.clear();
        }
    }

    /** See {@link #facePresent}. A plain volatile read — deliberately not
     *  synchronized on {@link #inferenceLock}, so a UI-driven poll of this can
     *  never contend with an in-flight recognition call. */
    public boolean isFacePresent() {
        return facePresent;
    }

    /**
     * Single-shot detection for the enrollment path — no tracking, no evidence,
     * just "is there one good face in this frame". Takes the inference lock:
     * this is called from the FX thread while the gate worker may be mid-frame.
     */
    public Optional<FaceDetectionResult> detect(Mat frame) throws OrtException {
        synchronized (inferenceLock) {
            return detectionService.detectForRecognition(frame);
        }
    }

    /**
     * Embeds an already-aligned face and matches it against the gallery. Used
     * by enrollment (which also wants the embedding back). Takes the inference
     * lock for the same reason {@link #detect} does.
     */
    public RecognitionResult recognize(Mat frame, boolean includeEmbedding) throws OrtException {
        synchronized (inferenceLock) {
            return recognizeUnlocked(frame, includeEmbedding);
        }
    }

    /** Caller must already hold {@link #inferenceLock}. */
    private RecognitionResult recognizeUnlocked(Mat frame, boolean includeEmbedding) throws OrtException {
        return recognitionService
                .identify(frame, includeEmbedding)
                .orElse(new RecognitionResult(RecognitionStatus.UNKNOWN_FACE));
    }

    public synchronized static FaceProcessor getInstance() throws OrtException {
        if (instance == null) {
            instance = new FaceProcessor();
        }
        return instance;
    }

    /**
     * Releases the ONNX detector/recognizer sessions and the shared ORT
     * environment — and with them the native inference thread pools that a
     * JVM signal does NOT stop. No-op if the pipeline was never started.
     * Call only after the camera / async face-processing threads have been
     * stopped (see {@link com.GymGate.bussines.services.RestartAppService#releaseResources()}).
     * Idempotent; never throws.
     */
    public synchronized static void shutdown() {
        if (instance == null) {
            return;
        }
        try {
            com.GymGate.Ai.models.ModelManager.getInstance().close();
        } catch (Throwable t) {
            System.err.println("FaceProcessor: model shutdown failed — " + t.getMessage());
        }
        instance = null;
    }
}
