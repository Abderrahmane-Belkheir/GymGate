package com.GymGate.bussines.services;

import com.GymGate.bussines.db.dao.PhotoDao;
import javafx.scene.image.Image;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;


/**
 * Member photos are stored as JPEG bytes in the {@code member_photos} table
 * (see {@link PhotoDao}), not as files — so they sync to Supabase with every
 * other table and load straight from the local database.
 */
public class PhotosService {

    /** Longest edge of a stored member photo, in pixels — aspect ratio is kept. */
    private static final int MAX_DIMENSION = 256;
    /** JPEG quality (0-100). High enough to be visually lossless at this size,
     *  while keeping each photo to roughly 10 KB. */
    private static final int JPEG_QUALITY = 90;

    /** Largest number of decoded photos kept in memory at once. Each decoded
     *  {@link Image} at the stored 256 px is ~256 KB, so this is a hard ~6 MB
     *  ceiling. Sized to comfortably cover one screenful of table rows plus a
     *  little scroll-back; the live recognition card only ever needs one. */
    private static final int MAX_CACHED_PHOTOS = 24;

    /**
     * LRU cache of decoded member photos, keyed by member id.
     *
     * <p>Without it, every place a member is shown re-hits the DB and re-decodes
     * the JPEG: the live recognition card on each scan, and the Members /
     * Attendance / Payments row cells on <em>every</em> {@code updateItem} while
     * scrolling. That is a stream of throwaway {@link Image} allocations (GC
     * churn) plus a query per row. The cache collapses each member's photo to a
     * single decode and a single query, and — being capped and access-ordered —
     * also bounds how much decoded-image memory the app holds, instead of the
     * old behaviour where {@code MemberDao.findAll} kept the raw JPEG bytes of
     * <em>every</em> member alive for the whole session.
     *
     * <p>{@code Optional.empty()} is cached too, so members with no photo don't
     * cause a repeat lookup on each scroll. Entries are dropped on
     * {@link #uploadPhoto}/{@link #removePhoto}. Wrapped with
     * {@link Collections#synchronizedMap} because cells (FX thread) and the
     * recognition card update (also FX thread, but via {@code Platform.runLater})
     * both reach it.
     */
    private static final Map<Integer, Optional<Image>> PHOTO_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Optional<Image>> eldest) {
                    return size() > MAX_CACHED_PHOTOS;
                }
            });

    public static boolean uploadPhoto(Mat mat, int id) {
        Mat sized = downscale(mat);
        try {
            MatOfByte buffer = new MatOfByte();
            boolean encoded = Imgcodecs.imencode(
                    ".jpg", sized, buffer,
                    new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, JPEG_QUALITY));
            if (!encoded) {
                return false;
            }
            PhotoDao.getInstance().save(id, buffer.toArray());
            PHOTO_CACHE.remove(id);
            return true;
        } finally {
            if (sized != mat) {
                sized.release();
            }
        }
    }

    public static void removePhoto(int id) {
        PhotoDao.getInstance().delete(id);
        PHOTO_CACHE.remove(id);
    }

    /** Decoded member photo, from the in-memory cache when possible (see
     *  {@link #PHOTO_CACHE}), otherwise loaded from the DB and cached. */
    public static Optional<Image> loadPhoto(int memberId) {
        Optional<Image> cached = PHOTO_CACHE.get(memberId);
        if (cached != null) {
            return cached;
        }
        Optional<Image> loaded = PhotoDao.getInstance().find(memberId).flatMap(PhotosService::toImage);
        PHOTO_CACHE.put(memberId, loaded);
        return loaded;
    }

    /** Decodes stored JPEG bytes into a JavaFX Image — used both by
     *  {@link #loadPhoto} and by callers that already have the bytes in hand
     *  (e.g. a members list loaded with a photo join). */
    public static Optional<Image> toImage(byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) {
            return Optional.empty();
        }
        Image image = new Image(new ByteArrayInputStream(jpeg));
        return image.isError() ? Optional.empty() : Optional.of(image);
    }

    /**
     * Returns {@code mat} unchanged when it already fits within
     * {@link #MAX_DIMENSION}; otherwise a new, downscaled Mat that the caller
     * must release (see {@link #uploadPhoto}).
     */
    private static Mat downscale(Mat mat) {
        int longest = Math.max(mat.cols(), mat.rows());
        if (longest <= MAX_DIMENSION || longest == 0) {
            return mat;
        }
        double scale = (double) MAX_DIMENSION / longest;
        Mat out = new Mat();
        Imgproc.resize(mat, out,
                new Size(Math.round(mat.cols() * scale), Math.round(mat.rows() * scale)),
                0, 0, Imgproc.INTER_AREA);
        return out;
    }
}
