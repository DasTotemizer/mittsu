# Architecture

Single-module Android app (`:app`), Kotlin + Jetpack Compose + Material 3, MVVM with
unidirectional data flow. Feature code is split by package so it can move into Gradle modules later.

```
app.mittsu
├── MittsuApplication / AppContainer   manual dependency container (no annotation processing)
├── MainActivity                        edge-to-edge host, applies theme preference
├── navigation/                         routes, bottom bar, NavHost, Custom Tab helper
├── ui/theme/                           palette, typography, shapes, extra semantic colours
├── ui/components/                      cards, progress bar, SRS bar, accuracy ring, pills
├── domain/model/                       pure Kotlin models and rules (unit tested)
├── repository/                         data-source interfaces; Phase 1 sample implementation
├── settings/                           DataStore-backed preferences, settings screen
├── network/                            WaniKani API constants (client arrives in Phase 2)
├── dashboard/                          home screen + ViewModel
├── reviews/ lessons/ quiz/ holiday/ stats/   feature screens (placeholders until their phase)
```

Planned for later phases: `database/` (Room cache of subjects, assignments, pending review
submissions, local practice stats), `data/` (WaniKani DTOs and mappers), and a secure token store.

## Key rules baked into the structure

- **Real vs practice are separate types and paths.** Practice code will depend on a read-only
  subject cache and will have no reference to the review submission API, so practice answers cannot
  reach WaniKani by construction. Practice screens use indigo (ai); real screens use vermilion (shu).
- **WaniKani is the source of truth.** The repository layer only reports what the API returns.
  Daily targets and recovery plans are presentation and session-size choices, never data changes.
- **Sample data is flagged.** `DashboardSummary.isSampleData` drives a visible notice, and sample data
  is only used until an account is connected.
- **User settings win over data sources.** The dashboard ViewModel overlays the daily target and
  review-management mode from settings onto whatever the repository provides.

## Visual identity

Japanese traditional colours on a washi-paper base (light) and sumi ink (dark):

| Role | Name | Light | Dark |
| --- | --- | --- | --- |
| Real WaniKani actions | Shu (vermilion) | `#C8442E` | `#E8664E` |
| Practice | Ai (indigo) | `#2C4A7A` | `#7FA3DB` |
| Correct | Matcha | `#5E7F45` | `#94BC74` |
| Highlight / lessons | Kincha (golden tea) | `#C0912F` | `#E2B85A` |
| Holiday | Fuji (wisteria) | `#8C6BA8` | `#C3A6DD` |

Editorial serif headlines (newspaper masthead), sans-serif interface text, uppercase "kicker"
labels, rounded 28dp cards with hairline borders. The launcher icon is the kanji 三 (three) as
three brush strokes on vermilion.

## Testing

`app/src/test` holds JVM unit tests for domain rules and Robolectric Compose tests for screens.
`ScreenshotCapture` renders screens to PNG for design review when `-Pmittsu.screenshots=<dir>` is set.
