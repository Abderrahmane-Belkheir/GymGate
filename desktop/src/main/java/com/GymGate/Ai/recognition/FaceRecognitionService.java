package com.GymGate.Ai.recognition;

import ai.onnxruntime.OrtException;
import com.GymGate.Ai.models.ModelManager;
import org.opencv.core.Mat;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class FaceRecognitionService {

    private final ArcFaceRecognizer faceRecognizer;

    // Defaults are overwritten from the active model's profile in the
    // constructor (see RecognitionConfig); kept here only as a safe fallback.
    private float matchThreshold = 0.65f;
    private float matchMargin = 0.05f;
    private int minSupportingEmbeddings = 1;   // 1 == consistency gate disabled
    private float supportSlack = 0.15f;
    private float centroidSlack = 0.12f;

    public FaceRecognitionService() throws OrtException{
        faceRecognizer = new ArcFaceRecognizer(ModelManager.getInstance().recognizer().session());
        RecognitionConfig.Profile profile = RecognitionConfig.active();
        this.matchThreshold = profile.matchThreshold();
        this.matchMargin = profile.matchMargin();
        this.minSupportingEmbeddings = profile.minSupportingEmbeddings();
        this.supportSlack = profile.supportSlack();
        this.centroidSlack = profile.centroidSlack();
    }
    public Optional<RecognitionResult> identify(Mat mat, boolean includeEmbedding) throws OrtException {
        return findBestMatch(faceRecognizer.getEmbedding(mat), includeEmbedding);
    }

    private Optional<RecognitionResult> findBestMatch(float[] probeEmbedding, boolean include) {

        int bestMemberId = -1;
        float bestScore = -1f;
        float secondBestScore = -1f;
        int bestMemberSupport = 0;
        List<float[]> bestMemberGallery = null;

        float supportBar = matchThreshold - supportSlack;

        for (Map.Entry<Integer, List<float[]>> entry : Embeddings.knownEmbeddings.entrySet()) {

            List<float[]> gallery = entry.getValue();
            if (gallery.isEmpty()) {
                continue;
            }

            float memberBestScore = -1f;
            int memberSupport = 0;
            for (float[] embedding : gallery) {
                float score = faceRecognizer.cosineSimilarity(probeEmbedding, embedding);
                if (score > memberBestScore) {
                    memberBestScore = score;
                }
                if (score >= supportBar) {
                    memberSupport++;
                }
            }

            if (memberBestScore > bestScore) {
                secondBestScore = bestScore;
                bestScore = memberBestScore;
                bestMemberId = entry.getKey();
                bestMemberSupport = memberSupport;
                bestMemberGallery = gallery;
            } else if (memberBestScore > secondBestScore) {
                secondBestScore = memberBestScore;
            }
        }

        boolean aboveThreshold = bestScore >= matchThreshold;
        boolean confidentMargin = secondBestScore < 0 || (bestScore - secondBestScore) >= matchMargin;

        // Consistency: a single stored angle lining up with the probe is not
        // enough to commit — a stranger or a sibling can flukingly match one of
        // a member's enrollment shots. When the winner has enough embeddings on
        // file for the check to be meaningful, require either `minSupporting`
        // of them within `supportSlack` of the bar, OR the averaged template
        // (centroid) within `centroidSlack`. The centroid is only needed here,
        // for the one winning member, so it's computed once — not per member.
        boolean consistencyChecked = aboveThreshold && confidentMargin
                && minSupportingEmbeddings > 1
                && bestMemberGallery != null
                && bestMemberGallery.size() >= minSupportingEmbeddings;
        boolean consistent = !consistencyChecked
                || bestMemberSupport >= minSupportingEmbeddings
                || faceRecognizer.cosineSimilarity(probeEmbedding, centroidOf(bestMemberGallery))
                        >= (matchThreshold - centroidSlack);

        RecognitionResult result;
        if (aboveThreshold && confidentMargin && consistent) {
            result = new RecognitionResult(bestMemberId, bestScore, RecognitionStatus.MATCHED);
        } else if (aboveThreshold) {
            // Cleared the absolute bar but either too close to the runner-up
            // member, or matched on only one stored angle — don't guess. The
            // caller re-presents rather than committing to a possible misroute.
            result = new RecognitionResult(bestMemberId, bestScore, RecognitionStatus.AMBIGUOUS);
        } else {
            // Below the absolute bar. memberId 0 (no identity claimed), but the
            // best score IS reported — it's the only signal that separates
            // "nobody in the gallery is close" (a stranger) from "almost
            // certainly an enrolled member in a poor frame", which FaceProcessor
            // uses to keep waiting instead of firing a premature "not found".
            result = new RecognitionResult(0, Math.max(bestScore, 0f), RecognitionStatus.UNKNOWN_FACE);
        }

        result.setRunnerUpScore(Math.max(secondBestScore, 0f));
        // Kept separately from memberId on purpose: memberId stays 0 on the
        // UNKNOWN branch so no caller can mistake a near miss for an identity
        // claim, while the log still gets to name who was nearly matched.
        result.setNearestMemberId(Math.max(bestMemberId, 0)); // -1 == empty gallery
        // The enrollment path always wants the probe embedding back, regardless
        // of whether it matched anyone.
        if (include) {
            result.setEmbedding(probeEmbedding);
        }
        return Optional.of(result);
    }

    /**
     * L2-normalized mean of a member's enrollment embeddings — an averaged
     * "template" for the face. More robust to per-shot noise than any single
     * embedding, and used as an alternative way to satisfy the consistency
     * check. Computed on the fly: a few hundred 512-float sums per attempt is
     * well under a millisecond and avoids a second map to keep in sync with
     * {@link Embeddings#knownEmbeddings}.
     */
    private static float[] centroidOf(List<float[]> gallery) {
        int dim = gallery.get(0).length;
        float[] mean = new float[dim];
        for (float[] e : gallery) {
            for (int i = 0; i < dim; i++) {
                mean[i] += e[i];
            }
        }
        double norm = 0;
        for (int i = 0; i < dim; i++) {
            mean[i] /= gallery.size();
            norm += (double) mean[i] * mean[i];
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                mean[i] /= (float) norm;
            }
        }
        return mean;
    }

    /** Minimum absolute cosine similarity for a candidate to be considered at all. Default 0.65. */
    public void setMatchThreshold(float matchThreshold) {
        this.matchThreshold = matchThreshold;
    }

    /** Current absolute-similarity bar — used by FaceProcessor to recognise a near miss. */
    public float getMatchThreshold() {
        return matchThreshold;
    }

    /** Minimum lead the best candidate needs over the runner-up to be trusted. Default 0.05. */
    public void setMatchMargin(float matchMargin) {
        this.matchMargin = matchMargin;
    }
}
