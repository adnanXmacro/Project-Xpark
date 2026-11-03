# Quick Picks refresh, playlist share, SD export

Date: 2026-11-03

Status: approved in conversation; self-reviewed

## Goal

One Open Tube update, music-only, with three local features (no server):

1. **Quick Picks refresh** — pull-to-refresh on the Music tab; each refresh shows 8 songs that feel like a YouTube Music Quick Picks shelf (Charts plus the user’s recents / favorites / starred artists), not the same first-8 Charts every time.
2. **Playlist share** — share a music playlist as a QR code and a copyable `opentube://` link so another Open Tube install can import the list (YouTube IDs, no files).
3. **SD export** — from a music playlist, after optional Download all: **Pocket MP3** writes normal playable audio; **Android / Open Tube** writes one `.opentube` package the other phone can load as that playlist, then choose to copy on-device or play from the card.

Ship by extending `MusicFragment`, `LocalStore`, `DownloadCenter`, `YtRepository`, and the music playlist screens. minSdk 24 / targetSdk 34 unchanged. No version bump until the user asks to ship.

## Non-goals

- Video playlists (share, export, download-all)
- Encryption, DRM, or “plays on an MP3 player but hidden from Android Files”
- Hosted share URLs, pastebins, or YouTube playlist upload (needs Google account)
- Related-of-every-recent fetch on Quick Picks refresh
- Changing in-app download paths (still app-private `Music/SparkTube`)
- Making Gallery/Files ignore Pocket MP3 exports
- Mixing music and video stores
- Redesigning Home, search, channel, glass nav, About
- New test framework

## Feasibility

- Quick Picks today are `models.take(8)` of `YtRepository.musicTrending` (YouTube Charts). Music tab has no `SwipeRefreshLayout`. Recents, favorites, starred artists, and Charts already exist locally.
- Music playlists are `music_playlists_json` (`Playlist` + `VideoEntry` with YouTube URLs). No per-playlist share. Custom scheme + QR needs no backend. QR size limits how many IDs we encode; copy/share text can carry the rest.
- Downloads are plaintext m4a in app-private storage. SAF can write a public folder (MP3) or a single `.opentube` file (zip of manifest + audio). A file that any MP3 player can play is always copyable; we do not pretend otherwise. Adopted (device-encrypted) SD cards cannot be read on another phone; portable cards can.

## Chosen approach

**Local only (Approach 1).**

Rejected:

- **Hosted share links** — extra server, accounts, privacy
- **YouTube playlist upload** — Google account, against Open Tube
- **Fake DRM / custom extensions** — breaks MP3 players or is still copyable on Android

---

## 1. Quick Picks

### Refresh

Wrap the Music tab `NestedScrollView` in `SwipeRefreshLayout` (same pattern as Home). Pull-to-refresh runs `load()`. The existing Trending genre tile still calls `load()`.

`onResume` does not reshuffle if shelves already have items (avoid a new set every tab switch). First open still loads as today.

### Pool

On each `load()`:

1. Fetch `YtRepository.musicTrending(country)` (Charts, fallback `"top songs"`). Live items stay filtered.
2. Build a candidate pool, deduped by `url`:
   - all Charts items
   - `LocalStore.musicHistory`
   - `LocalStore.musicFavorites`
   - Charts items whose `uploader` matches a starred artist (`favorite_artists_json`)
3. Drop candidates whose YouTube video ID is in the **last-shown ring** (up to 16 IDs in `AppPrefs`, comma-separated).
4. Shuffle the remainder and take 8 → Quick Picks.
5. If fewer than 8 remain, fill from Charts (including last-shown) so the shelf is never empty.
6. Append the new 8 IDs to the ring (cap 16, oldest drop off).

Empty history / no favorites → shuffle Charts only (still a new 8 vs last-shown).

Charts fetch failure: keep the previous Quick Picks (and other shelves); toast. Do not blank the tab.

### What does not change

- **Trending now** = Charts `drop(8).take(12)` in Charts order (not shuffled).
- **Popular** = Charts `drop(20).take(12)` in Charts order.
- Mix cards, mood chips, radio-on-single-tap, Play all = closed 8-track queue.
- No `RecommendEngine` and no per-track `related` calls on refresh.

---

## 2. Playlist share (QR + link)

Music playlists only. Entry points on `MusicPlaylistActivity` (overflow) and `MusicPlaylistsActivity` (item overflow): **Share playlist**.

### Payload

