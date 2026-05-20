# How to run the drums app

## Status

This is the **Phase 1** scaffold — all 26 plan tasks have landed, but a few asset
slots are intentionally empty. Build, install, and run will work; until you fill
the placeholder slots below, expect:

- Typography uses system default fonts (not Space Grotesk / JetBrains Mono).
- All "play" sounds are silent (no WAV samples bundled).
- The launcher icon shows the system default (no monogram).

## What you already have

- **Android Studio Panda 4**: `~/Downloads/android-studio-panda4-patch1-linux/android-studio/bin/studio.sh`
- **Android SDK**: `~/Android/Sdk` (build-tools 36.1.0 / 37.0.0, platform-tools, platform android-36)
- **Bundled JDK 21**: `~/Downloads/android-studio-panda4-patch1-linux/android-studio/jbr` (Android Studio uses this automatically — do not point Gradle at the system `java`, which is JDK 25)

## First-time setup (one click)

1. Run `~/Downloads/android-studio-panda4-patch1-linux/android-studio/bin/studio.sh` (or pin it to your launcher).
2. Studio → **Open** → navigate to `~/claude/drums-app` → Open.
3. Wait for "Gradle sync" (bottom-right progress bar). First sync downloads dependencies and **generates `gradlew`/`gradlew.bat`** for you. Allow 5–10 min on first run.
4. If Studio prompts to "Install missing SDK Platform: android-34", click **Install** and accept the license. (Alternatively, you can bump `compileSdk = 36` and `targetSdk = 36` in `app/build.gradle.kts` to use what's already on disk.)
5. If Gradle complains about the project JDK, **File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** → select "Embedded JDK 21" (Studio's bundled JBR).

## Run on an emulator

1. Studio toolbar → **Device Manager** → **Create Device** → pick **Pixel 8** → choose a system image (recommended: latest stable, API 34 or 35) → **Finish**.
2. Top toolbar → device dropdown → pick your Pixel 8.
3. Click the green ▶ **Run** button. Studio builds the APK, installs it, and launches.

## Run on a physical phone

1. On the phone: **Settings → About phone → tap "Build number" 7 times** to enable Developer options.
2. **Settings → System → Developer options → enable "USB debugging"**.
3. Plug into the laptop. On the phone, tap **Allow** when prompted for USB debugging.
4. The phone shows up in Studio's device dropdown. Pick it and click ▶ **Run**.

## Smoke checklist

After Run:

- [ ] App opens to a screen titled **Your kit** with 5 song rows.
- [ ] Tap **Smells Like Teen Spirit** → Player opens, drum staff renders, violet playhead at slot 0.
- [ ] Tap the big violet **Play** button → playhead sweeps across the staff. (Sound is silent until WAVs are bundled — see below.)
- [ ] Tap **Stop** → playhead resets to bar 1.
- [ ] Tap **Back** → returns to Library.
- [ ] Long-press a song row → opens Song Detail with cover hero + settings.
- [ ] In Song Detail, tap the Practice icon (right of "Start reading") → opens Speed-ramp screen.

If anything's broken, open Logcat at the bottom of Studio and copy the first ~20 red lines.

## Run unit tests from the terminal (after first sync)

Once Studio has generated `gradlew`:

```bash
cd ~/claude/drums-app
export ANDROID_HOME=~/Android/Sdk
export JAVA_HOME=~/Downloads/android-studio-panda4-patch1-linux/android-studio/jbr
./gradlew :app:testDebugUnitTest
```

Expected: `SongClockTest` and `DrumStaffLayoutTest` pass (pure JVM logic, no Android needed).

## Placeholder slots to fill (optional)

Each of these has a working fallback, so the app builds and runs today. Fill them when you're ready.

### 1. Drum sample WAVs

Path: `app/src/main/assets/samples/`
Required files (16-bit mono WAV, ≤200 ms each, ~44.1 kHz):
`kick.wav`, `snare.wav`, `hihat_closed.wav`, `hihat_open.wav`, `crash.wav`, `ride.wav`, `tom_hi.wav`, `tom_mid.wav`, `tom_floor.wav`, `click_high.wav`, `click_low.wav`

Source recommendation: https://freesound.org → search "drum kit one shot CC0". Until these exist, every `play()` is a silent no-op (no crash).

### 2. Typography TTFs

Path: `app/src/main/res/font/`
Files (snake-case lowercase):
- `space_grotesk_medium.ttf` (500)
- `space_grotesk_semibold.ttf` (600)
- `space_grotesk_bold.ttf` (700)
- `space_grotesk_extrabold.ttf` (800)
- `jetbrains_mono_regular.ttf` (400)
- `jetbrains_mono_medium.ttf` (500)
- `jetbrains_mono_bold.ttf` (700)

Source: https://fonts.google.com/specimen/Space+Grotesk and https://fonts.google.com/specimen/JetBrains+Mono. Download the family, unzip, copy `static/*.ttf`, rename to the snake-case names above.

Then in `app/src/main/java/ph/nextbank/drums/ui/theme/Type.kt`, replace the `FontFamily.Default` / `FontFamily.Monospace` fallbacks with:

```kotlin
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
    Font(R.font.space_grotesk_extrabold, FontWeight.ExtraBold),
)
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)
```

And add at the top of the file:
```kotlin
import androidx.compose.ui.text.font.Font
import ph.nextbank.drums.R
```

### 3. Launcher icons

Path: `app/src/main/res/mipmap-*/`

In Studio: right-click `res/` → **New → Image Asset** → choose a foreground icon (logo or monogram), accept defaults. Studio generates all density variants. Until then, the app uses the system default launcher icon.

## Known caveats for Phase 1

- Practice screen "Resume" button is wired to a no-op. Tapping it logs nothing — wiring the speed-ramp into Player is a Phase 1.5 follow-up.
- Song Detail only persists **tempo** to Room. Count-in, metronome mode, kit, mute settings are UI-only state and reset on restart.
- All Upload screen actions toast "Coming in a future update". OCR + Spotify are Phase 2/3.
