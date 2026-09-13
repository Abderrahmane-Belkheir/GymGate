package com.GymGate.Camera;

import com.github.sarxos.webcam.Webcam;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Resolves which physical camera to use at startup. Matches by device NAME
 * (stable across reboots/replugs) rather than raw index (which can silently
 * shift), then resolves that match down to the index OpenCV's VideoCapture
 * needs.
 *
 * webcam-capture is used only to enumerate device NAMES cheaply — actual
 * video capture still goes through OpenCV's VideoCapture(index) elsewhere.
 */
 class CameraDiscoveryService {

    private static final String PREF_LAST_DEVICE_NAME = "last_resolved_camera_name";
    private final Preferences prefs = Preferences.userNodeForPackage(CameraDiscoveryService.class);

    /** All cameras currently detected on the machine, in enumeration order. */
    public List<CameraDeviceInfo> listAvailableCameras() {
        List<CameraDeviceInfo> devices = new ArrayList<>();
        List<Webcam> webcams = Webcam.getWebcams();
        for (int i = 0; i < webcams.size(); i++) {
            System.out.println("Detected: " + webcams.get(i).getName());
            devices.add(new CameraDeviceInfo(webcams.get(i).getName(), i));
        }
        return devices;
    }

    /**
     * Resolves the camera to use: the first currently-connected device the
     * build has a calibration profile for.
     */
    public Optional<CameraDeviceInfo> resolveCamera(CaptureProfileSet profileSet) {
        List<CameraDeviceInfo> available = listAvailableCameras();

        if (available.isEmpty()) {
            System.out.println("No cameras detected on this machine.");
            return Optional.empty();
        }

        List<CaptureProfile> profiles = profileSet.getProfiles();
        if (profiles == null || profiles.isEmpty()) {
            System.out.println("No usable camera detected.");
            return Optional.empty();
        }

        List<CameraDeviceInfo> matches = new ArrayList<>();
        for (CameraDeviceInfo device : available) {
            if (profiles.stream().anyMatch(p -> p.appliesTo(device.getName()))) {
                matches.add(device);
            }
        }

        if (matches.isEmpty()) {
            System.out.println("No usable camera detected. Present: " + available);
            return Optional.empty();
        }

        if (matches.size() > 1) {
            System.out.println("Multiple usable cameras detected; using the first.");
        }

        CameraDeviceInfo chosen = matches.get(0);
        prefs.put(PREF_LAST_DEVICE_NAME, chosen.getName());
        System.out.println("Resolved camera: " + chosen);
        return Optional.of(chosen);
    }
}