No server. Custom URI:

```
opentube://playlist?n=<utf8-urlencoded-name>&v=<id1,id2,...>
```

- `n` — playlist name (trimmed; empty → `Shared playlist`)
- `v` — YouTube video IDs in playlist order, comma-separated, no `watch?v=` wrapper
- IDs extracted from each item `url` (`v=` / `youtu.be/` / already-id)

Caps:

- **QR** encodes at most the **first 80** IDs (fits a scannable QR). If the playlist is longer, the QR screen subtitle says the QR has the first 80 of N; copy/share still has all.
- **Copy link / share sheet** encodes **all** IDs up to `MAX_PLAYLIST_VIDEOS` (500). Clipboard and `ACTION_SEND` `text/plain`.

Share sheet body: playlist name on line 1, URI on line 2. We do not force the other device to open anything.

### QR screen

Small activity: QR bitmap, name, **Copy link**, **Share**, **Close**. Encode with ZXing `core`. Scan uses `com.journeyapps:zxing-android-embedded` (includes `core`; CaptureActivity). No extra UI chrome.

### Receive

1. **Intent-filter** on a dedicated `PlaylistImportActivity`: `scheme=opentube`, `host=playlist`, `VIEW`. If Open Tube is installed and the OS treats the URI as a link, the app opens and imports. Do not fold this into `YoutubeLinkActivity`.
2. **Scan playlist** on `MusicPlaylistsActivity` (toolbar overflow). Runtime `CAMERA`. Denied camera → snackbar pointing at **Paste playlist link**.
3. **Paste playlist link** — dialog; accept `opentube://playlist?...`. Invalid / empty `v` → toast, no playlist created.

Chat apps often leave `opentube://` as plain text. Copy + QR + paste cover that. We do **not** register a fake `https://` domain we do not own.

### Import

- Create via `createMusicPlaylist(name)`. If the name exists (case-insensitive), use `Name (2)`, then `(3)`, …
- For each ID, `addToMusicPlaylist` with `url=https://www.youtube.com/watch?v=<id>`, `isMusic=true`. Title starts as the video id; uploader/thumb/duration empty. First playback may fill metadata later. Do not block import on network.
- Items stream until the user downloads. This path never copies audio files.

---

## 3. SD export (music only)

### Download all

On `MusicPlaylistActivity`: **Download all**. Enqueues `DownloadCenter` AUDIO jobs for items that are not already `STATUS_DONE` AUDIO. Quality is the first AUDIO option `DownloadSheet` would offer for that stream (same as a one-tap audio download). Toast `Downloading x of y`. Failures do not delete already-done files. The user stays on the playlist screen; there is no blocking wait dialog.

### Export entry

**Export** on the same screen. If any item is not a done AUDIO download, dialog:

- **Download remaining** — start Download all, toast to tap Export again when downloads finish
- **Export downloaded only** — continue with files that exist
- **Cancel**

Empty playlist → toast, no picker.

Then format picker (two options, not a third):

### A. Pocket MP3

SAF `ACTION_OPEN_DOCUMENT_TREE` (user picks the SD card or any folder). Write:

- `01 Title.m4a`, `02 Title.m4a`, … (sanitized titles, copy of the app-private m4a)
- `playlist.m3u` listing those filenames in order

No `.nomedia`. Any player can play them; Files and PCs can see them. That is the honest MP3-player path.

### B. Android / Open Tube package

SAF `ACTION_CREATE_DOCUMENT` with mime `application/octet-stream` and suggested name `{sanitizedPlaylistName}.opentube` (user can pick the SD card).

`.opentube` is a **zip**:

```
manifest.json
audio/<videoId>.m4a
```

`manifest.json`:

```json
{
  "app": "OpenTube",
  "kind": "music-playlist",
  "version": 1,
  "playlist": {
    "name": "My mix",
    "items": [
      {
        "url": "https://www.youtube.com/watch?v=…",
        "title": "…",
        "uploader": "…",
        "thumb": "…",
        "duration": 210,
        "music": true,
        "file": "audio/<videoId>.m4a"
      }
    ]
  }
}
```

`file` is omitted when that track was not downloaded. Other Open Tube still imports the row and can stream it.

Not written under public `Music/`. Gallery and other music apps do not index `.opentube`. Files may show the single package; that is expected.

In-app downloads stay in app-private storage. This export copies bytes into the zip; it does not move the originals.

**Portable SD only.** Adopted (encrypted-to-device) cards cannot be read on another phone. If the write target is adopted, toast that the other device will not see it.

