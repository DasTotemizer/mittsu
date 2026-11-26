# Mittsu 三

An unofficial native Android companion for [WaniKani](https://www.wanikani.com) where every lesson and
review question is answered by tapping one of **three** answers. No keyboard during study.

> Mittsu is a third-party app. It is not made by or affiliated with WaniKani or Tofugu.
> WaniKani stays the source of truth for your account, SRS progress and review history.

| Light | Dark |
| --- | --- |
| ![Dashboard, light](docs/screenshots/dashboard-light.png) | ![Dashboard, dark](docs/screenshots/dashboard-dark.png) |

*Phase 1 dashboard with sample data.*

| Question | After a wrong answer |
| --- | --- |
| ![Quiz](docs/screenshots/quiz-light.png) | ![Feedback](docs/screenshots/quiz-answered-dark.png) |

## Status

Built in phases (see [docs/ROADMAP.md](docs/ROADMAP.md)).

| Phase | What | State |
| --- | --- | --- |
| 1 | Project foundation, navigation, dashboard on sample data, visual identity | **Done** |
| 2 | WaniKani connection: token, profile, subjects, assignments, caching | **Done** |
| 3 | Three-answer quiz engine and difficulty | **Done** |
| 4 | Real reviews submitted to WaniKani | **Done** |
| 5 | Lessons and practice mode | **Done** |
| 6 | Smart review management (Daily Balance, Gentle Recovery) | Next |
| 7 | Holiday Mode | |
| 8 | Statistics, polish, offline | |

## Build

Requirements: JDK 17+ (21 recommended), Android SDK with **platform 37.2** and build-tools 37.

```bash
./gradlew :app:assembleDebug          # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # unit + Robolectric UI tests
```

Or open the folder in a current Android Studio and press Run.

Every push and pull request also builds on GitHub Actions; the debug APK is attached to the run as
the `mittsu-debug-apk` artifact.

### Installing on your phone

1. Download `app-debug.apk` (from a GitHub Actions run, or build it yourself).
2. On the phone, allow "Install unknown apps" for your browser or file manager.
3. Open the APK and install. The debug build installs as **Mittsu** with id `app.mittsu.debug`,
   so it can live alongside a future release build.

### Renaming the app

The name and application id are set in `gradle.properties`:

```properties
mittsu.appName=Mittsu
mittsu.applicationId=app.mittsu
```

Or override per build: `./gradlew assembleDebug -Pmittsu.appName="My Name"`.

### Design screenshots

```bash
./gradlew :app:testDebugUnitTest --tests '*ScreenshotCapture*' -Pmittsu.screenshots=$PWD/docs/screenshots
```

## Connecting WaniKani

Mittsu uses a personal access token, never your password. Create one at
<https://www.wanikani.com/settings/personal_access_tokens> with these permissions:

- default read access
- `reviews:create` (to submit reviews)
- `assignments:start` (to complete lessons)

In the app, tap **Connect** on the sample-data banner (or Settings → Connect WaniKani), paste the
token and tap Connect. Mittsu checks the token with WaniKani before saving it, then downloads your
subjects, assignments and review statistics. The first download takes a little while; after that
only changes are fetched.

The token is encrypted with an AES key held in the Android Keystore, stored in a private file that
is excluded from backups, and never logged. Disconnecting (Settings) deletes the token and all
cached WaniKani data from the phone; your WaniKani account is not affected.

![Connect screen](docs/screenshots/connect-light.png)

## Docs

- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap and known limitations](docs/ROADMAP.md)
