package com.GymGate.Ai.recognition;


import java.net.http.HttpClient;

public class RecognitionResult{

    private final int memberId;
    private final float score;
    private RecognitionStatus status;
    private float[] embedding;

    // Best similarity to any OTHER member than the winner (0 if there was no
    // runner-up). Not used for the verdict — carried purely so RecognitionLog
    // can record the genuine/impostor score gap for later threshold fitting.
    private float runnerUpScore;

    // The member this face came CLOSEST to, whatever the verdict — including
    // when nothing cleared the bar and {@link #memberId} is deliberately left
    // at 0 ("no identity claimed"). Diagnostics only; never read by the
    // matching logic.
    //
    // This is what makes a false reject investigable. Without it a rejected
    // member logs as "something scored 0.41" with no indication of who, so a
    // member whose enrollment is bad is invisible in the data — you can see
    // that failures are happening but not that they are all the same person.
    private int nearestMemberId;

    // Variance-of-Laplacian focus score of the crop this attempt was computed
    // from (see FaceSharpness). Logged so a blur-rejection floor can be fitted
    // from this camera's own sharpness/score correlation.
    private double sharpness;

    // Set (only on a final UNKNOWN_FACE verdict) when nearestMemberId scored
    // close enough to the bar to plausibly be a poor frame of that member
    // rather than a stranger — see FaceProcessor.applyLowConfidenceHint(). This
    // is what lets a bare rejection instead surface a "might be this member?"
    // prompt for staff to confirm or dismiss. Still diagnostics-derived, never
    // read by the matching logic itself.
    private boolean nearMiss;


    public RecognitionResult(int memberId,float score,RecognitionStatus status){
        this.memberId=memberId;
        this.score=score;
        this.status=status;
    }

    public RecognitionResult(RecognitionStatus status){
        this(0,0,status);
    }

    public RecognitionResult(float[] embedding) {
        this(0,0,RecognitionStatus.UNKNOWN_FACE);
        this.embedding=embedding;
    }

    public int getMemberId(){return memberId;}
    public float getScore(){return score;}
    public float[] getEmbedding(){return embedding;}
    public RecognitionStatus getStatus(){return status;}
    public float getRunnerUpScore(){return runnerUpScore;}

    /** Closest member regardless of verdict — 0 if the gallery was empty. Diagnostics only. */
    public int getNearestMemberId(){return nearestMemberId;}
    public void setNearestMemberId(int nearestMemberId){this.nearestMemberId=nearestMemberId;}

    /** Focus score of the crop behind this attempt; 0 if it was never measured. */
    public double getSharpness(){return sharpness;}
    public void setSharpness(double sharpness){this.sharpness=sharpness;}

    /** True when this UNKNOWN_FACE verdict came close enough to the bar that
     *  {@link #getNearestMemberId()} is worth offering staff as a "might be
     *  this member?" suggestion instead of a bare rejection. */
    public boolean isNearMiss(){return nearMiss;}
    public void setNearMiss(boolean nearMiss){this.nearMiss=nearMiss;}

    /** Set by FaceRecognitionService when the caller asked for the probe
     *  embedding (enrollment); harmless on a recognition-time result. */
    public void setEmbedding(float[] embedding){this.embedding=embedding;}

    public void setRunnerUpScore(float runnerUpScore){this.runnerUpScore=runnerUpScore;}

}
