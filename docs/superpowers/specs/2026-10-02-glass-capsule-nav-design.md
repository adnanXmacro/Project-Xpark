# Glass capsule bottom nav

Date: 2026-10-02

Status: approved by user in conversation

## Goal

Replace Open Tube's current solid floating bottom bar with the HTML demo's glass capsule nav: frosted pill, sliding highlight bubble, drag-to-scrub, spring snap, and a slight active-icon scale. Keep the four existing tabs (Home / Music / Library / Menu), the user's accent for the selected tab, and theme-aware frost (dark glass in dark, light frost in light). Honor `AppPrefs.animations`.

Ship as a version after `1.2.1` / `14` (next: `1.2.2` / `15`) so a device already on `1.2.1` can Check for updates, exercise the in-app download dialog, and land on a build that has this bar.

## Non-goals

- Changing tab set or tab order
- WebView / embedding the HTML demo
- Material `BottomNavigationView`
- Glass on other chrome (mini-player, app bars, dialogs)
- New test framework
- Google Play
- Renaming Kotlin packages away from `com.sparktube.app`
- Bumping version until implementation is done and the user asks to ship

## Chosen approach

**1 — custom `GlassCapsuleNav` ViewGroup.** One view owns the pill, bubble, drag-to-scrub, spring snap, and icon scale. `MainActivity` only receives a tab-selected callback and keeps fragment show/hide, mini-player, and back-to-Home.

Rejected:

- **2** (keep `LinearLayout`, overlay a bubble): drag/spring would live in `MainActivity`; touch vs click and config-change get messy
- **3** (restyle Material `BottomNavigationView`): cannot live-track the bubble or use the HTML spring without fighting the widget

## User-visible behavior

- Bar is a centered capsule near the bottom: `match_parent` width with 16dp horizontal margins (HTML `100% - 32px`, not the current wrap-content 80dp cells). Pill shape (corner radius = half height).
- Theme-aware frost:
  - Dark: `rgba(22,22,22,0.82)`-class fill, 1px light hairline, inset highlight, drop shadow
  - Light: light frost fill and a dark hairline so it still reads as its own object
- API 31+: `RenderEffect` blur of content behind the bar. Older APIs: translucent themed fill, no live blur.
- Four equal tabs: existing icons and labels (Home, Music, Library, Menu).
- Selected tab: icon and label use `?attr/accent`. Inactive: muted (`nav_inactive`). Active icon scales to ~1.08.
- Highlight bubble sits behind the active tab (absolute, pill-shaped, slightly lighter than the bar, inner highlight). It is not a per-tab background drawable.
- Mini-player stays above the bar (keep ~106dp bottom margin). System back still goes Home, then the bubble moves to Home.

### Motion (Animations on)

- **Tap:** commit that tab immediately (fragment cross-fade starts at once). Bubble springs to it (`cubic-bezier(.34,1.56,.64,1)`, ~380ms). Spring is visual only.
- **Drag on the bar:** bubble live-tracks between tabs; width interpolates; edges rubber-band (~0.22). Nearest tab highlights during the drag. On release, spring-snap to the nearest tab and switch if it changed.
- **First layout / config change / process restore:** bubble jumps to the saved tab with no animation.

### Motion (Animations off)

- Tap snaps the bubble and selection instantly. No drag tracking. Fragment transaction has no custom animation (already the case).

## Components

| Unit | Role |
|------|------|
| `GlassCapsuleNav` | Custom `ViewGroup` in `com.sparktube.app.ui.widget`. Draws pill + bubble; lays out four tab children; handles tap, drag, spring, scale, accent/inactive tints, blur fallback. |
| `activity_main.xml` | Replace `floatingNav` `LinearLayout` with `GlassCapsuleNav` (`@+id/floatingNav`) containing the same four tab ids (`navHome`, `navMusic`, `navLibrary`, `navMenu`). |
| `MainActivity` | Bind `onTabSelected`; keep `select` / `applySelection` / fragment map / mini-player / `EXTRA_INSTALL_UPDATE`. Drive `GlassCapsuleNav.setSelectedTab` on programmatic Home (system back). |
| Drawables / colors | Theme-aware pill fill, border, bubble fill; keep `nav_item_tint` accent selector. Drop unused `bg_nav_item` if nothing else references it. |

