#!/usr/bin/env bash
# Re-apply Open Tube branding after a SparkTube file dump.
# Preserves applicationId, UpdateChecker URLs, Help Line, and display name.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STRINGS="$ROOT/app/src/main/res/values/strings.xml"
SETTINGS="$ROOT/settings.gradle.kts"
MENU="$ROOT/app/src/main/java/com/sparktube/app/ui/menu/MenuFragment.kt"
MENU_LAYOUT="$ROOT/app/src/main/res/layout/fragment_menu.xml"
GRADLE="$ROOT/app/build.gradle.kts"
UPDATER="$ROOT/app/src/main/java/com/sparktube/app/util/UpdateChecker.kt"

python3 - "$STRINGS" "$SETTINGS" "$MENU" "$MENU_LAYOUT" "$GRADLE" "$UPDATER" <<'PY'
import re
import sys

(
    strings_path,
    settings_path,
    menu_path,
    layout_path,
    gradle_path,
    updater_path,
) = sys.argv[1:7]

replacements = {
    "app_name": "Open Tube",
    "home_brand": "Open Tube",
    "home_tagline": "by projectAdnan",
    "about_text": (
        "Open Tube by project Adnan is a lightweight YouTube client for Android, "
        "based on SparkTube. Built with Kotlin and NewPipeExtractor. It shows "
        "country-based video suggestions and never displays live streams. This "
        "project is for educational purposes and is not affiliated with YouTube or Google."
    ),
    "live_not_supported": "Live streams are not shown in Open Tube",
    "download_pair_note": (
        "Downloads the video and audio tracks as a pair — they play together in Open Tube"
    ),
    "menu_join_telegram": "Help Line",
}

text = open(strings_path, encoding="utf-8").read()
for name, value in replacements.items():
    escaped = (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )
    pattern = rf'(<string name="{name}">)(.*?)(</string>)'
    new_text, n = re.subn(pattern, rf"\1{escaped}\3", text, count=1, flags=re.DOTALL)
    if n != 1:
        if name in ("home_brand", "home_tagline"):
            insert = f'    <string name="{name}">{escaped}</string>\n'
            text = text.replace(
                '<string name="app_name">Open Tube</string>\n',
                '<string name="app_name">Open Tube</string>\n' + insert,
                1,
            )
            continue
        raise SystemExit(f"could not patch string: {name}")
    text = new_text
open(strings_path, "w", encoding="utf-8").write(text)

settings = open(settings_path, encoding="utf-8").read()
settings, n = re.subn(
    r'rootProject\.name\s*=\s*"[^"]+"',
    'rootProject.name = "OpenTube"',
    settings,
    count=1,
)
if n != 1:
    raise SystemExit("could not patch settings.gradle.kts")
open(settings_path, "w", encoding="utf-8").write(settings)

menu = open(menu_path, encoding="utf-8").read()
menu = menu.replace("TELEGRAM_URL", "HELP_LINE_URL")
menu, n = re.subn(
    r'https://t\.me/[A-Za-z0-9_]+',
    "https://discord.gg/tY4jGUJ4",
    menu,
    count=1,
)
if "https://discord.gg/tY4jGUJ4" not in menu:
    raise SystemExit("could not patch MenuFragment help URL")
menu = re.sub(
    r'\.setPositiveButton\(R\.string\.github\).*?\.setNegativeButton\(android\.R\.string\.cancel, null\)',
    '.setPositiveButton(android.R.string.ok, null)',
    menu,
    count=1,
    flags=re.DOTALL,
)
open(menu_path, "w", encoding="utf-8").write(menu)

layout = open(layout_path, encoding="utf-8").read()
layout = layout.replace("@drawable/ic_telegram", "@drawable/ic_info")
layout = layout.replace('app:tint="#229ED9"', 'app:tint="?attr/accent"')
open(layout_path, "w", encoding="utf-8").write(layout)

gradle = open(gradle_path, encoding="utf-8").read()
gradle, n = re.subn(
    r'applicationId\s*=\s*"[^"]+"',
    'applicationId = "com.opentubebyproadnan.app"',
    gradle,
    count=1,
)
if n != 1:
    raise SystemExit("could not patch applicationId")
open(gradle_path, "w", encoding="utf-8").write(gradle)

updater = open(updater_path, encoding="utf-8").read()
updater = updater.replace(
    "https://api.github.com/repos/devfahim00/SparkTube/releases/latest",
    "https://api.github.com/repos/adnanXmacro/Project-Xpark/releases/latest",
)
updater = updater.replace(
    "https://github.com/devfahim00/SparkTube/releases",
    "https://github.com/adnanXmacro/Project-Xpark/releases",
)
if "adnanXmacro/Project-Xpark" not in updater:
    raise SystemExit("could not patch UpdateChecker URLs")
open(updater_path, "w", encoding="utf-8").write(updater)

print("Open Tube branding overlay applied.")
PY
