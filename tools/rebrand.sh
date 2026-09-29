#!/usr/bin/env bash
# Re-apply Open Tube branding after merging SparkTube upstream.
# Does not change applicationId, package names, or UpdateChecker URLs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STRINGS="$ROOT/app/src/main/res/values/strings.xml"
SETTINGS="$ROOT/settings.gradle.kts"

python3 - "$STRINGS" "$SETTINGS" <<'PY'
import re
import sys

strings_path, settings_path = sys.argv[1], sys.argv[2]

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
print("Open Tube branding overlay applied.")
PY
