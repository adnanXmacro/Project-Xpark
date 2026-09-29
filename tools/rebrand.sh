#!/usr/bin/env bash
# Re-apply Open Tube branding after merging SparkTube upstream.
# Does not change applicationId, package names, or UpdateChecker URLs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STRINGS="$ROOT/app/src/main/res/values/strings.xml"
SETTINGS="$ROOT/settings.gradle.kts"
MENU="$ROOT/app/src/main/java/com/sparktube/app/ui/menu/MenuFragment.kt"
MENU_LAYOUT="$ROOT/app/src/main/res/layout/fragment_menu.xml"

python3 - "$STRINGS" "$SETTINGS" "$MENU" "$MENU_LAYOUT" <<'PY'
import re
import sys

strings_path, settings_path, menu_path, layout_path = sys.argv[1:5]

replacements = {
    "app_name": "Open Tube by project Adnan",
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
open(menu_path, "w", encoding="utf-8").write(menu)

layout = open(layout_path, encoding="utf-8").read()
layout = layout.replace("@drawable/ic_telegram", "@drawable/ic_info")
layout = layout.replace('app:tint="#229ED9"', 'app:tint="?attr/accent"')
open(layout_path, "w", encoding="utf-8").write(layout)
print("Open Tube branding overlay applied.")
PY
