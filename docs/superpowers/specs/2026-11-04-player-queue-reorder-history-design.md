# Now Playing fade, Play next, playlist reorder, changelog, version history

Date: 2026-11-04

Status: approved in conversation; self-reviewed

## Goal

One Open Tube update with five local features:

1. **Cover fade** on Now Playing: blurred album art fills the screen behind the square cover and fades into the existing background (YouTube Music style).
2. **Play next** on every Music song overflow: insert after the current track; not saved to a playlist.
3. **Drag-to-reorder** songs inside a music playlist.
4. **Update popup** always lists what that version added / improved / fixed (built-in notes when GitHub notes are empty or noise).
5. **Version history** in More: a screen of every version we have notes for.

minSdk 24 / targetSdk 34 unchanged. No version bump until the user asks to ship.

## Non-goals

- Video playlists, video Play next, or reordering the playing queue
- Extra image libraries (no Coil transformations artifact)
- Handle icon on playlist rows
- Network fetch of GitHub release notes for Version history
- Changing Later / Download on the update dialog
- Redesigning Home, glass nav, About body, or video player

## Feasibility

- Now Playing is `activity_now_playing.xml`: square `art` inside `artContainer`, solid `bg_nowplaying_bg`. Coil 2.6.0 already loads hi-res covers (`Thumbs.loadHiRes`). API 31+ `RenderEffect` blur; below that a small stack-blur on a downsampled bitmap. No new Gradle dependency.
- Video already has `PlaybackCenter.enqueueVideo`. Music has no insert-after-current. Queue is `PlaybackCenter.queue` + `queueIndex`. Radio and Play all already use that list.
- Music playlists are `LocalStore` JSON. `MusicPlaylistActivity` long-press currently **removes**. `ItemTouchHelper` long-press starts a drag, so remove moves to the row overflow.
- `UpdatePrompt` already renders `Changelog.parse` then `fallback` when parse is empty. GitHub auto-bodies often parse to a non-empty but useless list, so the fallback never runs. Fix: require at least one real bullet, else fallback.
- `Changelog.fallback` already has notes for 1.2.3 through 1.2.13. Version history is that list, newest first, no network.

## Chosen approach

**Local only.** Rejected: Palette-only wash (user picked blurred cover), Play-next-at-end (user picked insert-after-current), video playlist reorder, replacing Check for update.

---

## 1. Cover fade

`activity_now_playing.xml`: full-screen `ImageView` (`artBackdrop`) behind all content, `scaleType=centerCrop`, `importantForAccessibility=no`. Keep the square `art`.

`NowPlayingActivity.loadArt` loads the same URL into both views. Backdrop: downsample, blur (RenderEffect API 31+; stack-blur on a ~64px bitmap below that), dim (~40% black scrim). A vertical gradient overlay (`artScrim`) from transparent at the top to `bg_nowplaying_bg` at the bottom so the blur melts into the existing gradient. Square cover unchanged (rounded, elevated).

Empty / missing thumb: hide backdrop, leave the current solid background.

---

## 2. Play next

`PlaybackCenter.playNextMusic(entry)`:

- If **music is already playing** (`mode == AUDIO` and queue is live): insert at `queueIndex + 1` in `PlaybackCenter.queue`. Music already advances from that list, so no ExoPlayer splice is required. Radio stays on; the inserted song plays next, then the rest of the radio. Do not open Now Playing; toast only.
- If **nothing is playing**: start that song as music (`playMusic(entry, radio = false)`) and open Now Playing as a normal play would.
- If **video is playing**: Music Play next always wins — stop video and start this song as music (`playMusic(entry, radio = false)`), then open Now Playing. Further Play next calls insert after it.
- Duplicates allowed (replay next is valid).
- Does not write to any playlist, favorites, or history until the song actually plays.

### Menus (Music only)

Shared helper `MusicSongMenu.show(anchor, model)` so every overflow is the same:

| Screen | Today | Add |
| --- | --- | --- |
| `MusicFragment` Quick Picks / Popular | Play, artist, playlist, star | **Play next** first |
| `SearchActivity` music | Star only | **Play next**, keep star |
| `MusicListActivity` favorites / downloads | Overflow = add to playlist | **Play next**, then add to playlist |
| `MusicPlaylistActivity` | Long-press = remove | Overflow: **Play next**, Remove. Drag = reorder (section 3) |
| `ArtistActivity` | No overflow | Overflow: **Play next** |

Tap-row still plays (radio from home/search/artist; playlist order from a playlist). Play next from a playlist row does not change that playlist's saved order.

Video `VideoMenu` "Add to queue" is unchanged.

---

## 3. Playlist reorder

Music playlists only (`MusicPlaylistActivity`).

- `ItemTouchHelper` UP/DOWN. Long-press on a row starts a drag; drop writes the new order.
- `LocalStore.reorderMusicPlaylist(context, id, from, to)` rewrites `items` in that playlist JSON. Play all / share / export / download-all read that order.
- **Remove** moves off long-press (conflicts with drag) onto the row overflow (`Remove from playlist` + confirm dialog, same copy as today).
- No drag handle. No reorder on video playlists or Up next.

---

## 4. Update popup

`UpdatePrompt.show`:

1. `parsed = Changelog.parse(release.body)`
2. If `parsed` has **no non-blank bullets**, use `Changelog.fallback(activity, version)` instead.
3. Render as today (Added / Improved / Fixed headings + bullets).
4. Later / Download unchanged.

`Changelog.parse` also drops leftover "Full Changelog" / compare-URL-only sections so auto-generated GitHub bodies count as empty.

This is why users see a blank catalog: parse returns a junk non-empty list, fallback never runs.

---

## 5. Version history

Menu: new card **Version history** immediately under **Check for update** (before Telegram / About). Chevron, same card style.

`VersionHistoryActivity`: toolbar back + title. Scroll of versions **newest first**. For each version string that `Changelog.fallback` knows (currently 1.2.13 … 1.2.3): heading `Open Tube {version}`, then the same section/bullet rendering as the update dialog. Built-in strings only. Tapping a version does not install. Unknown versions are omitted, not a generic blurb.

`Changelog.knownVersions()` returns that ordered list so Menu and the popup share one source.

---

## Error handling

- Blur failure: skip backdrop, keep solid background.
- Play next with a bad URL: same as play (error toast when it actually starts).
- Reorder drop on the same index: no write.
- Version history: always local; never depends on GitHub.

## Testing (manual)

- Now Playing: cover stays sharp; background is a blurred fade of the same art; missing art = old solid look.
- Play next while a song plays: that song is next in Up next; current song is not interrupted.
- Play next with player idle: song starts.
- Search / Quick Picks / playlist / artist / favorites / downloads all show Play next.
- Drag two songs in a music playlist; reopen; Play all follows the new order.
- Long-press no longer deletes; Remove is in the overflow and still confirms.
- Update popup on a GitHub auto-body shows the built-in bullets for that tag.
- Menu → Version history lists 1.2.13 down to 1.2.3 with notes.

## Files (expected)

- `activity_now_playing.xml`, `NowPlayingActivity.kt`, `Thumbs.kt` (blur helper)
- `PlaybackCenter.kt` (`playNextMusic`)
- `MusicSongMenu.kt` (new), call sites above
- `LocalStore.kt` (`reorderMusicPlaylist`), `MusicPlaylistActivity.kt`, `MusicRowAdapter.kt` if needed
- `Changelog.kt`, `UpdatePrompt.kt`
- `fragment_menu.xml`, `MenuFragment.kt`, `VersionHistoryActivity.kt` (new), `AndroidManifest.xml`, `strings.xml`
