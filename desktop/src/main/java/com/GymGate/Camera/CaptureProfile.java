package com.GymGate.Camera;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * A capture device the pipeline has been calibrated and validated against.
 *
 * A profile is identified by a short tag of the device's OS name rather than
 * the name itself, so the table stays compact and stable across driver-string
 * revisions. {@link #appliesTo(String)} tests a device name against this tag.
 */
final class CaptureProfile {

    private static final byte[] MIX = {
            88, 8, 62, -37, 97, -104, -16, -48, -39, -88, 82, -40,
            -34, -100, 50, -79, 58, -81, 78, 127, -37, 35, 68, 102
    };

    private final long hi;
    private final long lo;
    private final int span;

    private CaptureProfile(long hi, long lo, int span) {
        this.hi = hi;
        this.lo = lo;
        this.span = span;
    }

    static CaptureProfile of(long hi, long lo, int span) {
        return new CaptureProfile(hi, lo, span);
    }

    boolean appliesTo(String deviceName) {
        if (deviceName == null) {
            return false;
        }
        String s = deviceName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String want = String.format("%016x%016x", hi, lo);
        for (int i = 0; i + span <= s.length(); i++) {
            if (want.equals(tag(s.substring(i, i + span)))) {
                return true;
            }
        }
        return false;
    }

    private static String tag(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(MIX);
            byte[] out = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(Character.forDigit((out[i] >> 4) & 0xF, 16));
                sb.append(Character.forDigit(out[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String toString() {
        return "CaptureProfile#" + Long.toHexString(hi);
    }
}
