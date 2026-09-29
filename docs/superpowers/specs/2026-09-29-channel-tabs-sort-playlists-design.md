# Channel videos sort and YouTube playlists

Date: 2026-09-29

Status: approved by user in conversation; pending written-spec review

## Goal

First product update after Open Tube 1.0.0: a channel page that behaves like YouTube for browsing. A user who opens a channel can switch **Videos** and **Playlists**, sort Videos as **Latest / Popular / Oldest**, and open a channel playlist to browse and play it.

Ship as independent Open Tube `1.1.0` / versionCode `12`. In-app updater already points at `adnanXmacro/Project-Xpark`, so tagging `v1.1.0` is enough for users to see the update.

## Non-goals

- Shorts, Live, Releases/Albums, or About tabs
- Client-side fake sort of an already-loaded page
- Mixing YouTube playlists into Library / `LocalStore` playlists
- Renaming Kotlin packages away from `com.sparktube.app`
- New test framework
- Google Play
- Automatic git merge from SparkTube

## Why this is manageable

The channel screen (`ChannelActivity`) is videos-only today. `YtRepository.channelVideos` already loads `ChannelTabs.VIDEOS` via NewPipeExtractor 0.26.5.

`ChannelTabs.PLAYLISTS` and `PlaylistInfo` already exist in the vendored jar. SparkTube `main` still has the same videos-only channel screen, so this is an Open Tube feature, not a dump of an upstream screen.

The hard part is Popular / Oldest. `YoutubeChannelTabLinkHandlerFactory.getAvailableSortFilter()` is empty; Latest is whatever YouTube returns for `/videos`. Real YouTube sort must be added in `tools/extractor-patch` (same overlay pattern as the Android-compat jar), not by reordering fetched items.

## Chosen approach

**A — real YouTube sort in the extractor**, plus channel playlists.

Rejected:

- **B** (sort only loaded pages): Popular/Oldest would not match YouTube on large channels
- **C** (stub chips): incomplete first update; user wants all three sorts once Videos and Playlists are both tabs

Future SparkTube core dumps stay cheap because:

- UI and `YtRepository` changes live in our Kotlin, overlay after dump
- Extractor sort lives in `tools/extractor-patch`, rebuilt into `app/libs/newpipeextractor-0.26.5-android-compat.jar` (or the next patched jar name)
- Public extractor APIs stay (`ChannelTabs`, `ChannelTabInfo`, `PlaylistInfo`). Do not fork SparkTube-private types
- If SparkTube later ships the same UI, we take their files and re-apply our overlay (identity, updater, this channel UI if theirs differs)

## Channel screen

Keep the existing header: back, avatar, name, subscriber count, subscribe.

Replace the static "Videos" label with:

1. **Videos | Playlists** — two tabs, Videos selected by default. Same chip/tab visual language as search (`bg_chip`, selected accent).
2. On **Videos** only: **Latest | Popular | Oldest** chips. Default **Latest**. Hidden on Playlists.

Behavior:

- First open: header + Latest videos (current default feed)
- Switch sort: clear the video list and next-page cursor, skeleton, fetch page 1 for that sort
- Switch to Playlists: hide sort chips, load playlist rows (cache the first Playlists page in the activity so a tab switch back does not always refetch)
- Switch back to Videos: restore the current sort list if still held; do not refetch unless empty/error
- Pagination: same RecyclerView infinite scroll as today, per active tab
- Live streams still stripped via `LiveFilter`
- Missing `ChannelTabs.PLAYLISTS` tab: Playlists tab shows the existing empty state, not a crash

## Repository

Extend `YtRepository` (do not add a second YouTube access layer):

- `channelVideos(channelUrl, sort: ChannelVideoSort)` — first page of `ChannelTabs.VIDEOS` with that YouTube sort
- `channelVideosMore(channelUrl, sort, page)` — next page; sort must match the cursor’s tab
- `channelPlaylists(channelUrl)` — first page of `ChannelTabs.PLAYLISTS`
- `channelPlaylistsMore(channelUrl, page)`
- `youtubePlaylist(url)` — `PlaylistInfo.getInfo` for the channel-playlist screen
- `youtubePlaylistMore(url, page)` — `PlaylistInfo.getMoreItems`