### Load on the other phone

- **Load from SD** on `MusicPlaylistsActivity` (SAF `ACTION_OPEN_DOCUMENT`, `*/*`; accept files whose name ends in `.opentube`). No `MANAGE_EXTERNAL_STORAGE`. No auto-scan of the card on resume.

On a valid package, prompt:

> This playlist is on an SD card. Store it on this device so it plays without the card?

- **Store on device** — copy each `audio/*.m4a` into app-private `Music/SparkTube/` (same naming as `DownloadCenter`), register `DownloadRecord` `STATUS_DONE` AUDIO, `createMusicPlaylist` + items. Persist the source URI only for debugging; playback does not need the card.
- **Play from card** — take persistable URI permission on the `.opentube`. Create the music playlist. Register a `DownloadCenter` overlay map `url -> (packageUri, zipEntry)` so playback extracts that entry to `cache/sdplay/<playlistId>/<videoId>.m4a` on demand and plays the cache file. Missing `file` keys stream from YouTube. On `MusicPlaylistsActivity` resume, if the package URI is unreadable, clear that overlay and the cache folder, toast that the card is gone; playlist rows stay (they can still stream).
- **Cancel** — no playlist created.

Duplicate names: same ` (2)` suffix as share import.

Corrupt / wrong `kind` / zip error: toast, no crash.

---

## Architecture

Three isolated units. No new backend.

| Unit | Owns | Depends on |
|---|---|---|
| Quick Picks | Shuffle + last-shown ring | `MusicFragment`, `YtRepository.musicTrending`, `LocalStore` history/favorites/artists, `AppPrefs` |
| PlaylistShare | Encode/decode URI, QR bitmap, scan/paste, intent-filter | `LocalStore` music playlist APIs, ZXing `core` + `zxing-android-embedded` |
| SdExport | Download all, MP3 tree write, `.opentube` zip read/write, load prompt | `DownloadCenter`, `LocalStore`, SAF |

`PlaybackCenter` is unchanged except it already resolves local files via `DownloadCenter` by URL. Store-on-device registers downloads so that path works. Play-from-card must resolve the SD/zip path for those URLs (a small lookup map in `DownloadCenter` or `LocalStore`, cleared when the card is gone).

### Data flow

```
Quick Picks
  pull-to-refresh -> musicTrending + local pool - last16 -> shuffle 8 -> bind
  persist last-shown IDs in AppPrefs

Share
  playlist items -> video IDs -> opentube://playlist?n=&v=
  QR (max 80) / copy / ACTION_SEND
  receiver -> createMusicPlaylist + addToMusicPlaylist

SD
  Download all missing AUDIO
  Pocket MP3 -> SAF tree -> numbered m4a + m3u
  Android -> zip manifest + audio/*.m4a
  other phone Load -> prompt copy vs play-from-card
```

---

## Error handling

- Charts fail: keep previous Quick Picks; toast
- Pool smaller than 8: fill from Charts including last-shown
- Invalid / empty share URI: toast; no playlist
- Camera denied: paste-link fallback
- QR of a 81+ playlist: encode first 80; tell the user; copy has all
- Duplicate playlist name: `Name (2)`
- Download all partial fail: remaining stay missing; export dialog still offered
- SAF cancel: no write
- Adopted SD: toast that the other phone will not see it
- Corrupt `.opentube`: toast
- Play-from-card after eject: toast; playlist rows remain (stream if URL works)
- Share does not copy files; user who wanted files uses SD export

---

## Testing

- `./gradlew assembleDebug` — `BUILD SUCCESSFUL`
- Music pull-to-refresh: second pull is not the same 8 when Charts has >8 items; last-shown exclusion works; empty history still fills from Charts
- Trending now / Popular still Charts order; mixes/moods unchanged
- Share QR + copy; import on a second install (or paste on the same device) creates a music playlist, not a video one; names suffix on collision
- Intent `opentube://playlist?n=Test&v=dQw4w9WgXcQ` imports
- Download all only AUDIO, skips already-done
- Pocket MP3 folder contains numbered m4a + m3u that a generic player can open
- `.opentube` is a zip with `manifest.json` and `audio/`; Gallery does not treat it as a song
- Load from SD: Store on device plays after “eject” (files in app-private); Play from card fails after eject with a toast
- Video playlists have no Share/Export/Download all
- Existing downloads, radio, sleep timer, backup still work
