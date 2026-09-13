package com.GymGate.Ai.recognition;

/**
 * MATCHED: best candidate cleared both the absolute similarity threshold
 *   and the confidence margin over the runner-up — safe to trust.
 * AMBIGUOUS: best candidate cleared the absolute threshold but was too
 *   close to the second-best candidate to trust with confidence (e.g.
 *   0.71 vs 0.705 in a large gallery). Treat this as "not confirmed", not
 *   as a match — surfacing it separately from UNKNOWN_FACE lets the
 *   caller ask the person to re-present instead of silently guessing.
 * UNKNOWN_FACE: no candidate cleared the absolute threshold at all.
 */
public enum RecognitionStatus {UNKNOWN_FACE, AMBIGUOUS, MATCHED}
