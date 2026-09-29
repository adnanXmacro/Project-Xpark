# Open Tube independent fork and update model

Date: 2026-09-29

Status: approved by user in conversation; pending written-spec review

## Goal

Open Tube (this repo: `adnanXmacro/Project-Xpark`) is a full product we own. SparkTube (`devfahim00/SparkTube`) is an external source of core behavior only. SparkTube never notifies our users. Users are asked to update only after we ship a GitHub release on Project-Xpark.

## Non-goals

- Automatic git merge from SparkTube `main`
- Splitting a Gradle `core` module in this pass
- Publishing to Google Play
- Carrying over data from existing `com.sparktube.app` installs
- Renaming Kotlin packages away from `com.sparktube.app`
- Changing Help Line / Discord, extractor internals, or player logic except as needed for branding/updater

## Chosen approach

Manual port. We copy SparkTube files only when the maintainer says a core change landed (or Fahim knocks). We bump our independent version and publish our release. Then the in-app updater sees it.

Rejected: git `upstream` merge (UI collisions) and a core/UI Gradle split (extra refactor; revisit if ports hurt).

## Identity

| Item | Value |
|------|--------|
| Display / launcher name | Open Tube |
| Home header line 1 | Open Tube, with red `O` and red `T` |
| Home header line 2 | `by projectAdnan` |
| `applicationId` | `com.opentubebyproadnan.app` |
| Android `namespace` | keep `com.sparktube.app` |
| Kotlin source package | keep `com.sparktube.app` |
| Theme / style names | keep `Theme.SparkTube` and existing resource names |
| Crash-log folder | leave as-is unless a later branding pass |

Existing installs that used `com.sparktube.app` will not receive an in-place update. Users install Open Tube as a new app once. SparkTube and Open Tube can coexist.

`app_name` string becomes `Open Tube` so the launcher, recents, and About title stay short. The long phrase "Open Tube by project Adnan" is no longer the launcher label.

## Updates

`UpdateChecker` talks only to:

- API: `https://api.github.com/repos/adnanXmacro/Project-Xpark/releases/latest`
- Fallback HTML: `https://github.com/adnanXmacro/Project-Xpark/releases`

Behavior (unchanged UX, new repo):

- Release builds: silent check once per process on `MainActivity` open
- Menu: Check for update (manual)
- Debug builds: silent check stays skipped (`BuildConfig.DEBUG`)
- Compare `BuildConfig.VERSION_NAME` to the GitHub `tag_name` with existing numeric `isNewer`

SparkTube tags and APKs are never fetched or offered.

## Versioning

Independent of SparkTube.

Current tree is `versionName 1.4.1` / `versionCode 10` inherited from SparkTube. First Open Tube identity ship is:

- `versionName`: `1.0.0`
- `versionCode`: `11` (must be greater than 10 only if we had kept the old applicationId; with a new applicationId, `1` would also work. Use `11` so a future return to the old id would still install over. Either is valid; this spec locks `versionName 1.0.0` and `versionCode 11`.)

After that: we bump when we ship. SparkTube's version does not appear in our Play/GitHub identity.

Release tags should match `versionName` (e.g. `v1.0.0`) so `isNewer` works.

## Home header UI

Replace the single `appTitle` TextView in `fragment_home.xml` with a two-line block:

1. `Open Tube` as a `Spannable`: character `O` and the `T` in `Tube` use `spark_red` (`#F03E3E`). Remaining letters use `on_surface` (white in dark theme, near-black in light). Bold, ~22sp.
2. Tagline `by projectAdnan` under it, smaller, `on_surface_variant`, not bold.

Country flag stays on the same header row (end of the title column or beside it). Search / crash-test buttons stay on the right.

Apply this on `HomeFragment` after view creation so it survives theme changes. Do not put the spannable in `strings.xml` (cannot color individual letters there).

Onboarding can keep a plain `Open Tube` string; colored header is the home top bar only.

## About

`MenuFragment.showAbout()`:

- Title: `R.string.app_name` (`Open Tube`)
- Message: existing `about_text` (still mentions SparkTube as the upstream basis)
- Positive button: OK / close only (`android.R.string.ok`)
- Remove the GitHub button and the `devfahim00/SparkTube` URI

## Core vs ours

### Port from SparkTube only when told

- `app/src/main/java/com/sparktube/app/data/`
- `app/src/main/java/com/sparktube/app/net/`
- `app/src/main/java/com/sparktube/app/playback/`
- `app/src/main/java/com/sparktube/app/download/`
- `tools/extractor-patch/` and `app/libs/` extractor jar
- NewPipeExtractor / related Gradle versions
- Behavior-only helpers: `util/Net.kt`, `Formatters.kt`, `Thumbs.kt`, `Avatars.kt`, `AppPrefs.kt` (prefs keys/behavior, not branding)
- `SparkTubeApp.kt` extractor/player wiring (keep our name/theme hooks if any)

### Never take from SparkTube

- `applicationId`, launcher strings, home header, About dialog
- `UpdateChecker.kt` URLs
- Menu Help Line / Discord
- Layouts, themes, drawables, mipmaps we restyle
- `tools/rebrand.sh`, `BRANDING.md`, this repo's README / `index.html`

### Mixed files

Activities/fragments that SparkTube also edits (`PlayerActivity`, `MenuFragment`, `MainActivity`, …): port logic hunks only. Keep our XML, branding, updater, Help Line.

## Port workflow (repeatable)

1. Maintainer (or Fahim) names the SparkTube change (commit, release, or files).
2. Agent fetches those files and applies only core hunks; does not restyle UI from upstream.
3. Agent bumps our `versionName` / `versionCode` when the change is user-facing.
4. Maintainer publishes a GitHub release on `adnanXmacro/Project-Xpark` with an APK asset.
5. Users see silent-on-launch and/or Menu Check for update against our repo.

Until step 4 happens, installed apps do not prompt.

## Branding overlay

Update `BRANDING.md` and `tools/rebrand.sh` so they no longer claim:

- `applicationId` stays `com.sparktube.app`
- updater still reads SparkTube
- overlay must not touch `UpdateChecker`

After this work, rebrand must preserve Open Tube identity and our updater URL if an accidental upstream dump overwrites strings.

## Error handling

- GitHub API failure: silent check no-ops; manual check keeps existing "up to date / failed" UX
- Release with no `.apk` asset: dialog can still open `htmlUrl` (existing `openDownload` behavior)
- Wrong tag format: `isNewer` already treats non-numeric parts as 0

## Testing (manual)

- Fresh install uses package `com.opentubebyproadnan.app`
- Launcher label is `Open Tube`
- Home header shows red O, red T, tagline `by projectAdnan`
- About has no GitHub button
- Check for update hits Project-Xpark, not SparkTube
- SparkTube APK can be installed beside Open Tube
- Debug build does not auto-popup an update on launch

No automated UI tests exist in this repo; do not add a test framework in this pass.

## Implementation order

1. Identity: `applicationId`, `app_name`, branding docs/script
2. Updater URLs + fallback HTML
3. Home header spannable + tagline
4. About dialog: drop GitHub
5. Version bump to `1.0.0` / `11`
6. README / BRANDING notes for the new package and update source