`ChannelVideoSort`: `LATEST`, `POPULAR`, `OLDEST`. Latest maps to today’s unsorted videos tab (empty sort filter). Popular and Oldest are the extractor sort ids we add in the patch (lock the exact strings in implementation once the InnerTube params are known; do not invent client-side aliases).

Playlist rows for the channel tab are a small UI model, e.g. `YoutubePlaylistItem(url, name, thumbnailUrl, streamCount)`. Built from `PlaylistInfoItem`. Do not reuse `LocalStore.Playlist` (that type has a local id and saved videos).

`channelInfo` stays as-is for the header.

## Extractor patch

NewPipeExtractor 0.26.5 does not expose channel video sort. Implementation must:

1. Teach the YouTube channel videos tab to request YouTube’s Latest / Popular / Oldest (chips or equivalent InnerTube / `/videos` sort), then paginate with the same sort
2. Keep the existing Android URL-compat patch
3. Stay in `tools/extractor-patch` + rebuilt vendored jar
4. Do not tag `v1.1.0` if Popular/Oldest still return the Latest list (do not ship chips that silently show Latest)

If YouTube or the extractor cannot support a sort for a given channel, the Videos tab stays on Latest and shows the existing friendly error — it does not fake an order.

Do not bump the extractor major version in this pass unless the patch cannot be applied to 0.26.5. Prefer patching 0.26.5 so SparkTube dumps that still vendor 0.26.5 overlay cleanly.

## YouTube playlist screen

New activity `YoutubePlaylistActivity`. Separate from `PlaylistActivity`, which is Library-only (local id, long-press remove).

Shows: back, playlist title, video count, **Play all**, paginated video list (`VideoAdapter`).

- Tap a video: `PlaybackCenter.playPlaylist` from that index, then `PlayerActivity.startResume` (same hand-off as Library playlists)
- Play all: same from index 0
- No long-press remove, no LocalStore writes
- Live items stripped
- Empty / error: same empty and error views as the channel screen

`AndroidManifest` must register the new activity.

## Adapters and layouts

- Channel Playlists tab: new row adapter (thumbnail, title, video count). Do not extend Library `PlaylistAdapter` (`LocalStore.Playlist` + delete)
- Videos tab: keep `VideoAdapter`
- Layout: `activity_channel.xml` tabs + sort chips; new playlist activity layout can copy `activity_playlist.xml` minus the local-only remove UX

## Version, release, updater

| Item | Value |
|------|--------|
| `versionName` | `1.1.0` |
| `versionCode` | `12` |
| Git tag | `v1.1.0` |
| Release APK name | `OpenTube-1.1.0.apk` |

Must be a **signed** release (keystore in `keystore/`, CI `apksigner verify`). Unsigned APKs fail sideload.

Users on 1.0.0 (`com.opentubebyproadnan.app`) in-place update. SparkTube `com.sparktube.app` still does not.

## Errors

- Channel header / videos / playlists fetch fail: existing `Formatters.friendlyException` + error view
- Sort fetch fail: keep Latest selected if the failed sort was not Latest; if Latest itself fails, show error on Videos
- Playlist open fail: error on the playlist screen, back returns to the channel
- Cancellation (leave screen): not an error (`CancellationException` rethrown as today)

## Testing

No new test framework this pass.

Manual before tagging `v1.1.0`:

1. Open a channel that has both videos and playlists
2. Videos default Latest; Popular and Oldest each change the list (not a reorder of the same first page on a large channel)
3. Paginate Videos under each sort and Playlists
4. Open a playlist, Play all, tap a middle video, next/previous follows the playlist
5. Channel with no playlists: empty Playlists tab
6. Subscribe still works
7. Live items do not appear
8. After a simulated SparkTube file dump, re-run branding overlay + extractor patch so the app still builds

## Out of scope follow-ups

- Shorts / Live / Releases tabs
- Saving a YouTube playlist into Library
- Channel search-within-channel
- Extractor major upgrade unrelated to sort
