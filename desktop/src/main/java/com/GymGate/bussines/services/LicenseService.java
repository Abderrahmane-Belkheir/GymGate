package com.GymGate.bussines.services;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Reads the encrypted license file from the per-user data directory
 * ({@code %LOCALAPPDATA%\GymGate\data\a.properties} on Windows, otherwise
 * {@code ~/.gymgate/data/a.properties}) and decides whether this is a demo
 * build whose trial window has already closed.
 *
 * <p>The file is not plain text — it is Base64 of an AES-256-GCM blob
 * ({@code iv || ciphertext+tag}) produced by {@code Configure.java} with the
 * same hardcoded key. GCM is authenticated, so any edit to the file makes
 * decryption fail.
 *
 * <p>The authoritative copy lives beside the database (per-user data dir) so a
 * trial can be turned into a full licence by dropping in a new file — no
 * reinstall, no admin, no touching Program Files. On a fresh machine that file
 * does not exist yet; the app then <b>seeds it from the copy bundled in the jar</b>
 * ({@code /a.properties} on the classpath, written by {@code Configure.java} at
 * build time) so the client never has to place a file by hand. Seeding only
 * happens when the per-user file is absent — a later hand-placed licence always
 * wins.
 *
 * <p>Called as the very first thing in {@link com.GymGate.GymGateEntry#start} —
 * before OpenCV, the database, settings or anything else is touched — so an
 * expired trial never spins up native resources it would only have to tear
 * down again.
 *
 * <p>Failure policy (fail safe — anything short of a valid licence is EXPIRED):
 * <ul>
 *   <li>file absent AND nothing bundled to seed from -&gt; EXPIRED
 *   <li>file present (or just seeded), unreadable / fails to decrypt -&gt; EXPIRED
 *   <li>version=demo AND the PC clock reads any date before {@code start} -&gt; EXPIRED
 *   <li>decrypts OK            -&gt; normal version/limit check
 * </ul>
 */
public final class LicenseService {

    private static final String LICENSE_FILE = "a.properties";
    private static final String DEMO = "demo";

    /** Hardcoded key for the license blob. Must match Configure.java. */
    private static final String LICENSE_KEY = "Abdoumimi12";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    /** Support phone number shown on the "trial ended" screen. */
    public static final String CONTACT_PHONE = "0553702233";

    private LicenseService() {
    }

    /**
     * @return {@code false} only for a valid full licence, or a demo licence
     *         whose {@code limit} is today or later and whose {@code start} is
     *         not in the future relative to the PC clock. Anything else — no
     *         file, an unreadable / tampered file, {@code version=demo} past its
     *         limit, or a demo whose clock was turned back before {@code start} —
     *         is {@code true} (expired).
     */
    public static boolean isTrialExpired() {
        Path file = licenseFile();
        if (file == null) {
            System.err.println("license: could not resolve the licence path — treating as expired");
            return true;
        }
        if (!Files.isRegularFile(file) && !seedBundledLicense(file)) {
            System.err.println("license: no licence file at " + file
                    + " and none bundled to seed from — treating as expired");
            return true;
        }

        String blob;
        try {
            blob = Files.readString(file, StandardCharsets.US_ASCII).trim();
        } catch (IOException e) {
            System.err.println("license: file present but could not be read — treating as expired ("
                    + e.getMessage() + ")");
            return true;
        }

        String plain;
        try {
            plain = decrypt(blob);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            System.err.println("license: file is present but could not be decrypted "
                    + "(tampered or corrupt) — treating as expired");
            return true;
        }

        Properties props = new Properties();
        try {
            props.load(new StringReader(plain));
        } catch (IOException e) {
            System.err.println("license: decrypted payload is not readable — treating as expired");
            return true;
        }

        String version = props.getProperty("version", "").trim();
        if (!version.equalsIgnoreCase(DEMO)) {
            return false;
        }

        // Demo only: catch a rolled-back PC clock. 'start' is the licence issue
        // date; a legitimate first run is on or after it. ANY earlier date —
        // even one day — means the clock was turned back to dodge 'limit'.
        String startRaw = props.getProperty("start", "").trim();
        if (!startRaw.isEmpty()) {
            try {
                LocalDate start = LocalDate.parse(startRaw);
                if (LocalDate.now().isBefore(start)) {
                    System.err.println("license: system clock (" + LocalDate.now() + ") is before the licence "
                            + "start date (" + start + ") — clock rolled back; treating as expired");
                    return true;
                }
            } catch (DateTimeParseException e) {
                System.err.println("license: invalid 'start' date '" + startRaw
                        + "' — skipping the clock-rollback check");
            }
        }

        String limitRaw = props.getProperty("limit", "").trim();
        if (limitRaw.isEmpty()) {
            System.err.println("license: version=demo but 'limit' is missing — allowing start");
            return false;
        }

        try {
            LocalDate limit = LocalDate.parse(limitRaw);
            return LocalDate.now().isAfter(limit);
        } catch (DateTimeParseException e) {
            System.err.println("licen.usse: invalid 'limit' date '" + limitRaw
                    + "' — expected yyyy-MM-dd; allowing start");
            return false;
        }
    }

    /**
     * First run on this machine: copy the licence bundled in the jar
     * ({@code /a.properties}) to {@code target}, creating the per-user data dir.
     * Returns {@code true} once {@code target} is a readable file (freshly
     * seeded, or already there from a race). Returns {@code false} — caller
     * treats as expired — when there is no bundled copy or the copy fails.
     * Only ever called when {@code target} does not yet exist, so it never
     * overwrites a licence the client placed by hand.
     */
    private static boolean seedBundledLicense(Path target) {
        try (InputStream in = LicenseService.class.getResourceAsStream("/" + LICENSE_FILE)) {
            if (in == null) {
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("license: seeded " + target + " from the bundled copy");
            return true;
        } catch (IOException e) {
            System.err.println("license: could not seed the bundled licence — " + e.getMessage());
            return Files.isRegularFile(target);
        }
    }

    /**
     * {@code %LOCALAPPDATA%\GymGate\data\a.properties} on Windows, else
     * {@code ~/.gymgate/data/a.properties}. Mirrors
     * {@code AppPaths.resolveUserDataRoot()} — kept inline so the trial gate
     * pulls in nothing and has no side effects when it runs first.
     */
    private static Path licenseFile() {
        try {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path root = (localAppData != null)
                    ? Path.of(localAppData, "GymGate")
                    : Path.of(System.getProperty("user.home"), ".gymgate");
            return root.resolve("data").resolve(LICENSE_FILE);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- crypto (mirror of Configure.java) --------------------------------

    private static byte[] aesKey() throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256")
                .digest(LICENSE_KEY.getBytes(StandardCharsets.UTF_8));
    }

    private static String decrypt(String base64Blob) throws GeneralSecurityException {
        byte[] blob = Base64.getDecoder().decode(base64Blob);
        if (blob.length <= IV_BYTES) {
            throw new IllegalArgumentException("license blob too short");
        }
        byte[] iv = Arrays.copyOfRange(blob, 0, IV_BYTES);
        byte[] body = Arrays.copyOfRange(blob, IV_BYTES, blob.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(aesKey(), "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
    }
}
