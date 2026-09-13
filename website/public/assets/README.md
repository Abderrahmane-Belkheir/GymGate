# Product film

Drop the final 10-second clip here as:

    public/assets/gymgate-product-video.mp4

`CinematicVideo.tsx` checks for this file at runtime and switches the
"Product" section from its static placeholder to the scroll-scrubbed
cinematic sequence automatically — no code changes needed.

Recommended export: H.264 MP4, ~1920×1080 or 1280×720, no audio track needed
(the element is muted), short GOP / frequent keyframes so seeking to an
arbitrary frame while scrolling stays smooth.
