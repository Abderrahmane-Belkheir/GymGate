package com.GymGate.Camera;

import java.util.ArrayList;
import java.util.List;

/**
 * Supplies the capture-device calibration table this build ships with.
 *
 * The pipeline's distance gating and preview scaling are tuned per device, so
 * startup binds to a device the build has a validated profile for. The table is
 * compiled in: there is no external file, so a deployment's tuning can't drift.
 */
public class CaptureProfileService {

    private static final List<CaptureProfile> PROFILES = List.of(
            CaptureProfile.of(0x162fba9d6b6514b8L, 0xde20ab5aab240853L, 6)
    );

    /** Returns a fresh copy of the compiled-in calibration table. */
    public CaptureProfileSet load() {
        CaptureProfileSet set = new CaptureProfileSet();
        set.setProfiles(new ArrayList<>(PROFILES));
        return set;
    }
}
