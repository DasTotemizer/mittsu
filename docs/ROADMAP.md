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

## Phase 2 (done)

- Personal access token sign-in: format check, validated with `GET /user` before it is saved,
  stored AES-256-GCM encrypted with an Android Keystore key, excluded from backups, never logged.
- WaniKani API v2 client (Retrofit + OkHttp + kotlinx.serialization): `Authorization: Bearer` and
  `Wanikani-Revision: 20170710` headers, pagination via `pages.next_url`, rate-limit handling that
  waits for `RateLimit-Reset`, retries on 5xx, clear errors for 401/403/network.
- Room cache of user, subjects (radicals, kanji, vocabulary, kana vocabulary with meanings, auxiliary
  meanings, readings, mnemonics, hints, context sentences), assignments, review statistics and SRS
  systems. Incremental sync with `updated_after` after the first full download.
- Response validation: subjects that would make unsafe questions (no accepted meaning, kanji or
  vocabulary with no accepted reading, no characters or image) are skipped.
- Dashboard shows the real level, reviews due now, lessons available, next review, 24 h forecast,
  SRS distribution and all-time accuracy. Recomputed every minute; pull to refresh; works offline
  from the cache, with a banner when a sync fails or the token stops working.
- Settings: account details, Sync now, Disconnect (removes token and cache).
- Reads WaniKani's Vacation Mode status and shows it (read-only).
- 55 unit and UI tests, including a scripted fake WaniKani server.

## Phase 3 (done)

- Three-answer question generator (`quiz/QuestionGenerator.kt`): one correct answer (the primary
  meaning or reading) and two distractors from real cached WaniKani subjects.
- Safety rules, enforced before any difficulty preference and re-checked by `QuestionSafety`:
  - no distractor equals any accepted meaning, whitelisted synonym or reading of the subject,
    including readings WaniKani shows but doesn't accept (e.g. ひと for 一);
  - a candidate subject sharing any accepted meaning or reading with the target is rejected
    entirely (大きい "Big, Large" never appears for 大; 山 さん never appears for 三 さん);
  - meanings compared case- and "to"-insensitively, readings compared with katakana folded to hiragana;
  - subjects with identical characters are never mixed; distractors are never synonyms of each other;
  - pool widens from same type near level → same type → any type; if still short, a three-button
    self-check is used instead of a misleading question.
- Difficulty only reorders safe candidates: Easy picks clearly different answers, Normal similar
  level and type, Hard visually similar kanji, shared components, similar readings and word shapes.
- Adaptive difficulty: starts Normal, steps up after ≥90% over 10 answers, down below 70%.
- Quiz session engine shared with real reviews: meaning and reading tracked separately, incorrect
  counts per type, wrong answers come back 3–6 questions later, items complete exactly once when every
  required type is correct, serializable snapshot for session recovery.
- Quiz screen: large characters, clear meaning/reading banner, subject type and level, three large
  answer cards, instant colour/icon feedback with the correct answer and a short explanation,
  haptics, progress, difficulty shown, end early, summary with meaning/reading accuracy.
- Quick practice (Practice tab): 10 learned subjects, using your settings. Never contacts WaniKani.
- Settings: adaptive difficulty, question focus (mixed / meaning / reading), shuffle answer positions.
- 102 tests.

## Known limitations so far

- Sample data is shown until an account is connected (labelled in the UI).
- **Reviews completed today** is counted from reviews submitted through Mittsu (from Phase 4).
  WaniKani's API no longer lists past reviews, so reviews done on the website or other apps today
  can't be counted.
- Accuracy is all-time, from WaniKani's review statistics. Per-session accuracy arrives with Phase 4.
- WaniKani does not say which permissions a token has. A token without `reviews:create` or
  `assignments:start` will connect fine and only fail when submitting (handled with a clear message).
- Tested against a scripted fake server and the real API's error responses; not yet with a live token.
- Reviews, Lessons, Progress and the holiday workflow are placeholders. Practice offers quick practice only.
- Radicals that WaniKani shows only as an image (no character) are left out of quizzes for now.
- Portrait-only by design.
- Fonts use the system serif and sans-serif families (no bundled font files yet).

## WaniKani API limitations to design around

- **Vacation Mode cannot be changed through the API.** Mittsu links to the official settings page and
  only records what the user confirms.
- **Lessons are started with `PUT /assignments/{id}/start`;** reviews are submitted with `POST /reviews`.
  Mittsu will never use one for the other.
- **Daily targets cannot change WaniKani due dates.** They limit how many reviews Mittsu offers in a
  session and always show the true outstanding count.
