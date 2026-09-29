# Open Tube branding overlay

This repo is a branded fork of [SparkTube](https://github.com/devfahim00/SparkTube).

Display name: **Open Tube**
Tagline: **by projectAdnan**
Package / `applicationId`: `com.opentubebyproadnan.app`

Kotlin source package stays `com.sparktube.app` so SparkTube core files can be copied with little path rewriting.

In-app "Check for update" reads `adnanXmacro/Project-Xpark` GitHub releases only. SparkTube releases never notify Open Tube users.

## Pull SparkTube core updates

Do not `git merge upstream`. Port only core files when the maintainer names a SparkTube change, then bump our independent `versionName` / `versionCode` and publish a Project-Xpark release.

After a messy dump that overwrote strings, restore branding:

```bash
# Re-apply Open Tube name, Help Line, and updater URL
./tools/rebrand.sh
```

Do not install a SparkTube APK over this app. The packages are different, so they can coexist. Users update only from Project-Xpark releases.

## Overlay scope

README display name and clone/download links are maintained in this fork's README.

`tools/rebrand.sh` patches:

- launcher / about name (`Open Tube`)
- home header brand + tagline strings
- a few user-facing "SparkTube" strings
- Help Line label + Discord invite
- Gradle `rootProject.name`
- `applicationId` `com.opentubebyproadnan.app`
- `UpdateChecker` URLs to `adnanXmacro/Project-Xpark`

It does not rename Kotlin packages, theme style names, or the crash-log folder.
