# GymGate

A gym management platform with AI-based biometric member check-in, offline-first
data sync, automated customer engagement, and a companion mobile app and website.

This is a monorepo with three components:

| Component | Stack                                      | Path        |
|-----------|----------------------------------------------|-------------|
| Desktop   | Java 17, JavaFX 21, Maven                     | `desktop/`  |
| Mobile    | Flutter (Dart), Supabase, Firebase Cloud Messaging | `mobile/`   |
| Website   | React 19, TypeScript, Vite, GSAP, Lenis       | `website/`  |

All three share the same Supabase backend (PostgreSQL with row-level security)
for member, plan, payment, and attendance data.

---

## Desktop App (`desktop/`)

### Features

- Real-time facial recognition check-in (OpenCV + ONNX Runtime, SCRFD
  detection + ArcFace-style embedding)
- Offline-first local SQLite database with automatic sync to Supabase
- Member, plan, payment, and attendance management with revenue/trend reporting
- Automated WhatsApp reminders (expiry, lapsed renewal, inactivity)
- Full English / French / Arabic localization (including RTL)

### Requirements

- JDK 17+
- Maven 3.8+
- Internet access on first build (resolves JavaFX, OpenCV, ONNX Runtime, etc.
  from Maven Central)
- A webcam, for check-in/enrollment features

### Setup

1. **Face recognition models** — `desktop/src/main/resources/models/` is not
   committed; it must contain `scrfd_2.5g.onnx` (detection) and
   `w600k_mbf.onnx` (embedding) for recognition features to work.
2. **Supabase credentials** — `desktop/src/main/resources/supabase.properties`
   holds the `email` / `password` used to authenticate to Supabase.

### Build & Run

```bash
cd desktop
mvn clean package
java -cp "target/libs/*" com.GymGate.Launcher

Launcher is a small indirection class that calls Application.launch(...)
internally, so JavaFX runs from a plain classpath jar without needing
--module-path/--add-modules.

---

Mobile App (mobile/)

Flutter companion app (gymgate_app) reading/writing the same Supabase
backend, with push notifications via Firebase Cloud Messaging (triggered
server-side through Supabase Edge Functions).

Requirements

- Flutter SDK (Dart ^3.13.1)
- A configured Firebase project (for firebase_core / firebase_messaging)

Setup

- mobile/config/supabase.properties — Supabase credentials, bundled as a
  Flutter asset.
- Platform Firebase config (google-services.json for Android,
  GoogleService-Info.plist for iOS) if not already present.

Build & Run

cd mobile
flutter pub get
flutter run

Targets Android, iOS, Windows, Linux, macOS, and web (see mobile/pubspec.yaml
for the full dependency list and platform folders for platform-specific config).

Tests

cd mobile
flutter test

---

Website (website/)

Marketing/landing site built with React 19, TypeScript, and Vite, with GSAP
for animation and Lenis for smooth scrolling.

Requirements

- Node.js (LTS)

Setup & Run

cd website
npm install
npm run dev        # local dev server

Build

npm run build       # type-checks (tsc -b) then builds via Vite
npm run preview      # preview the production build locally

Lint

npm run lint

---

Repository Layout

desktop/     Java/JavaFX desktop app (Maven)
mobile/      Flutter mobile app
website/     React/TypeScript marketing site (Vite)
