# Roadmap and known limitations

## Phase 1 (done)

- Kotlin + Compose + Material 3 project on AGP 9.5, compileSdk 37.2, targetSdk 36 (Android 16), minSdk 26.
- Dependencies for later phases already wired: Retrofit, OkHttp, kotlinx.serialization, Room (KSP),
  DataStore, WorkManager, Custom Tabs.
- App shell: bottom navigation (Today, Practice, Progress, Settings) plus Reviews, Lessons and Holiday routes.
- Dashboard on clearly labelled sample data: level, reviews due now, total outstanding, lessons,
  daily target progress, next review, 24 h forecast, SRS distribution, recent accuracy, actions.
- Settings persisted with DataStore: theme (system/light/dark), difficulty, review mode, daily target 10–150.
- Holiday screen already opens WaniKani's account settings in a Custom Tab.
- 21 unit and UI tests.

## Known limitations so far

- No WaniKani connection yet: all dashboard numbers are sample data (labelled in the UI).
- Reviews, Lessons, Practice, Progress and the holiday workflow are placeholders.
- Portrait-only by design.
- Fonts use the system serif and sans-serif families (no bundled font files yet).

## WaniKani API limitations to design around

- **Vacation Mode cannot be changed through the API.** Mittsu links to the official settings page and
  only records what the user confirms.
- **Lessons are started with `PUT /assignments/{id}/start`;** reviews are submitted with `POST /reviews`.
  Mittsu will never use one for the other.
- **Daily targets cannot change WaniKani due dates.** They limit how many reviews Mittsu offers in a
  session and always show the true outstanding count.