`GlassCapsuleNav` does not switch fragments, talk to `PlaybackCenter`, or know tab names beyond its children.

## Data flow

```
User tap or drag-snap on GlassCapsuleNav
  -> onTabSelected(id)
  -> MainActivity.select(id)
       if id == selectedId: return
       selectedId = id
       applySelection()  // existing fragment show/hide

System back while not on Home
  -> MainActivity.select(R.id.navHome)
  -> GlassCapsuleNav.setSelectedTab(navHome, animate = AppPrefs.animations)

Config change
  -> selectedId restored
  -> GlassCapsuleNav.setSelectedTab(..., animate = false)
```

Drag that lands on the current tab does not restart the fragment (`select` already no-ops when `id == selectedId`).

Rapid taps: each tap commits immediately; extra taps on the already-selected tab no-op. A spring already in progress is cancelled and retargeted.

## Visual tokens (from HTML, adapted)

| Token | Dark | Light |
|-------|------|-------|
| Pill fill | ~#161616 at 82% | light frost (~#F5F5F7 at high alpha, current `navbar_bg` family) |
| Pill border | white ~10% | black ~35% (`nav_border`) |
| Bubble | ~#2D2D30 at 95% | slightly darker than pill |
| Inactive icon/label | white ~35–38% / `nav_inactive` | `nav_inactive` |
| Active icon + label | `?attr/accent` | `?attr/accent` |
| Icon size | 24dp (keep current assets) | same |
| Label | 12sp, existing strings | same |
| Bottom offset | 18dp (keep current `layout_marginBottom`) | same |
| Horizontal inset | 16dp | same |

Spring interpolator: `PathInterpolator(0.34f, 1.56f, 0.64f, 1f)`. Duration 320ms on release, 380ms on tap (HTML values).

## Error handling and edges

- Blur unavailable (API < 31, or `RenderEffect` fails): translucent fill, no crash.
- Width too narrow (landscape / small width): four equal flex children still share the pill; labels may ellipsize; bubble width always matches the tab cell.
- RTL: bubble X is computed from child bounds, so layout direction is respected.
- TalkBack: each tab stays a clickable with its label (`contentDescription` = tab string if needed). Drag is extra; tap still works.
- Theme / accent change while sitting on Main: recreate already reapplies theme; bubble snaps with no animation.

## Testing

No new test framework. Manual:

1. Tap each of the four tabs; fragment matches; bubble springs (or snaps if Animations off).
2. Drag across the bar; bubble tracks; release snaps to nearest; fragment matches.
3. Drag that stays on the current tab does not flicker the fragment.
4. Animations off: instant snap, drag does not live-track.
5. Dark and light: frost reads as a pill; selected color follows accent.
6. Mini-player visible: sits above the bar, not under it.
7. System back from a non-Home tab moves bubble to Home.
8. Rotate / recreate: correct tab, bubble not animated.
9. After ship: from installed `1.2.1`, Check for updates → Download update → started-download dialog → notification → install `1.2.2` → new bar is present.

## Shipping

Do not bump version in this spec's implementation phase. When the user asks to ship:

- `versionName` `1.2.2`, `versionCode` `15`
- Signed APK `OpenTube-1.2.2.apk`
- GitHub release `v1.2.2` as latest so `1.2.1` (code 14) sees an update and uses the in-app download + started-download dialog already in `1.2.1`

Keep `applicationId` `com.opentubebyproadnan.app`. Kotlin package stays `com.sparktube.app`.
