# Open Tube branding overlay

This repo is a branded fork of [SparkTube](https://github.com/devfahim00/SparkTube).

Display name: **Open Tube by project Adnan**

Package / `applicationId` stay `com.sparktube.app` so SparkTube merges stay clean.
In-app "Check for update" still reads `devfahim00/SparkTube` GitHub releases.

## Pull SparkTube updates

```bash
# Fetch SparkTube
git fetch upstream

# Merge their main branch
git merge upstream/main

# Restore Open Tube name if the merge overwrote strings
./tools/rebrand.sh
```

Do not install a SparkTube APK over this app. That APK would restore the SparkTube name.
Build Open Tube yourself after each merge.

## Overlay scope

`tools/rebrand.sh` only patches:

- launcher / about name
- a few user-facing "SparkTube" strings
- Gradle `rootProject.name`

It does not rename Kotlin packages, theme style names, crash-log folder, or the updater URL.
