# In-app update download and install

Date: 2026-10-02

Status: approved by user in conversation

## Goal

After the user taps Download update, the APK downloads inside Open Tube. A notification shows progress and Cancel. The browser never opens when a GitHub APK asset exists. When the file is ready, Android's Install screen appears automatically if Open Tube is in the foreground (or when the user returns to the app). If they already left, they tap Ready to install.

Ship as Open Tube `1.2.0` / versionCode `13`. Users on `1.0.0` / `1.1.0` still open the browser for this APK (those builds cannot be patched). From `1.2.0` onward, later GitHub releases use this flow.

## Non-goals

- Changing `1.0.0` / `1.1.0` already on devices
- Silent install (Android always shows the system Install UI)
- Google Play, in-app billing, or Play Core
- Using system `DownloadManager`
- A full-screen download page or an in-dialog progress bar
- New test framework
- Renaming Kotlin packages away from `com.sparktube.app`

## Chosen approach

**B — app-owned foreground service.** `UpdateDownloadService` fetches the GitHub release APK with OkHttp into app cache, posts our notification (`Downloading {version} — N%` + Cancel), then hands the file to the package installer via the existing FileProvider.

Rejected:

- **A** (DownloadManager): weaker Cancel/copy control; OEM notification text
- **C** (dialog-only progress): dies when the user leaves the app

## User-visible flow

1. Silent check or Menu → Check for update still shows the existing dialog (title, changelog, Download update / Later).
2. Download update starts the service. Dialog dismisses. A toast is optional if a download is already running.
3. Notification: progress bar, percent, version, Cancel. User can keep watching videos.
4. Cancel: abort HTTP, delete the partial file, dismiss the notification.
5. Success, app in foreground (any activity started): open the system Install screen immediately.
6. Success, app in background: notification becomes Ready to install. Tap opens Install. Opening Open Tube later also opens Install.
7. No APK asset on the GitHub release: fall back to opening `htmlUrl` in the browser (same as today).
8. Failure: notification Download failed; partial file deleted.

Android 8+: if the app cannot install unknown packages yet, open the system "allow from this source" screen once. After the user returns, if permission is granted, open Install. Do not loop the settings screen if they refuse; the Ready to install notification tap can ask again.

After a successful upgrade, leftover cache APKs whose version is not newer than the running app are deleted so the new process does not re-prompt.

## Components

| Unit | Role |
|------|------|
| `UpdateChecker` | Still fetches GitHub latest. `openDownload` is replaced by `startDownload`, which starts the service when `apkUrl` is set. |
| `UpdateDownloadService` | Foreground `dataSync` service: GET the APK, write `cacheDir/updates/OpenTube-{tag}.apk`, notify progress, Cancel action. |
| `UpdateInstaller` | Process holder: activity-started count (foreground), pending file, launch installer, unknown-sources gate, stale-file cleanup. Registered from `SparkTubeApp`. |
| FileProvider | Existing `com.sparktube.app.fileprovider`; add `<cache-path path="updates/" />`. |
| Manifest | `REQUEST_INSTALL_PACKAGES`, `FOREGROUND_SERVICE_DATA_SYNC`, service `foregroundServiceType="dataSync"`. |

Download is not routed through `DownloadCenter` (that engine is for videos).

## Data flow

```
Download update
  -> UpdateChecker.startDownload
  -> UpdateDownloadService ACTION_START
  -> OkHttp GET apkUrl (follow redirects) -> cacheDir/updates/*.apk
  -> UpdateInstaller.onReady
       if startedActivities > 0 -> ACTION_VIEW APK (FileProvider)
       else Ready-to-install notification
  -> Activity onStart 0->1 with pending APK -> same ACTION_VIEW
```

Notification tap uses `MainActivity` (`FLAG_ACTIVITY_NEW_TASK`) plus `UpdateInstaller` on any activity start, so a dead process still installs after the user opens the app (file is on disk).

## Permissions and install

- `POST_NOTIFICATIONS` already requested on `MainActivity` (Android 13+).
- `REQUEST_INSTALL_PACKAGES` plus `packageManager.canRequestPackageInstalls()` before `ACTION_VIEW` / `application/vnd.android.package-archive`.
- Install Intent: FileProvider URI, `FLAG_GRANT_READ_URI_PERMISSION`, `FLAG_ACTIVITY_NEW_TASK`.
- The system Install UI is required; the app never claims to install silently.

## Errors

- HTTP / IO failure: failed notification, delete temp file, stop service.
- Cancel: same cleanup, no failed banner.
- Missing `apkUrl`: browser fallback to release page.
- Second Download update while running: do not start another download.

## Versioning

- `versionName` `1.2.0`, `versionCode` `13`
- Tag `v1.2.0` with signed `OpenTube-1.2.0.apk`
- In-app download can only be exercised from a `1.2.0` install against a later GitHub latest (e.g. `1.2.1`)

## Testing (device)

No new unit-test framework. Maintainer sideloads `1.2.0`, then a later tag is used to confirm: no browser, progress + Cancel notification, auto Install when in-app, notification tap / reopen when backgrounded.
