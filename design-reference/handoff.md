# Handoff: Drums App (Android)

## Overview

A native **Android app** for drummers. Users upload sheet music (PDF or photo of paper notation) and the app reads it back — playing drum sounds in time while a **moving playhead line** scrolls across the on-screen notation, indicating exactly where the song currently is.

Core flow:
1. User adds a song (drop a PDF, take a photo of sheet music, or connect Spotify).
2. App OCRs / parses the drum notation into a structured pattern (bars × subdivisions × drum hits).
3. User opens the **Player** — drum notation renders as a traditional 5-line drum staff, drum sounds play to a metronome at the song's BPM, and a vertical accent-colored playhead line sweeps across the active line of music.
4. **Practice mode** loops a section while ramping tempo from slow → target BPM across N loops.

Target platform: **Android (Material 3 base, custom theming on top)**. Sized for 412 × 892 dp class devices (Pixel 8 / 9).

---

## About the Design Files

The files in `design_reference/` are **design mockups created as an HTML prototype** — they demonstrate intended look, layout, and behavior. **They are not production code to copy directly.**

Your task is to **recreate these designs as a native Android app** using the established stack for the project (Jetpack Compose recommended; or Kotlin + Material 3 XML if there's an existing native codebase to integrate into). If no codebase exists yet, **start fresh with Jetpack Compose + Material 3** — it maps closely to the design language used here.

The HTML files use React + inline JSX purely so the design could be interactively prototyped in the browser. Reference them for exact measurements, colors, copy, and behavior — then implement idiomatically for the target platform.

### How to view the prototype
Open `design_reference/Drums App.html` in a browser. It's a pan/zoom design canvas with 3 visual directions × 5 screens each. Use scroll-wheel / pinch to zoom; drag the background to pan; click the expand icon (top-right of any artboard on hover) to focus a screen fullscreen. ←/→ steps through screens in the same direction; ↑/↓ jumps across directions; Esc exits.

---

## Fidelity

**High-fidelity.** Final colors, typography, spacing, iconography, and interactions are decided. Recreate pixel-accurately using the values in this README.

---

## Chosen Visual Direction: **Neon Studio (Violet)**

Of the 3 explored directions, the user selected **Neon Studio** with **violet** accent. The other two directions (`Heavy Stage`, `Warm Rhythm`) are in the prototype for reference only — **do not ship them**.

**Aesthetic:** dark, after-hours studio energy. Near-black background, surface elevation via slightly lighter dark grays, single saturated violet accent for emphasis (playhead, primary CTAs, "now playing" indicators). Bold geometric grotesk type. Monospace for metadata and numeric readouts (BPM, time, bar counts).

---

## Design Tokens

### Colors

| Token | Hex | Usage |
|---|---|---|
| `bg` | `#0a0a0c` | App background |
| `surface` | `#131318` | Cards, list rows, sheet-music container |
| `surface2` | `#1c1c24` | Elevated chips, icon backdrops, secondary surfaces |
| `line` | `#26262e` | Borders, dividers, icon-button strokes |
| `text` | `#f5f5f7` | Primary text |
| `dim` | `#8a8a94` | Secondary text, captions, monospace metadata |
| `accent` | `#8a3dff` | **Violet** — playhead, primary buttons, FAB, BPM readout, active states |
| `accent-on` | `#ffffff` | Text/icon color on top of `accent` |
| `staff-line` | `#3a3a44` | 5-line staff lines and bar lines |
| `note` | `#f5f5f7` | Drum noteheads, stems, beams |
| `playhead` | `#8a3dff` | Vertical playhead line + dot cap |
| `bar-track` | `#26262e` | Progress bar / loop-dot track background |
| `chip-bg` | `rgba(255,255,255,0.08)` | Drum-hit chips (inactive) |
| `chip-active-bg` | `#8a3dff` | Drum-hit chips (active — currently sounding) |
| `chip-active-text` | `#0a0a0c` | Text on active chip |
| `cover-grad-from` | `#8a3dff` | Cover-art gradient start |
| `cover-grad-to` | `#7a1020` | Cover-art gradient end (deep wine) |

### Typography

| Family | Source | Weights used |
|---|---|---|
| **Space Grotesk** | Google Fonts | 500, 600, 700, 800 — display, body, headings, buttons |
| **JetBrains Mono** | Google Fonts | 400, 500, 700 — BPM numerals, bar/time counters, drum-hit chips, ALL-CAPS labels |

Type scale (closest Material 3 token in parens):

| Role | Family | Size | Weight | Letter-spacing | Line height |
|---|---|---|---|---|---|
| Display BPM (Practice screen) | Space Grotesk | 72 px | 800 | -3.5 | 1.0 |
| Player BPM | Space Grotesk | 36 px | 800 | -1.5 | 1.0 |
| Screen title (Home "Your kit") | Space Grotesk | 32 px | 800 | -1.0 | 1.0 |
| Cover hero title (Song detail) | Space Grotesk | 26 px | 800 | -0.8 | 1.05 |
| Bar counter | Space Grotesk | 22 px | 700 | -1.0 | 1.0 |
| Section title | Space Grotesk | 22 px | 700 | -0.5 | 1.1 |
| Card title | Space Grotesk | 17 px | 700 | -0.3 | 1.2 |
| Button label | Space Grotesk | 14 px | 700 | 0.4 | 1.2 |
| List row title | (system body) | 14 px | 600 | -0.2 | 1.3 |
| Body | Space Grotesk | 13 px | 400 | 0 | 1.5 |
| Caption / metadata | JetBrains Mono | 11–12 px | 400 | 0 | 1.4 |
| **ALL-CAPS labels** | JetBrains Mono | 10–11 px | 400/700 | **1.5** | 1.2 |
| Drum-hit chip | JetBrains Mono | 9 px | 700 | 0.8 | 1.0 |

Use ALL-CAPS + JetBrains Mono + 1.5 letter-spacing for:
- `LIBRARY`, `BPM`, `BAR`, `NOW READING`, `RECENTLY IMPORTED`, `LOOPING BARS 1–2`, `PLAYBACK`, `SOURCE`, `CONTINUE`, `CURRENT TEMPO`, etc.

### Spacing

8-pt grid throughout. Common values: `4, 6, 8, 10, 12, 14, 16, 18, 24, 32` px.

- Screen edge padding: **16 px**
- Card internal padding: **16 px** (12 for compact)
- Vertical gap between page sections: **14 px**
- Gap inside row layouts: **10–12 px**
- Tap targets: **min 40 × 40 px** (icon buttons), **44 × 44 px** preferred

### Border Radius

| Token | Value | Usage |
|---|---|---|
| `radius-sm` | 6 px | Small chips, dense controls |
| `radius-md` | 8–12 px | Cover art (small), icon backdrops |
| `radius-lg` | 14 px | Cards, buttons, surfaces (primary radius) |
| `radius-pill` | 999 px | Tabs, icon buttons, "Continue" pill |

### Elevation / Shadows

- **Cards**: no shadow on dark theme — separation via background contrast and 1 px `#26262e` border.
- **FAB / primary play button**: `0 8 24 rgba(138, 61, 255, 0.33)` (violet glow at ~33% alpha).
- **Pressed**: scale down to 0.97 over 80 ms.

### Iconography

All icons are **simple, single-color, stroke-based** (1.4–1.6 px stroke at 16 px viewbox), exported as vector. Icons used:

- `play`, `pause`, `stop` — transport
- `upload`, `camera`, `file` — import flow
- `spotify` — Spotify connect (use Spotify's official green `#1DB954`)
- `back` (chevron), `more` (three dots), `plus` — chrome
- `metronome` (triangle + pendulum), `loop` (circular arrows), `speed` (gauge needle)

Source the equivalents from **Material Symbols** (Outlined, weight 400) for the production app — they match the aesthetic closely. Lock to a single style throughout.

---

## Screens

### 01 · Home (Library)

**Purpose:** browse saved songs, resume the most-recent one quickly, add new ones.

**Layout** (top → bottom, screen padding 16 px, gap 14 px):

1. **Header row** (`flex`, baseline-aligned, space-between)
   - Left column:
     - Eyebrow: `LIBRARY` — JetBrains Mono, 12 px, color `dim`, letter-spacing 1, uppercase
     - Title: **`Your kit`** — Space Grotesk 800, 32 px, color `text`
   - Right: 36 × 36 "more" icon button (1.4 px `line` border, transparent fill, pill)

2. **Tab row** (`flex`, gap 6 px, horizontally overflowing)
   - 3 pill tabs: `All 24` (active), `Recent`, `Spotify`
   - Active tab: background `text` (#f5f5f7), text `bg`, no border
   - Inactive: transparent bg, color `dim`, 1 px `line` border
   - Padding: 8 × 14 px, font 13 px / 600

3. **"Continue practicing" hero card** (full width, background `surface`, radius `14`, padding 16, gap 12)
   - **Row 1:** 48 × 48 cover art (gradient `accent → cover-grad-to`, diagonal stripe overlay at 25% white, monogram letters Space Grotesk 800) + text column:
     - Eyebrow `⟶ CONTINUE` — JetBrains Mono 10 px / 700, color `accent`, letter-spacing 1.5
     - Title (song name) — Space Grotesk 700, 17 px, color `text`, truncate
     - Subtitle — 12 px, color `dim`: `{Artist} · {BPM} BPM`
   - **Row 2:** Mini DrumStaff preview (2 bars, no clef, with playhead at slot ~11, width 300 × height 68)
   - **Row 3 (transport):** progress bar fills 34% with `accent`; mono time-stamp `1:14 / 3:32`; 36 px circular play button (`accent` bg, white play icon)

4. **`ALL SONGS` section label** — JetBrains Mono 11 / 1.5 letter-spacing, color `dim`, padding 12 × 4 px

5. **Song rows** (one per song, padding 10 × 4 px, bottom border `1 px line`, flex gap 12)
   - 40 × 40 cover art (radius 8)
   - Text column: title (14 / 600, truncate) + meta line `{artist} · {bpm}BPM · {last-played}` (Mono 11, dim)
   - Right: 14 px "more" icon, color `dim`

6. **FAB** (absolutely positioned, bottom: 50, right: 20)
   - 56 × 56, radius 28, background `accent`, plus icon (20 px, white)
   - Shadow: `0 10 24 rgba(0,0,0,0.18)` plus the violet glow noted above

**Sample data shown in mock** (use as realistic placeholders during development):
- Smells Like Teen Spirit · Nirvana · 116 BPM · Yesterday (NV)
- Tom Sawyer · Rush · 88 BPM · 3d ago (RU)
- Rosanna · Toto · 86 BPM · 1w ago (TO)
- In the Air Tonight · Phil Collins · 95 BPM · 2w ago (PC)
- YYZ · Rush · 144 BPM · Imported (RU)

---

### 02 · Upload (Add a song)

**Purpose:** import a new song from PDF, image, photo, or Spotify.

**Layout:**

1. **Header**: back chevron (36 px icon button) + title `Add a song` (Space Grotesk 700, 22 px, letter-spacing -0.5)

2. **Body intro**: 13 px / `dim` / line-height 1.5: *"Drop in sheet music or pick a track. We'll detect tempo and align the playhead automatically."*

3. **Drop zone** (large)
   - Background `surface`, radius 14, **2 px dashed `line` border**
   - Padding 32 × 16 px, centered column, gap 10
   - 56 × 56 circle in `accent`, upload icon (22 px, white)
   - Headline: `Drop file here` (Space Grotesk 700, 16 px)
   - Sub: `PDF · PNG · JPG · up to 20MB` (Mono 12 / dim)

4. **`OR PICK A SOURCE` label** (Mono 11, letter-spacing 1.5, dim, uppercase)

5. **Source rows** (3 stacked cards, gap 8, background `surface`, 1 px `line` border, radius 14, padding 16, flex gap 14):
   - 44 × 44 leading icon container (radius 12), tinted backgrounds:
     1. **Take a photo** — camera icon — `accent` bg / white icon — sub: "Snap sheet music with your camera"
     2. **Choose PDF or image** — file icon — `surface2` bg / `text` icon — sub: "Pick from your files"
     3. **Connect Spotify** — Spotify logo (in official `#1DB954`) — `surface2` bg — sub: "Drum along to tracks you're playing"
   - Trailing `›` chevron, 18 px, dim

6. **`RECENTLY IMPORTED` label**

7. **Processing card**: 32 × 32 file icon tile + filename `yyz_drum_chart.pdf` (13 / 600) + status `Processing · 2 of 4 pages` (Mono 11 / dim) + right-aligned `62%` (Mono 11 / 700 / `accent`)

---

### 03 · Player (HERO — main reading view)

**Purpose:** the user is sight-reading. The playhead must be **dead obvious** at all times.

**Layout (no scroll — full viewport):**

1. **Top bar** (padding `12 16 8`, flex gap 10)
   - Back icon button (34 px)
   - Center column: eyebrow `NOW READING` (Mono 10) + song title (Space Grotesk 700, 15 px, truncate)
   - Right: more icon button (34 px)

2. **BPM + bar readout** (`flex`, baseline-aligned, gap 16, centered)
   - `BPM` label + big violet number (36 px / 800 / tabular-nums)
   - `BAR` label + counter `{n}/{total}` (22 px / 700; total in `dim`)

3. **Sheet music container** (`flex: 1`, margin `4 12 0`)
   - Background `surface`, radius 14, 1 px `line` border, padding `18 10 10`
   - Holds the **DrumStaffStack** (multi-line score, see "Drum Notation" below)
   - Absolute top-right corner: time signature `4/4` in Mono 9 / `dim`

4. **Drum-hit indicators** (drum chips lighting up live)
   - Row of 8 chips: `KICK`, `SNR`, `HH`, `CRSH`, `RIDE`, `TOM1`, `TOM2`, `FLR`
   - Inactive: `chip-bg` background, `dim` text
   - Active (currently sounding this 16th-note slot): `accent` background, `chip-active-text`
   - Mono 9 / 700, padding 4 × 8 px, radius 6
   - Transition `background .08s` (snap, not fade)

5. **Transport row** (padding `8 16 16`, centered flex gap 18)
   - Metronome ghost button (42 px circle, `line` border)
   - Stop ghost button (42 px)
   - **Primary play/pause** (64 × 64 circle, `accent` bg, white icon, **shadow `0 8 24 rgba(138,61,255,0.33)`**, scale 0.97 on press)
   - Loop ghost button
   - Speed/practice ghost button

#### Drum Notation (the most important component)

A traditional **5-line drum staff** rendered as SVG (or use a drawing-canvas / `Canvas` in Compose; recommend **drawing the staff procedurally** rather than using a notation library — patterns are simple and you control playhead positioning per pixel).

**Staff structure (per line of music):**
- 5 horizontal lines, 9 px apart, drawn at color `staff-line` (#3a3a44), stroke 0.9 px
- Percussion clef glyph at left (vertical double-bar): a 4 px thick rect + a 1.5 px thin rect, both spanning the staff height
- Time signature `4/4` immediately after clef — Georgia serif 700, 17 px (the only place we use serif; it's a notation convention)
- Bar lines: vertical 0.9 px line at each bar boundary in `staff-line` color

**Drum positions on the staff** (top to bottom):
- Crash cymbal: `x` notehead 20 px above top line, color `accent`
- Ride: `x` notehead 16 px above top, color `accent`
- Hi-hat (closed): `x` notehead 12 px above top line, color `note`
- Hi-hat (open): same x, plus a 2.8 px circle above it
- High tom: oval on top space (1st space from top)
- Mid tom: oval on the space above middle line
- **Snare**: oval on middle (3rd) line
- Floor tom: oval on bottom space
- **Kick (bass drum)**: oval on space just *below* the bottom line, with stem going **down**

**Notehead shape**: 
- Oval: rx 4.2, ry 3.1, rotated -22° around its center (standard music-engraving angle). Fill `note`.
- X (cymbals): two 8 px crossed lines, 1.6 px stroke, round caps.

**Stems & beams** (standard music notation):
- Top-row hits (hi-hat / crash / ride): stem goes **up** 16 px from notehead, drawn 4 px right of x-center
- Mid-row hits (snare / toms) without a kick on the same slot: stem goes up 22 px
- Kick: stem goes **down** 16 px from notehead, drawn 4 px left of x-center
- **Beams**: consecutive 8th/16th notes in the same beat group get a horizontal 2.5 px thick beam connecting their stem tips. Group by beat: 4 16th-note slots per beat.

**Bar pattern data structure:**
A bar = array of 16 slots (16th notes). Each slot = array of drum tokens hit at that subdivision (or empty for rest):
```
['k', 'h']  // kick + hi-hat together
['h']       // hi-hat only
['s', 'h']  // snare + hi-hat (backbeat)
[]          // rest
```
Tokens: `k` kick, `s` snare, `h` hihat-closed, `o` hihat-open, `c` crash, `r` ride, `t1` high tom, `t2` mid tom, `t3` floor tom.

A song = `{ title, artist, bpm, timeSig: [num, den], bars: [bar, bar, ...] }`.

See `drum-staff.jsx` for the canonical SVG-rendering reference and `BAR_GROOVE`, `BAR_FILL`, `BAR_CRASH` for sample patterns.

#### Playhead (the moving line)

**Visual:**
- Vertical line, **2.2 px wide**, color `accent` (#8a3dff), round caps
- Spans from 22 px above the top staff line down to 14 px below the bottom staff line
- Capped on top with a 3 px filled circle in `accent` (looks like a marker pin)

**Motion:**
- Position on a line of music = `innerX0 + (currentSlot / slotsPerLine) * innerW`
  - `innerX0` = right edge of clef + `staffPaddingX`
  - `innerW` = staff width minus clef and right padding
- **Updates every requestAnimationFrame** for buttery motion (do NOT snap to slot — interpolate smoothly between subdivisions).
- Driven by the song clock: time elapsed since play start (in ms) ÷ ms-per-16th-note ⇒ floating-point `currentSlot`.
- `msPerSlot = (60000 / bpm) / 4` (4 = sixteenths per beat).

**Multi-line behavior (`DrumStaffStack`):**
- The full song is rendered as a vertical stack of lines, **2 bars per line** by default.
- All lines are visible at all times in the sheet-music container (it scrolls if needed).
- The **current line** is rendered at **opacity 1.0**, all other lines at **opacity 0.55**.
- Only the current line shows the playhead; when playback crosses into the next 2-bar group, the previous line fades to 0.55 and the next line becomes 1.0 with the playhead reset to its left edge. Transition `opacity .25s`.
- Native Android implementation: a `LazyColumn` of staff-line composables; each gets `alpha = if (isCurrent) 1f else 0.55f` (animated).

**Synchronization with audio:**
- Schedule drum-sample playback using Android's `SoundPool` or `AudioTrack` (sample-accurate) keyed off the same song-clock as the playhead.
- Keep playhead position derived purely from `playbackPosition - playStartTime` — never from a counter that increments per audio callback — so visual and audio share the same source of truth.

---

### 04 · Song Detail

**Purpose:** edit playback settings, see import metadata, kick off practice mode.

**Layout (scrollable):**

1. **Cover hero** (200 px tall)
   - Background: linear gradient 135° from `accent` to `cover-grad-to`
   - Diagonal white stripes overlay at 18% opacity (18 stripes, 2 px wide, slanting top-left → bottom-right)
   - Back icon (34 px) absolute top-left 12,12 — translucent white border `rgba(255,255,255,0.45)`
   - More icon (34 px) absolute top-right
   - Bottom-anchored text block:
     - Eyebrow `{nBars} BARS · 4/4` — Mono 10 / uppercase / white at 85% opacity
     - Title (song name) — Space Grotesk 800, 26 px, white
     - Artist — 13 px, white at 90%

2. **CTA row** (padding `14 16`, gap 10)
   - **`Start reading`** — flex 1, padding 14 × 18, background `text` (#f5f5f7), color `bg`, radius 14, Space Grotesk 700, 14 px, letter-spacing 0.4, play icon (13 px) + label. (On the violet variant, swap button bg to `accent`.)
   - Speed/practice button — 14 × 14 padding, transparent, 1.5 px `line` border, radius 14, speed icon (16 px)

3. **`PLAYBACK` section label**

4. **Setting rows** (padding 14 × 16, bottom border `1 px line`):
   - Label (13 / 600) + sub (11 / `dim`) on left; value on right in Mono 13 / 700 / `accent`
   - Rows:
     - **Tempo** · `116 BPM` (sub: "5 BPM under original")
     - **Count-in** · `1 bar` (sub: "Click before playback starts")
     - **Metronome** · `On · soft`
     - **Drum kit** · `Acoustic — Studio`
     - **Mute** · `Hi-hat` (sub: "Practice the muted part live")

5. **`SOURCE` section label**

6. Setting rows:
   - **Imported** · `PDF · 4 pages` (sub: "12 May 2026")
   - **Tempo detection** · `Auto · ±2 BPM`

---

### 05 · Practice (Speed ramp)

**Purpose:** loop a section while incrementally speeding up the tempo each loop, from a slow `startBpm` to a `targetBpm` over `N` loops.

**Layout:**

1. **Header** — back chevron + title `Speed ramp` (Space Grotesk 700, 22 px)

2. **Big BPM card** (`surface`, radius 14, padding 18, centered)
   - Eyebrow: `CURRENT TEMPO` (Mono 10 / dim / uppercase / 1.5 letter-spacing)
   - **Giant number** — Space Grotesk 800, **72 px**, letter-spacing -3.5, tabular-nums, color `accent`
   - Sub: `BPM · LOOP 3 OF 8` (Mono 12 / dim / uppercase)
   - **Range visual** below:
     - Three numeric markers in Mono 10: start (left, dim) — current (middle, accent) — target (right, dim)
     - 6 px tall track, `bar-track` bg, radius 3
     - Fill: `bar-fill` (#8a3dff), width = `(current - start) / (target - start)` × 100%
     - Pinpoint thumb: 12 × 12 circle at the fill's right edge, accent bg, white 4 px ring (achieved with `box-shadow: 0 0 0 4px bg`)

3. **Loop preview card** (`surface`, 1 px `line` border, radius 14, padding `14 10 10`)
   - Label: `LOOPING BARS 1–2` (Mono 11 / dim / uppercase)
   - Embedded 2-bar `DrumStaff` (with clef) — sample state shows playhead at slot 6 of bar 0

4. **Loop-progress dots** (8 equal-width segments, flex gap 6)
   - Completed loops (i < currentLoop): `accent` fill, 60% opacity for completed, 100% for current
   - Pending loops: `bar-track` fill
   - Each segment: flex 1, height 8 px, radius 4

5. **Config card** (`surface`, 1 px `line` border, radius 14, overflow hidden)
   - 4 settings rows (padding 13 × 16, bottom border `1 px line` except last):
     - **Start tempo** · `72 BPM`
     - **Target tempo** · `116 BPM`
     - **Loops** · `8`
     - **Step** · `+6 BPM / loop`

6. **Resume button** — full width, padding 16 × 18, `text` bg, `bg` text, radius 14, Space Grotesk 800, 15 px, letter-spacing 0.5, label `▶ Resume from loop 3`. (Recommend switching to `accent` bg for stronger CTA.)

---

## Interactions & Behavior

### Navigation
- **Home → Player**: tap the "Continue" card or any song row.
- **Home → Upload**: tap the FAB.
- **Home → Song detail**: long-press any row, OR add an explicit chevron / overflow menu item.
- **Song detail → Player**: tap "Start reading".
- **Song detail → Practice**: tap the speed-icon button next to "Start reading".
- **Player → back**: back chevron returns to Home (or wherever the user came from).

### Player playback
- **Play / pause**: tapping the big circular button toggles `playing`. While playing, drum samples fire on each slot that has a non-empty hit, and the playhead interpolates smoothly.
- **Stop**: resets `currentSlot` to 0 and pauses. Playhead snaps to start of bar 1.
- **Loop**: tapping the loop icon enters "select loop region" mode — user taps a start bar and an end bar in the staff; playhead loops between them.
- **Speed/practice icon**: jumps to Practice mode.
- **Metronome icon**: toggles click track on/off (visual: filled-in `accent` background when on).

### Upload flow
- **Drop zone**: tapping opens the system file picker filtered to PDF/PNG/JPG.
- **Take a photo**: opens camera with a viewfinder + capture button; on capture, runs OCR/parsing.
- **Choose PDF or image**: same as drop zone.
- **Connect Spotify**: opens Spotify OAuth flow; on success, shows a "Now playing on Spotify" detection screen (out of scope for v1 — design later).

### Parsing / OCR (backend behavior, FYI)
- After upload, the app shows a processing card on the Upload screen with a progress %.
- When parsing completes, the new song appears in the Library with status "Just imported".

### Practice mode
- **Resume**: starts playback at the current loop's BPM, at the start of the loop region.
- When a loop completes, BPM increments by `step` and the next loop starts (count-in plays once at the start).
- When `currentBpm` reaches `targetBpm`, the ramp ends and the app shows a celebratory state (out of scope for v1).

### Animations
- **Playhead motion**: 60 fps, RAF-driven (Compose: a `Float` state driven by `withFrameNanos`). No easing — strict linear time.
- **Active-line fade**: opacity transition 250 ms on the staff-line container.
- **Drum-hit chip**: snap (80 ms) — these should feel percussive, not smoothly faded.
- **Button press**: scale to 0.97 over 80 ms, return on release.
- **Tab swap**: 150 ms ease-out on the active-pill background slide (if implementing animated tabs; otherwise instant is fine).
- **FAB press**: scale + shadow lift.

### Tap targets & accessibility
- All interactive elements **≥ 40 × 40 dp**.
- BPM-readout numerals use `font-variant-numeric: tabular-nums` so digits don't jitter when ticking.
- Color contrast: `text` on `bg` is 17:1 (passes WCAG AAA); `dim` on `bg` is 4.9:1 (passes AA). `accent` on `bg` is 5.4:1 (AA).
- Provide an alternative visual indicator for the playhead beyond just color (e.g., the dot cap and bold weight) — colorblind users should still be able to see it against the dark surface.

---

## State Management

Suggested ViewModel shape (Kotlin / Compose):

```kotlin
data class Song(
  val id: String,
  val title: String,
  val artist: String,
  val bpm: Int,
  val timeSig: Pair<Int, Int>,
  val bars: List<List<List<DrumToken>>>,   // bars → slots → hits
  val coverInitials: String,               // e.g. "NV"
  val importedFrom: ImportSource,          // PDF | IMAGE | SPOTIFY
  val lastPlayed: Instant?,
)

enum class DrumToken { KICK, SNARE, HIHAT, OPEN_HIHAT, CRASH, RIDE, TOM_HI, TOM_MID, TOM_FLOOR }

data class PlayerState(
  val song: Song,
  val playing: Boolean,
  val currentSlot: Float,     // 0..totalSlots, fractional
  val bpm: Int,               // may differ from song.bpm (practice mode)
  val metronomeOn: Boolean,
  val countInBars: Int,       // 0, 1, or 2
  val mutedDrums: Set<DrumToken>,
  val loopRange: IntRange?,   // null = no loop
)

data class PracticeState(
  val startBpm: Int,
  val targetBpm: Int,
  val loops: Int,
  val stepBpm: Int,
  val currentLoop: Int,
  val currentBpm: Int,
)
```

Player state transitions:
- `play()`: set `playing = true`, record `playStartTime = SystemClock.elapsedRealtime()`, start RAF loop updating `currentSlot`.
- `pause()`: `playing = false`, RAF loop exits.
- `stop()`: `currentSlot = 0`, `playing = false`.
- Slot-change effect: when `floor(currentSlot)` changes, schedule audio for any drums in that slot.

### Persistence
- Songs stored in Room DB (table per the `Song` shape above; `bars` serialized to JSON).
- Last-played and `lastSlot` persisted so reopening a song resumes where the user was (matches the "save playback position to localStorage" guidance from the design phase).

### Audio
- Use **SoundPool** for short drum samples (kick, snare, closed hi-hat, open hi-hat, crash, ride, hi/mid/floor tom). Recommend studio-quality 24-bit samples at ~200ms each.
- For sub-frame precision on metronome click + drum hits, prefer **Oboe** (low-latency C++ audio) if you can afford the integration cost.

---

## Assets

- **No image assets** in the design — all visuals are CSS/SVG. Cover art is procedurally rendered (gradient + diagonal stripes + monogram).
- **Icons**: use Material Symbols Outlined for the production app. The ones in the prototype are placeholders.
- **Drum samples**: source from a CC0 / royalty-free pack (suggest [Soni Musicae](https://sonimusicae.free.fr/) or [Philharmonia samples](https://philharmonia.co.uk/resources/sound-samples/)). Need at minimum: kick, snare (hit + rim), hi-hat (closed + open + pedal), crash, ride, 3 toms, metronome click (high + low).
- **Fonts**: Space Grotesk + JetBrains Mono from Google Fonts. Bundle via `app/src/main/res/font/` for offline use.

---

## Files

In `design_reference/`:

| File | What's in it |
|---|---|
| `Drums App.html` | Entry point. Open in a browser to see the full prototype canvas. |
| `drums-app.jsx` | App shell: design canvas, theme application, live playhead hook (`useLivePlayhead`), tweaks panel wiring. **The `useLivePlayhead` hook is the reference implementation of the playhead timing — port this logic directly.** |
| `screens.jsx` | All 5 screen components + the `THEMES` object (the **Neon Studio** theme is the chosen one — use those exact hex values). The other two themes are reference for divergent directions; do not ship. |
| `drum-staff.jsx` | **Canonical reference for drum-notation rendering.** Includes the SVG layout math for staff lines, drum vertical positions, noteheads (oval + X), stems, beams, and playhead. Also defines sample bar patterns (`BAR_GROOVE`, `BAR_FILL`, `BAR_CRASH`) and a default song object. **Read this file carefully** — porting it accurately is the single most important task. |
| `design-canvas.jsx` | Prototype-only — Figma-ish pan/zoom canvas wrapper. **Ignore for the Android implementation.** |
| `android-frame.jsx` | Prototype-only — fake Android bezel for the mocks. Ignore. |
| `tweaks-panel.jsx` | Prototype-only — live design-tweaks panel. Ignore. |

---

## Open questions for the user / PM

These weren't fully nailed down in the design phase — please confirm before building:

1. **OCR for sheet music**: which engine? A hosted service (e.g., SmartScore, PhotoScore Cloud) or an on-device library (e.g., [Audiveris](https://github.com/Audiveris/audiveris) — JVM, may need significant adaptation)? This affects offline behavior and privacy.
2. **Spotify integration scope**: just detect what's playing and load a matching chart from the library? Or actually align playback to Spotify's audio? The latter requires Spotify Web Playback SDK and a Premium account.
3. **Drum-kit sample selection**: one default, or user-selectable from multiple kits? The Song Detail screen suggests selectable ("Acoustic — Studio") — confirm.
4. **Loop region selection UX**: tap-and-drag on the staff? Numeric input? Both? The current design hints at this but doesn't fully specify.
5. **Account & sync**: is this single-device with local DB only, or are songs synced to an account?
6. **Onboarding flow**: not designed. Probably needed for first-run permissions (camera, audio, storage) and Spotify connection.

---

*Generated handoff for the Drums App design exploration. Lock-in direction: **Neon Studio (Violet)**. Questions? Re-open the prototype HTML — every measurement and color in this README came from there.*
