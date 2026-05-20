# Drums App — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a runnable Android APK that lets a user open one of five bundled drum songs and watch a violet playhead sweep across a procedurally drawn drum-staff while drum samples play in time. Sheet-music import and Spotify are stubbed.

**Architecture:** Single-module Jetpack Compose + Material 3 app. MVVM with one `ViewModel` per screen, Hilt for DI, Room for local persistence. Drum-staff rendering uses Compose `Canvas` (procedural drawing — no notation library). The playhead is driven by a `SongClock` derived from `SystemClock.elapsedRealtime()`; the same clock schedules audio via `SoundPool`.

**Tech Stack:** Kotlin · Jetpack Compose · Material 3 · Hilt · Room · SoundPool · JUnit 4 · Compose UI Test · Gradle (Kotlin DSL)

**Source of truth:** `design-reference/handoff.md` (all colors, sizes, copy, layouts) and `design-reference/drum-staff.jsx` (canonical staff/playhead math).

---

## File structure

```
drums-app/
├── settings.gradle.kts                 # New
├── build.gradle.kts                    # New — project-level
├── gradle.properties                   # New
├── gradle/libs.versions.toml           # New — version catalog
├── gradle/wrapper/                     # New — Gradle wrapper
├── gradlew, gradlew.bat                # New — wrapper scripts
├── .gitignore                          # New
├── app/
│   ├── build.gradle.kts                # New — module
│   ├── proguard-rules.pro              # New
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml     # New
│       │   ├── assets/samples/         # 11 WAV files (user drops in)
│       │   ├── res/
│       │   │   ├── values/strings.xml, themes.xml, colors.xml
│       │   │   ├── font/space_grotesk_*.ttf, jetbrains_mono_*.ttf
│       │   │   └── mipmap-*/ic_launcher.* (default launcher icons)
│       │   └── java/ph/nextbank/drums/
│       │       ├── DrumsApplication.kt
│       │       ├── MainActivity.kt
│       │       ├── data/
│       │       │   ├── model/Song.kt, DrumToken.kt, ImportSource.kt
│       │       │   ├── db/AppDatabase.kt, SongDao.kt, SongEntity.kt, Converters.kt
│       │       │   ├── repo/SongRepository.kt
│       │       │   └── samples/SampleSongs.kt
│       │       ├── di/AppModule.kt
│       │       ├── audio/
│       │       │   ├── DrumSampleBank.kt
│       │       │   ├── SongClock.kt
│       │       │   └── Metronome.kt
│       │       ├── ui/
│       │       │   ├── theme/Theme.kt, Color.kt, Type.kt, Shape.kt
│       │       │   ├── components/
│       │       │   │   ├── DrumStaff.kt
│       │       │   │   ├── DrumStaffStack.kt
│       │       │   │   ├── DrumHitChips.kt
│       │       │   │   ├── CoverArt.kt
│       │       │   │   ├── PillTab.kt
│       │       │   │   └── TransportButton.kt
│       │       │   ├── library/LibraryScreen.kt, LibraryViewModel.kt
│       │       │   ├── upload/UploadScreen.kt, UploadViewModel.kt
│       │       │   ├── player/PlayerScreen.kt, PlayerViewModel.kt
│       │       │   ├── song_detail/SongDetailScreen.kt, SongDetailViewModel.kt
│       │       │   ├── practice/PracticeScreen.kt, PracticeViewModel.kt
│       │       │   └── nav/NavGraph.kt, Route.kt
│       │       └── util/Format.kt
│       ├── test/java/ph/nextbank/drums/
│       │   ├── audio/SongClockTest.kt
│       │   ├── data/SongRepositoryTest.kt
│       │   └── ui/components/DrumStaffMathTest.kt
│       └── androidTest/java/ph/nextbank/drums/
│           └── LibrarySmokeTest.kt
└── docs/
    └── HOW_TO_RUN.md                   # Instructions for the user
```

---

## Milestone 1 — Project skeleton & theme (Tasks 1–5)

End state: empty app installs and shows a violet "Drums" splash. No data, no audio.

### Task 1: Gradle scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `.gitignore`

- [ ] **Step 1: Create `.gitignore`**

```gitignore
.gradle/
build/
local.properties
*.iml
.idea/
.cxx/
captures/
.kotlin/
app/release/
```

- [ ] **Step 2: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.5.2"
kotlin = "2.0.20"
ksp = "2.0.20-1.0.25"
compose-bom = "2024.09.02"
compose-compiler-ext = "1.5.15"
hilt = "2.52"
hilt-nav-compose = "1.2.0"
room = "2.6.1"
lifecycle = "2.8.6"
navigation = "2.8.1"
core-ktx = "1.13.1"
activity-compose = "1.9.2"
junit = "4.13.2"
androidx-test-core = "1.6.1"
androidx-test-runner = "1.6.2"
androidx-test-ext-junit = "1.2.1"
espresso = "3.6.1"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "core-ktx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }
androidx-lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }

compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }

navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }

hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hilt-nav-compose" }

room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }

junit = { module = "junit:junit", version.ref = "junit" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidx-test-core" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidx-test-runner" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-ext-junit" }
espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 3: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}

rootProject.name = "drums-app"
include(":app")
```

- [ ] **Step 4: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

- [ ] **Step 5: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 6: Generate Gradle wrapper**

Run: `cd /home/sara/claude/drums-app && gradle wrapper --gradle-version 8.10.2 --distribution-type bin` (if `gradle` is not on PATH, the user will generate the wrapper from Android Studio's first sync — note this in HOW_TO_RUN.md). Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` exist.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat .gitignore
git commit -m "Add Gradle scaffold + version catalog"
```

### Task 2: App module build file + manifest

**Files:**
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/values/colors.xml`

- [ ] **Step 1: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "ph.nextbank.drums"
    compileSdk = 34

    defaultConfig {
        applicationId = "ph.nextbank.drums"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
```

- [ ] **Step 2: Create empty `app/proguard-rules.pro`**

```
# Keep Room generated DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
```

- [ ] **Step 3: Create `app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">Drums</string>
</resources>
```

- [ ] **Step 4: Create `app/src/main/res/values/colors.xml`** (token colors from `design-reference/handoff.md §Design Tokens → Colors`)

```xml
<resources>
    <color name="bg">#0a0a0c</color>
    <color name="surface">#131318</color>
    <color name="surface2">#1c1c24</color>
    <color name="line">#26262e</color>
    <color name="text">#f5f5f7</color>
    <color name="dim">#8a8a94</color>
    <color name="accent">#8a3dff</color>
    <color name="accent_on">#ffffff</color>
    <color name="staff_line">#3a3a44</color>
    <color name="note">#f5f5f7</color>
    <color name="playhead">#8a3dff</color>
    <color name="bar_track">#26262e</color>
    <color name="cover_grad_from">#8a3dff</color>
    <color name="cover_grad_to">#7a1020</color>
</resources>
```

- [ ] **Step 5: Create `app/src/main/res/values/themes.xml`** (XML theme is a thin Material3 shim; actual styling is in Compose)

```xml
<resources>
    <style name="Theme.Drums" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">@color/bg</item>
        <item name="android:windowBackground">@color/bg</item>
    </style>
</resources>
```

- [ ] **Step 6: Create `app/src/main/AndroidManifest.xml`**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".DrumsApplication"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:theme="@style/Theme.Drums"
        android:allowBackup="false">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Drums">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 7: Add default launcher icons**

Run: `cp -r $ANDROID_HOME/platforms/android-34/data/res/mipmap-mdpi/sym_def_app_icon.png app/src/main/res/mipmap-mdpi/ic_launcher.png` (or, if no `$ANDROID_HOME`, the user will generate launcher icons via Android Studio's Image Asset Studio on first open — note this in HOW_TO_RUN.md). At minimum, create empty mipmap directories so the manifest references resolve:

```bash
mkdir -p app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}
```

- [ ] **Step 8: Commit**

```bash
git add app/
git commit -m "Add Android app module + manifest + token colors"
```

### Task 3: Application class + Hilt DI module skeleton

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/DrumsApplication.kt`
- Create: `app/src/main/java/ph/nextbank/drums/di/AppModule.kt`

- [ ] **Step 1: Create `DrumsApplication.kt`**

```kotlin
package ph.nextbank.drums

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DrumsApplication : Application()
```

- [ ] **Step 2: Create empty `AppModule.kt` (will be filled by later tasks)**

```kotlin
package ph.nextbank.drums.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/
git commit -m "Add Hilt application class + empty DI module"
```

### Task 4: Compose theme (colors, typography, shapes)

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/theme/Color.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/theme/Type.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/theme/Shape.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/theme/Theme.kt`

- [ ] **Step 1: Create `Color.kt`** (mirrors `colors.xml`; Compose-side tokens)

```kotlin
package ph.nextbank.drums.ui.theme

import androidx.compose.ui.graphics.Color

object DrumsColors {
    val Bg = Color(0xFF0A0A0C)
    val Surface = Color(0xFF131318)
    val Surface2 = Color(0xFF1C1C24)
    val Line = Color(0xFF26262E)
    val Text = Color(0xFFF5F5F7)
    val Dim = Color(0xFF8A8A94)
    val Accent = Color(0xFF8A3DFF)
    val AccentOn = Color(0xFFFFFFFF)
    val StaffLine = Color(0xFF3A3A44)
    val Note = Color(0xFFF5F5F7)
    val Playhead = Color(0xFF8A3DFF)
    val BarTrack = Color(0xFF26262E)
    val ChipBg = Color(0x14FFFFFF)         // rgba(255,255,255,0.08)
    val ChipActiveBg = Color(0xFF8A3DFF)
    val ChipActiveText = Color(0xFF0A0A0C)
    val CoverGradFrom = Color(0xFF8A3DFF)
    val CoverGradTo = Color(0xFF7A1020)
}
```

- [ ] **Step 2: Add Google Fonts to `res/font/`**

The Compose `androidx.compose.ui.text.googlefonts.GoogleFont` API can fetch Space Grotesk and JetBrains Mono at runtime, but for offline use we bundle the TTFs.

Download and drop these files into `app/src/main/res/font/`:
- `space_grotesk_medium.ttf` (500)
- `space_grotesk_semibold.ttf` (600)
- `space_grotesk_bold.ttf` (700)
- `space_grotesk_extrabold.ttf` (800)
- `jetbrains_mono_regular.ttf` (400)
- `jetbrains_mono_medium.ttf` (500)
- `jetbrains_mono_bold.ttf` (700)

Source: https://fonts.google.com/specimen/Space+Grotesk and https://fonts.google.com/specimen/JetBrains+Mono (Download Family → unzip → copy `static/*.ttf` → rename to snake_case as above).

This is a manual file-drop step. The plan will check for these files in `Type.kt` and warn at compile if missing.

- [ ] **Step 3: Create `Type.kt`** (type scale from `handoff.md §Typography`)

```kotlin
package ph.nextbank.drums.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ph.nextbank.drums.R

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

object DrumsType {
    val displayBpm = TextStyle(SpaceGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 72.sp, letterSpacing = (-0.05).em, lineHeight = 72.sp)
    val playerBpm = TextStyle(SpaceGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, letterSpacing = (-0.04).em, lineHeight = 36.sp)
    val screenTitle = TextStyle(SpaceGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.03).em, lineHeight = 32.sp)
    val coverTitle = TextStyle(SpaceGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.03).em, lineHeight = 27.sp)
    val barCounter = TextStyle(SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.045).em, lineHeight = 22.sp)
    val sectionTitle = TextStyle(SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.022).em, lineHeight = 24.sp)
    val cardTitle = TextStyle(SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.018).em, lineHeight = 20.sp)
    val buttonLabel = TextStyle(SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.028.em, lineHeight = 17.sp)
    val rowTitle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = (-0.014).em, lineHeight = 18.sp)
    val body = TextStyle(SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp)
    val caption = TextStyle(JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp)
    val allCapsLabel = TextStyle(JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.13.em, lineHeight = 13.sp)
    val drumHitChip = TextStyle(JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.08.em, lineHeight = 9.sp)
}

// Material3 mapping (used by default Material components; our styled UI uses DrumsType directly)
val DrumsTypography = Typography(
    headlineLarge = DrumsType.screenTitle,
    titleLarge = DrumsType.sectionTitle,
    titleMedium = DrumsType.cardTitle,
    labelLarge = DrumsType.buttonLabel,
    bodyMedium = DrumsType.body,
    labelSmall = DrumsType.caption,
)
```

- [ ] **Step 4: Create `Shape.kt`**

```kotlin
package ph.nextbank.drums.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val DrumsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
```

- [ ] **Step 5: Create `Theme.kt`**

```kotlin
package ph.nextbank.drums.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    background = DrumsColors.Bg,
    surface = DrumsColors.Surface,
    surfaceVariant = DrumsColors.Surface2,
    onBackground = DrumsColors.Text,
    onSurface = DrumsColors.Text,
    primary = DrumsColors.Accent,
    onPrimary = DrumsColors.AccentOn,
    outline = DrumsColors.Line,
)

@Composable
fun DrumsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = DrumsTypography,
        shapes = DrumsShapes,
        content = content,
    )
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/theme/ app/src/main/res/font/
git commit -m "Add Compose theme: colors, typography, shapes"
```

### Task 5: MainActivity + splash placeholder

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/MainActivity.kt`

- [ ] **Step 1: Create `MainActivity.kt` with a placeholder screen**

```kotlin
package ph.nextbank.drums

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsTheme
import ph.nextbank.drums.ui.theme.DrumsType

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DrumsTheme { PlaceholderRoot() }
        }
    }
}

@Composable
private fun PlaceholderRoot() {
    Box(
        modifier = Modifier.fillMaxSize().background(DrumsColors.Bg),
        contentAlignment = Alignment.Center,
    ) {
        Text("Drums", color = DrumsColors.Accent, style = DrumsType.screenTitle)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/MainActivity.kt
git commit -m "Add MainActivity with placeholder splash"
```

- [ ] **Step 3: Verification checkpoint**

The user (or you, if Android SDK is on PATH) should open the project in Android Studio, sync Gradle, and run on the emulator. Expected: black screen with a centered violet "Drums" word. If sync fails, fix and commit before continuing to Milestone 2.

---

## Milestone 2 — Data model, Room, sample songs (Tasks 6–10)

End state: library screen renders 5 hard-coded songs from the Room DB. No interaction yet.

### Task 6: Data model — Song, DrumToken, ImportSource

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/data/model/DrumToken.kt`
- Create: `app/src/main/java/ph/nextbank/drums/data/model/ImportSource.kt`
- Create: `app/src/main/java/ph/nextbank/drums/data/model/Song.kt`

- [ ] **Step 1: Create `DrumToken.kt`**

```kotlin
package ph.nextbank.drums.data.model

/**
 * Drum-staff tokens. The string code matches the design-reference (drum-staff.jsx)
 * so the canonical patterns transfer 1:1.
 */
enum class DrumToken(val code: String) {
    KICK("k"),
    SNARE("s"),
    HIHAT_CLOSED("h"),
    HIHAT_OPEN("o"),
    CRASH("c"),
    RIDE("r"),
    TOM_HI("t1"),
    TOM_MID("t2"),
    TOM_FLOOR("t3");

    companion object {
        private val byCode = values().associateBy { it.code }
        fun fromCode(code: String): DrumToken =
            byCode[code] ?: error("Unknown drum token: $code")
    }
}
```

- [ ] **Step 2: Create `ImportSource.kt`**

```kotlin
package ph.nextbank.drums.data.model

enum class ImportSource { BUNDLED, PDF, IMAGE, SPOTIFY }
```

- [ ] **Step 3: Create `Song.kt`**

```kotlin
package ph.nextbank.drums.data.model

import java.time.Instant

/**
 * A song is a sequence of bars; each bar is a sequence of subdivision slots;
 * each slot is a (possibly empty) list of drum tokens hit at that subdivision.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val bpm: Int,
    val timeSig: Pair<Int, Int>,        // (beatsPerBar, beatUnit) — Phase 1 always (4, 4)
    val bars: List<List<List<DrumToken>>>,
    val coverInitials: String,           // e.g. "NV", "RU"
    val importedFrom: ImportSource,
    val lastPlayed: Instant?,
) {
    val totalBars: Int get() = bars.size
    val slotsPerBar: Int get() = bars.firstOrNull()?.size ?: 16
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/data/model/
git commit -m "Add Song, DrumToken, ImportSource data model"
```

### Task 7: Sample songs (5 bundled bar patterns)

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/data/samples/SampleSongs.kt`

- [ ] **Step 1: Create `SampleSongs.kt`** (patterns adapted from `design-reference/drum-staff.jsx` BAR_GROOVE / BAR_FILL / BAR_CRASH, plus three more grooves invented for variety)

```kotlin
package ph.nextbank.drums.data.samples

import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.model.DrumToken.*
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song

// ─── primitive helpers ─────────────────────────────────────────
private val _: List<DrumToken> = emptyList()
private fun hit(vararg t: DrumToken): List<DrumToken> = t.toList()

// ─── canonical bars (from drum-staff.jsx) ──────────────────────
private val GROOVE_A = listOf(
    hit(KICK, HIHAT_CLOSED), _, hit(HIHAT_CLOSED), _,
    hit(SNARE, HIHAT_CLOSED), _, hit(HIHAT_CLOSED), _,
    hit(KICK, HIHAT_CLOSED), _, hit(KICK, HIHAT_CLOSED), _,
    hit(SNARE, HIHAT_CLOSED), _, hit(HIHAT_CLOSED), _,
)

private val GROOVE_B = listOf(
    hit(KICK, HIHAT_CLOSED), _, hit(HIHAT_CLOSED), _,
    hit(SNARE, HIHAT_CLOSED), _, hit(HIHAT_CLOSED), _,
    hit(KICK, HIHAT_CLOSED), _, hit(HIHAT_CLOSED), _,
    hit(SNARE, HIHAT_CLOSED), _, hit(SNARE, HIHAT_CLOSED), _,
)

private val FILL = listOf(
    hit(KICK, HIHAT_CLOSED), _, hit(HIHAT_CLOSED), _,
    hit(SNARE, HIHAT_CLOSED), _, hit(HIHAT_CLOSED), _,
    hit(SNARE), hit(SNARE), hit(TOM_HI), hit(TOM_HI),
    hit(TOM_MID), hit(TOM_MID), hit(TOM_FLOOR), hit(TOM_FLOOR),
)

private val CRASH = listOf(
    hit(KICK, CRASH), _, hit(HIHAT_CLOSED), _,
    hit(SNARE, HIHAT_CLOSED), _, hit(HIHAT_CLOSED), _,
    hit(KICK, HIHAT_CLOSED), _, hit(KICK, HIHAT_CLOSED), _,
    hit(SNARE, HIHAT_CLOSED), _, hit(HIHAT_CLOSED), _,
)

// Slower, ride-heavy groove for Tom Sawyer feel
private val RIDE_GROOVE = listOf(
    hit(KICK, RIDE), _, hit(RIDE), _,
    hit(SNARE, RIDE), _, hit(RIDE), _,
    hit(KICK, RIDE), _, hit(KICK, RIDE), _,
    hit(SNARE, RIDE), _, hit(RIDE), _,
)

// Half-time feel for In the Air Tonight
private val HALF_TIME = listOf(
    hit(KICK, HIHAT_CLOSED), _, _, _,
    _, _, hit(HIHAT_CLOSED), _,
    _, _, hit(SNARE), _,
    _, _, hit(HIHAT_CLOSED), _,
)

// Eighth-note open hihat groove for Rosanna feel
private val ROSANNA_FEEL = listOf(
    hit(KICK, HIHAT_CLOSED), _, hit(HIHAT_OPEN), _,
    hit(SNARE, HIHAT_CLOSED), _, hit(HIHAT_CLOSED), _,
    hit(HIHAT_CLOSED), _, hit(KICK, HIHAT_OPEN), _,
    hit(SNARE, HIHAT_CLOSED), _, hit(HIHAT_CLOSED), _,
)

// ─── songs ────────────────────────────────────────────────────
val SAMPLE_SONGS: List<Song> = listOf(
    Song(
        id = "smells-like-teen-spirit",
        title = "Smells Like Teen Spirit",
        artist = "Nirvana",
        bpm = 116,
        timeSig = 4 to 4,
        bars = listOf(CRASH, GROOVE_A, GROOVE_B, FILL, CRASH, GROOVE_A, GROOVE_B, FILL),
        coverInitials = "NV",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
    Song(
        id = "tom-sawyer",
        title = "Tom Sawyer",
        artist = "Rush",
        bpm = 88,
        timeSig = 4 to 4,
        bars = listOf(CRASH, RIDE_GROOVE, RIDE_GROOVE, FILL, CRASH, RIDE_GROOVE, RIDE_GROOVE, FILL),
        coverInitials = "RU",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
    Song(
        id = "rosanna",
        title = "Rosanna",
        artist = "Toto",
        bpm = 86,
        timeSig = 4 to 4,
        bars = listOf(CRASH, ROSANNA_FEEL, ROSANNA_FEEL, FILL, CRASH, ROSANNA_FEEL, ROSANNA_FEEL, FILL),
        coverInitials = "TO",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
    Song(
        id = "in-the-air-tonight",
        title = "In the Air Tonight",
        artist = "Phil Collins",
        bpm = 95,
        timeSig = 4 to 4,
        bars = listOf(HALF_TIME, HALF_TIME, HALF_TIME, HALF_TIME, HALF_TIME, FILL, CRASH, HALF_TIME),
        coverInitials = "PC",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
    Song(
        id = "yyz",
        title = "YYZ",
        artist = "Rush",
        bpm = 144,
        timeSig = 4 to 4,
        bars = listOf(CRASH, GROOVE_A, FILL, GROOVE_B, CRASH, GROOVE_A, FILL, GROOVE_B),
        coverInitials = "RU",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/data/samples/
git commit -m "Add 5 bundled sample songs"
```

### Task 8: Room database (entity, DAO, converters, AppDatabase)

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/data/db/SongEntity.kt`
- Create: `app/src/main/java/ph/nextbank/drums/data/db/Converters.kt`
- Create: `app/src/main/java/ph/nextbank/drums/data/db/SongDao.kt`
- Create: `app/src/main/java/ph/nextbank/drums/data/db/AppDatabase.kt`

- [ ] **Step 1: Create `SongEntity.kt`**

```kotlin
package ph.nextbank.drums.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song
import java.time.Instant

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val bpm: Int,
    val beatsPerBar: Int,
    val beatUnit: Int,
    /** Encoded bars: bars separated by '|', slots by ',', tokens within a slot by '+'. Empty slot = empty string. */
    val barsEncoded: String,
    val coverInitials: String,
    val importedFrom: ImportSource,
    val lastPlayedEpochMs: Long?,
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        bpm = bpm,
        timeSig = beatsPerBar to beatUnit,
        bars = decodeBars(barsEncoded),
        coverInitials = coverInitials,
        importedFrom = importedFrom,
        lastPlayed = lastPlayedEpochMs?.let(Instant::ofEpochMilli),
    )

    companion object {
        fun fromSong(s: Song): SongEntity = SongEntity(
            id = s.id,
            title = s.title,
            artist = s.artist,
            bpm = s.bpm,
            beatsPerBar = s.timeSig.first,
            beatUnit = s.timeSig.second,
            barsEncoded = encodeBars(s.bars),
            coverInitials = s.coverInitials,
            importedFrom = s.importedFrom,
            lastPlayedEpochMs = s.lastPlayed?.toEpochMilli(),
        )

        internal fun encodeBars(bars: List<List<List<DrumToken>>>): String =
            bars.joinToString("|") { bar ->
                bar.joinToString(",") { slot -> slot.joinToString("+") { it.code } }
            }

        internal fun decodeBars(s: String): List<List<List<DrumToken>>> =
            s.split("|").map { bar ->
                bar.split(",").map { slot ->
                    if (slot.isEmpty()) emptyList()
                    else slot.split("+").map(DrumToken::fromCode)
                }
            }
    }
}
```

- [ ] **Step 2: Create `Converters.kt`**

```kotlin
package ph.nextbank.drums.data.db

import androidx.room.TypeConverter
import ph.nextbank.drums.data.model.ImportSource

class Converters {
    @TypeConverter fun fromImportSource(v: ImportSource): String = v.name
    @TypeConverter fun toImportSource(v: String): ImportSource = ImportSource.valueOf(v)
}
```

- [ ] **Step 3: Create `SongDao.kt`**

```kotlin
package ph.nextbank.drums.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY lastPlayedEpochMs DESC NULLS LAST, title ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun findById(id: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Update
    suspend fun update(song: SongEntity)

    @Query("UPDATE songs SET bpm = :bpm WHERE id = :id")
    suspend fun updateBpm(id: String, bpm: Int)
}
```

- [ ] **Step 4: Create `AppDatabase.kt`** (with seed callback)

```kotlin
package ph.nextbank.drums.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ph.nextbank.drums.data.samples.SAMPLE_SONGS

@Database(entities = [SongEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        fun build(ctx: Context, scope: CoroutineScope): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "drums.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed sample songs on first launch.
                        scope.launch(Dispatchers.IO) {
                            // We deliberately re-open the DB via the builder again because
                            // the callback fires before the builder returns. Use the
                            // instance held by Hilt instead — see AppModule.
                        }
                    }
                })
                .build()
    }
}
```

Note: the seed is actually done from the Hilt provider in Task 9 because the callback can't access the Hilt-provided DAO directly. The callback above is a no-op; the AppModule will perform an idempotent seed after building the DB.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/data/db/
git commit -m "Add Room entity, DAO, converters, AppDatabase"
```

### Task 9: SongRepository + Hilt providers

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/data/repo/SongRepository.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/di/AppModule.kt`

- [ ] **Step 1: Create `SongRepository.kt`**

```kotlin
package ph.nextbank.drums.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ph.nextbank.drums.data.db.SongDao
import ph.nextbank.drums.data.db.SongEntity
import ph.nextbank.drums.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepository @Inject constructor(private val dao: SongDao) {
    fun observeAll(): Flow<List<Song>> = dao.observeAll().map { list -> list.map(SongEntity::toSong) }
    suspend fun findById(id: String): Song? = dao.findById(id)?.toSong()
    suspend fun upsertAll(songs: List<Song>) = dao.insertAll(songs.map(SongEntity::fromSong))
    suspend fun updateBpm(id: String, bpm: Int) = dao.updateBpm(id, bpm)
}
```

- [ ] **Step 2: Rewrite `AppModule.kt`**

```kotlin
package ph.nextbank.drums.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ph.nextbank.drums.data.db.AppDatabase
import ph.nextbank.drums.data.db.SongDao
import ph.nextbank.drums.data.repo.SongRepository
import ph.nextbank.drums.data.samples.SAMPLE_SONGS
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context, scope: CoroutineScope): AppDatabase =
        AppDatabase.build(ctx, scope)

    @Provides
    fun provideSongDao(db: AppDatabase): SongDao = db.songDao()

    @Provides @Singleton
    fun provideSongRepository(dao: SongDao, scope: CoroutineScope): SongRepository {
        val repo = SongRepository(dao)
        // Idempotent seed: insertAll uses REPLACE on conflict, so re-seeding bundled
        // songs is safe — user changes to bundled songs (e.g. BPM) are overwritten on
        // app cold start. For Phase 1 that's acceptable; v1.1 can compare and skip.
        scope.launch { repo.upsertAll(SAMPLE_SONGS) }
        return repo
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/data/repo/ app/src/main/java/ph/nextbank/drums/di/
git commit -m "Add SongRepository + Hilt wiring with idempotent seed"
```

### Task 10: SongRepositoryTest (Room in-memory)

**Files:**
- Create: `app/src/test/java/ph/nextbank/drums/data/SongRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ph.nextbank.drums.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ph.nextbank.drums.data.db.AppDatabase
import ph.nextbank.drums.data.repo.SongRepository
import ph.nextbank.drums.data.samples.SAMPLE_SONGS

@RunWith(AndroidJUnit4::class)
class SongRepositoryTest {
    @get:Rule val rule = InstantTaskExecutorRule()
    private lateinit var db: AppDatabase
    private lateinit var repo: SongRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = SongRepository(db.songDao())
    }

    @After fun tearDown() = db.close()

    @Test fun `upsert + observe round-trips sample songs`() = runTest {
        repo.upsertAll(SAMPLE_SONGS)
        val out = repo.observeAll().first()
        assertEquals(5, out.size)
        val nirvana = out.first { it.id == "smells-like-teen-spirit" }
        assertEquals("Nirvana", nirvana.artist)
        assertEquals(116, nirvana.bpm)
        assertEquals(8, nirvana.bars.size)
        assertEquals(16, nirvana.bars[0].size)
    }

    @Test fun `findById returns null for unknown id`() = runTest {
        repo.upsertAll(SAMPLE_SONGS)
        assertNotNull(repo.findById("smells-like-teen-spirit"))
        assertEquals(null, repo.findById("nope"))
    }

    @Test fun `bars round-trip preserves all drum tokens`() = runTest {
        repo.upsertAll(SAMPLE_SONGS)
        val out = repo.findById("smells-like-teen-spirit")!!
        // First bar of CRASH starts with [k, c]
        assertEquals(2, out.bars[0][0].size)
    }
}
```

- [ ] **Step 2: Run test — expect failure (Room test deps may need `robolectric` or proper instrumentation)**

Run from project root: `./gradlew :app:testDebugUnitTest --tests SongRepositoryTest`
Expected at this point: FAIL because Room unit tests on the JVM need Robolectric. If the Robolectric setup is too heavy, **move this test to `app/src/androidTest/`** and run it as an instrumented test against the emulator:

```bash
./gradlew :app:connectedDebugAndroidTest --tests SongRepositoryTest
```

- [ ] **Step 3: If running as androidTest, add Robolectric-free flavor**

If you keep it under `test/`, add to `app/build.gradle.kts`:

```kotlin
testImplementation("org.robolectric:robolectric:4.13")
testImplementation("androidx.test:core:1.6.1")
android { testOptions { unitTests.isIncludeAndroidResources = true } }
```

Annotate the test class with `@org.robolectric.annotation.Config(manifest = Config.NONE)` and run `./gradlew :app:testDebugUnitTest`.

- [ ] **Step 4: Make the test pass**

The implementation already exists from Tasks 6–9. Run again, expect PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/ app/build.gradle.kts
git commit -m "Add SongRepository round-trip test"
```

---

## Milestone 3 — Audio engine (Tasks 11–13)

End state: a unit-testable `SongClock` and a real `DrumSampleBank` that plays a kick when called. Verified via a hidden debug screen wired into MainActivity temporarily.

### Task 11: SongClock (pure logic, fully TDD)

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/SongClock.kt`
- Create: `app/src/test/java/ph/nextbank/drums/audio/SongClockTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ph.nextbank.drums.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongClockTest {

    private fun clock(bpm: Int = 120, totalSlots: Int = 128, slotsPerBeat: Int = 4) =
        SongClock(bpmProvider = { bpm }, totalSlots = totalSlots, slotsPerBeat = slotsPerBeat)

    @Test fun `idle clock reports slot 0 and not playing`() {
        val c = clock()
        assertFalse(c.isPlaying)
        assertEquals(0f, c.currentSlot(nowMs = 0L), 0.0001f)
    }

    @Test fun `play advances slot at bpm rate`() {
        val c = clock(bpm = 120, slotsPerBeat = 4)
        c.play(nowMs = 0L)
        // 120 BPM → 500 ms per beat → 125 ms per 16th-note slot.
        assertEquals(0f, c.currentSlot(nowMs = 0L), 0.0001f)
        assertEquals(1f, c.currentSlot(nowMs = 125L), 0.0001f)
        assertEquals(4f, c.currentSlot(nowMs = 500L), 0.0001f)
        assertEquals(0.5f, c.currentSlot(nowMs = 62L + 1L), 0.05f) // ~halfway through slot 0
    }

    @Test fun `pause freezes slot, resume continues from same slot`() {
        val c = clock(bpm = 120)
        c.play(nowMs = 0L)
        // advance to slot 2 at t=250
        c.pause(nowMs = 250L)
        // time passing while paused must not advance the slot
        assertEquals(2f, c.currentSlot(nowMs = 1_000L), 0.0001f)
        c.play(nowMs = 1_000L)
        // 125ms later, we're 1 slot beyond pause point
        assertEquals(3f, c.currentSlot(nowMs = 1_125L), 0.0001f)
    }

    @Test fun `stop resets slot to 0 and pauses`() {
        val c = clock(bpm = 120)
        c.play(nowMs = 0L)
        c.stop()
        assertFalse(c.isPlaying)
        assertEquals(0f, c.currentSlot(nowMs = 9_999L), 0.0001f)
    }

    @Test fun `slot wraps when reaching totalSlots`() {
        val c = clock(bpm = 240, totalSlots = 16, slotsPerBeat = 4) // 62.5ms per slot
        c.play(nowMs = 0L)
        // At t=1000ms → 16 slots → wraps to 0
        assertEquals(0f, c.currentSlot(nowMs = 1_000L), 0.01f)
        // At t=1062.5ms → 17 slots → 1
        assertEquals(1f, c.currentSlot(nowMs = 1_062L + 1L), 0.05f)
    }

    @Test fun `slotJustEntered fires once per integer slot`() {
        val c = clock(bpm = 120)
        c.play(nowMs = 0L)
        // poll forward; collect which integer slots were "just entered"
        val entered = mutableListOf<Int>()
        for (t in 0..500 step 10) {
            val justEntered = c.slotJustEntered(nowMs = t.toLong())
            if (justEntered != null) entered += justEntered
        }
        // Expected: slots 0, 1, 2, 3, 4 all entered (within 500ms at 125ms/slot)
        assertEquals(listOf(0, 1, 2, 3, 4), entered)
    }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests SongClockTest
```
Expected: FAIL (`SongClock` not defined).

- [ ] **Step 3: Implement `SongClock.kt`**

```kotlin
package ph.nextbank.drums.audio

/**
 * Derives the playhead's `currentSlot` (a floating-point sixteenth-note index)
 * from monotonic wall-clock time. Pure, deterministic, no Android dependencies
 * — `nowMs` is passed in by callers (production callers pass
 * `SystemClock.elapsedRealtime()`).
 *
 * The slot is shared by the visual playhead and the audio scheduler so they
 * always agree about where in the song we are.
 */
class SongClock(
    private val bpmProvider: () -> Int,
    private val totalSlots: Int,
    private val slotsPerBeat: Int = 4,
) {
    @Volatile var isPlaying: Boolean = false
        private set

    /** Time (nowMs) at which the most-recent play() started. */
    private var playStartMs: Long = 0
    /** Slot value at the moment of the most-recent play(). */
    private var slotAtPlayStart: Float = 0f
    /** Slot value frozen during pause. */
    private var pausedSlot: Float = 0f
    /** Last integer slot that we reported as "entered". */
    private var lastReportedSlot: Int = -1

    private val msPerSlot: Float get() = (60_000f / bpmProvider()) / slotsPerBeat

    fun currentSlot(nowMs: Long): Float {
        if (!isPlaying) return pausedSlot
        val elapsed = nowMs - playStartMs
        val raw = slotAtPlayStart + (elapsed.toFloat() / msPerSlot)
        // Wrap inside [0, totalSlots)
        return ((raw % totalSlots) + totalSlots) % totalSlots
    }

    fun play(nowMs: Long) {
        if (isPlaying) return
        slotAtPlayStart = pausedSlot
        playStartMs = nowMs
        isPlaying = true
    }

    fun pause(nowMs: Long) {
        if (!isPlaying) return
        pausedSlot = currentSlot(nowMs)
        isPlaying = false
    }

    fun stop() {
        isPlaying = false
        pausedSlot = 0f
        slotAtPlayStart = 0f
        lastReportedSlot = -1
    }

    /**
     * Returns the integer slot that was just entered between the previous poll
     * and this one, or null if no integer-slot boundary was crossed.
     * Audio scheduling polls this each frame to fire drum hits.
     */
    fun slotJustEntered(nowMs: Long): Int? {
        val nowSlot = currentSlot(nowMs).toInt()
        if (nowSlot != lastReportedSlot) {
            lastReportedSlot = nowSlot
            return nowSlot
        }
        return null
    }

    /** Force the next `slotJustEntered` call to fire for the current slot. */
    fun resetSlotTracking() {
        lastReportedSlot = -1
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests SongClockTest
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/SongClock.kt app/src/test/java/ph/nextbank/drums/audio/
git commit -m "Add SongClock with TDD-driven tests"
```

### Task 12: DrumSampleBank (SoundPool, asset-driven)

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/DrumSampleBank.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/di/AppModule.kt` (provide DrumSampleBank)

- [ ] **Step 1: User drops 9 drum samples + 2 metronome ticks into `app/src/main/assets/samples/`**

Required files (16-bit mono WAV, ≤200 ms each, ~44.1 kHz):
- `kick.wav`, `snare.wav`, `hihat_closed.wav`, `hihat_open.wav`, `crash.wav`, `ride.wav`, `tom_hi.wav`, `tom_mid.wav`, `tom_floor.wav`, `click_high.wav`, `click_low.wav`

Source recommendation (CC0): https://freesound.org → search "drum kit one shot CC0". Pack and bundle into the assets folder. The plan continues regardless — `DrumSampleBank.load()` logs a warning if a sample is missing and `play()` for that token is a no-op.

- [ ] **Step 2: Create `DrumSampleBank.kt`**

```kotlin
package ph.nextbank.drums.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import ph.nextbank.drums.data.model.DrumToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrumSampleBank @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: MutableMap<DrumToken, Int> = mutableMapOf()
    private val loaded: MutableSet<Int> = mutableSetOf()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded += sampleId
            else Log.w(TAG, "SoundPool load failed for sample $sampleId (status=$status)")
        }
        load()
    }

    private fun load() {
        DrumToken.values().forEach { tok ->
            val asset = assetFor(tok) ?: return@forEach
            try {
                val afd = ctx.assets.openFd("samples/$asset")
                val id = pool.load(afd, /* priority = */ 1)
                ids[tok] = id
                afd.close()
            } catch (e: Exception) {
                Log.w(TAG, "Missing sample $asset for $tok: ${e.message}")
            }
        }
    }

    fun play(token: DrumToken, volume: Float = 1f) {
        val id = ids[token] ?: return
        if (id !in loaded) return
        pool.play(id, volume, volume, /* priority = */ 1, /* loop = */ 0, /* rate = */ 1f)
    }

    fun release() {
        pool.release()
    }

    private fun assetFor(t: DrumToken): String? = when (t) {
        DrumToken.KICK -> "kick.wav"
        DrumToken.SNARE -> "snare.wav"
        DrumToken.HIHAT_CLOSED -> "hihat_closed.wav"
        DrumToken.HIHAT_OPEN -> "hihat_open.wav"
        DrumToken.CRASH -> "crash.wav"
        DrumToken.RIDE -> "ride.wav"
        DrumToken.TOM_HI -> "tom_hi.wav"
        DrumToken.TOM_MID -> "tom_mid.wav"
        DrumToken.TOM_FLOOR -> "tom_floor.wav"
    }

    companion object { private const val TAG = "DrumSampleBank" }
}
```

- [ ] **Step 3: Provide it from Hilt**

In `AppModule.kt`, add:

```kotlin
@Provides @Singleton
fun provideDrumSampleBank(@ApplicationContext ctx: Context): DrumSampleBank = DrumSampleBank(ctx)
```

(Hilt can construct it directly via `@Inject constructor`, so this provider is optional — keep it explicit for clarity.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/DrumSampleBank.kt app/src/main/java/ph/nextbank/drums/di/AppModule.kt
git commit -m "Add DrumSampleBank with SoundPool loader"
```

### Task 13: Metronome

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/Metronome.kt`

- [ ] **Step 1: Create `Metronome.kt`**

```kotlin
package ph.nextbank.drums.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Metronome @Inject constructor(@ApplicationContext ctx: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()

    private val high: Int = runCatching {
        ctx.assets.openFd("samples/click_high.wav").use { pool.load(it, 1) }
    }.getOrDefault(0)

    private val low: Int = runCatching {
        ctx.assets.openFd("samples/click_low.wav").use { pool.load(it, 1) }
    }.getOrDefault(0)

    /** Beat index 0 plays the downbeat (high click). Other beats play the low click. */
    fun click(beatIndex: Int) {
        val id = if (beatIndex == 0) high else low
        if (id != 0) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/Metronome.kt
git commit -m "Add Metronome click player"
```

---

## Milestone 4 — Drum staff renderer (Tasks 14–17)

End state: a Compose `DrumStaff` component renders any bar pattern correctly, and `DrumStaffStack` shows multi-line scores with playhead following.

### Task 14: DrumStaff math (pure, fully TDD)

The math is identical to `design-reference/drum-staff.jsx` — y-positions per drum token, x-positions per slot, beam grouping. Port it as pure functions before drawing anything.

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/components/DrumStaffLayout.kt`
- Create: `app/src/test/java/ph/nextbank/drums/ui/components/DrumStaffLayoutTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package ph.nextbank.drums.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.nextbank.drums.data.model.DrumToken.*

class DrumStaffLayoutTest {

    private fun layout(width: Float = 300f, showClef: Boolean = true) = DrumStaffLayout(
        width = width, height = 100f, top = 28f, lineGap = 9f,
        showClef = showClef, staffPaddingX = 12f, barCount = 2, slotsPerBar = 16,
    )

    @Test fun `staff has 5 lines, 9 px apart starting at top=28`() {
        val l = layout()
        assertEquals(28f, l.staffTopY, 0.001f)
        assertEquals(28f + 4 * 9f, l.staffBottomY, 0.001f)
        assertEquals(listOf(28f, 37f, 46f, 55f, 64f), l.staffLines)
    }

    @Test fun `snare sits on middle line`() {
        val l = layout()
        assertEquals(l.staffLines[2], l.yOf(SNARE), 0.001f)
    }

    @Test fun `kick sits below the bottom line`() {
        val l = layout()
        assertTrue(l.yOf(KICK) > l.staffBottomY)
    }

    @Test fun `slot 0 of bar 0 is just inside the clef`() {
        val l = layout(width = 300f, showClef = true)
        val x = l.slotX(globalSlot = 0)
        // clefW=32, padX=12 → innerX0=44. innerW=300-32-2*12=244. slotW=244/32. center of slot 0 = 44 + 0.5*slotW
        val slotW = (300f - 32f - 2 * 12f) / 32f
        assertEquals(44f + 0.5f * slotW, x, 0.001f)
    }

    @Test fun `playhead x is linear in currentBeat`() {
        val l = layout(width = 300f, showClef = false)
        // showClef=false → clefW=0, innerX0=12, innerW=300-2*12=276
        val phAtStart = l.playheadX(currentBeat = 0f)
        val phAtEnd = l.playheadX(currentBeat = 32f) // end of 2 bars × 16 slots
        assertEquals(12f, phAtStart, 0.001f)
        assertEquals(12f + 276f, phAtEnd, 0.001f)
        // halfway
        assertEquals(12f + 138f, l.playheadX(currentBeat = 16f), 0.001f)
    }

    @Test fun `beam groups join adjacent hi-hat 8th notes within same beat`() {
        val l = layout()
        // Two adjacent hi-hat hits in the same beat (slots 0 and 2 of bar 0)
        val bar = MutableList(16) { emptyList<ph.nextbank.drums.data.model.DrumToken>() }
        bar[0] = listOf(HIHAT_CLOSED)
        bar[2] = listOf(HIHAT_CLOSED)
        val stems = l.upStems(listOf(bar))
        val beams = l.beamGroups(stems)
        assertEquals(1, beams.size)
        assertEquals(2, beams[0].size)
    }

    @Test fun `beam groups split across beat boundaries`() {
        val l = layout()
        // Hi-hat at slot 2 (still beat 0) and slot 4 (beat 1) — should NOT beam.
        val bar = MutableList(16) { emptyList<ph.nextbank.drums.data.model.DrumToken>() }
        bar[2] = listOf(HIHAT_CLOSED)
        bar[4] = listOf(HIHAT_CLOSED)
        val stems = l.upStems(listOf(bar))
        val beams = l.beamGroups(stems)
        assertEquals(0, beams.size)
    }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests DrumStaffLayoutTest
```

- [ ] **Step 3: Implement `DrumStaffLayout.kt`** (port from `drum-staff.jsx`)

```kotlin
package ph.nextbank.drums.ui.components

import ph.nextbank.drums.data.model.DrumToken

data class UpStem(val x: Float, val y1: Float, val y2: Float, val barIndex: Int, val slotIndex: Int)
data class DownStem(val x: Float, val y1: Float, val y2: Float)

class DrumStaffLayout(
    val width: Float,
    val height: Float,
    val top: Float = 28f,
    val lineGap: Float = 9f,
    val showClef: Boolean,
    val staffPaddingX: Float = 12f,
    val barCount: Int,
    val slotsPerBar: Int = 16,
    val slotsPerBeat: Int = 4,
) {
    val clefW: Float = if (showClef) 32f else 0f
    val innerX0: Float = clefW + staffPaddingX
    val innerX1: Float = width - staffPaddingX
    val innerW: Float = innerX1 - innerX0
    val slotsTotal: Int = slotsPerBar * barCount
    val slotW: Float = innerW / slotsTotal
    val barW: Float = slotW * slotsPerBar

    val staffLines: List<Float> = (0..4).map { top + it * lineGap }
    val staffTopY: Float = staffLines.first()
    val staffBottomY: Float = staffLines.last()

    fun slotX(globalSlot: Int): Float = innerX0 + (globalSlot + 0.5f) * slotW

    fun playheadX(currentBeat: Float): Float =
        innerX0 + (currentBeat / slotsTotal) * innerW

    fun yOf(token: DrumToken): Float = when (token) {
        DrumToken.HIHAT_CLOSED -> staffTopY - 12f
        DrumToken.HIHAT_OPEN -> staffTopY - 12f
        DrumToken.CRASH -> staffTopY - 20f
        DrumToken.RIDE -> staffTopY - 16f
        DrumToken.TOM_HI -> staffLines[1] - lineGap / 2f
        DrumToken.TOM_MID -> staffLines[2] - lineGap / 2f
        DrumToken.SNARE -> staffLines[2]
        DrumToken.TOM_FLOOR -> staffLines[3] + lineGap / 2f
        DrumToken.KICK -> staffLines[4] + lineGap
    }

    /** Stems pointing up from top-row hits (cymbals & hi-hat) or from snare/toms without kick. */
    fun upStems(bars: List<List<List<DrumToken>>>): List<UpStem> {
        val out = mutableListOf<UpStem>()
        bars.forEachIndexed { bi, bar ->
            bar.forEachIndexed { si, slot ->
                if (slot.isEmpty()) return@forEachIndexed
                val globalSlot = bi * slotsPerBar + si
                val x = slotX(globalSlot)
                val hasTop = slot.any { it in TOP_ROW }
                val hasMid = slot.any { it in MID_ROW }
                val hasKick = DrumToken.KICK in slot
                if (hasTop) {
                    val topY = slot.filter { it in TOP_ROW }.minOf(::yOf)
                    out += UpStem(x, topY, topY - 16f, bi, si)
                } else if (hasMid && !hasKick) {
                    val midY = if (DrumToken.SNARE in slot) yOf(DrumToken.SNARE)
                               else yOf(slot.first { it in MID_ROW })
                    out += UpStem(x, midY, midY - 22f, bi, si)
                }
            }
        }
        return out
    }

    fun downStems(bars: List<List<List<DrumToken>>>): List<DownStem> {
        val out = mutableListOf<DownStem>()
        bars.forEachIndexed { bi, bar ->
            bar.forEachIndexed { si, slot ->
                if (DrumToken.KICK !in slot) return@forEachIndexed
                val x = slotX(bi * slotsPerBar + si)
                out += DownStem(x, yOf(DrumToken.KICK), yOf(DrumToken.KICK) + 16f)
            }
        }
        return out
    }

    /** Adjacent up-stems within the same beat (group of 4 slots) get beamed. */
    fun beamGroups(stems: List<UpStem>): List<List<UpStem>> {
        val groups = mutableListOf<List<UpStem>>()
        var current = mutableListOf<UpStem>()
        stems.forEach { s ->
            if (current.isEmpty()) current += s
            else {
                val last = current.last()
                val sameBar = s.barIndex == last.barIndex
                val sameBeat = last.slotIndex / slotsPerBeat == s.slotIndex / slotsPerBeat
                val adjacent = s.slotIndex - last.slotIndex <= 2
                if (sameBar && sameBeat && adjacent) current += s
                else {
                    if (current.size > 1) groups += current.toList()
                    current = mutableListOf(s)
                }
            }
        }
        if (current.size > 1) groups += current.toList()
        return groups
    }

    companion object {
        private val TOP_ROW = setOf(
            DrumToken.HIHAT_CLOSED, DrumToken.HIHAT_OPEN, DrumToken.CRASH, DrumToken.RIDE
        )
        private val MID_ROW = setOf(
            DrumToken.SNARE, DrumToken.TOM_HI, DrumToken.TOM_MID, DrumToken.TOM_FLOOR
        )
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests DrumStaffLayoutTest
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/components/DrumStaffLayout.kt app/src/test/java/ph/nextbank/drums/ui/components/
git commit -m "Add DrumStaffLayout pure math with TDD tests"
```

### Task 15: DrumStaff Composable (Canvas renderer)

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/components/DrumStaff.kt`

- [ ] **Step 1: Create `DrumStaff.kt`**

```kotlin
package ph.nextbank.drums.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.ui.theme.DrumsColors

/**
 * Renders one or more drum-staff bars in a single horizontal row, with optional
 * percussion clef, time signature, and a violet playhead at `currentSlot`.
 *
 * Layout math lives in [DrumStaffLayout]; this Composable is the painter.
 */
@Composable
fun DrumStaff(
    bars: List<List<List<DrumToken>>>,
    currentSlot: Float = 0f,
    showClef: Boolean = true,
    showPlayhead: Boolean = true,
    timeSig: Pair<Int, Int> = 4 to 4,
    widthDp: Int = 320,
    heightDp: Int = 100,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.dp.toPx() }
    val heightPx = with(density) { heightDp.dp.toPx() }
    val barCount = bars.size

    val layout = remember(widthPx, heightPx, showClef, barCount) {
        DrumStaffLayout(
            width = widthPx,
            height = heightPx,
            showClef = showClef,
            barCount = barCount,
        )
    }
    val upStems = remember(bars) { layout.upStems(bars) }
    val downStems = remember(bars) { layout.downStems(bars) }
    val beams = remember(upStems) { layout.beamGroups(upStems) }

    Canvas(modifier = modifier.size(widthDp.dp, heightDp.dp)) {
        val lineCol = DrumsColors.StaffLine
        val noteCol = DrumsColors.Note
        val accent = DrumsColors.Accent
        val playheadCol = DrumsColors.Playhead

        // staff lines
        layout.staffLines.forEach { yy ->
            drawLine(
                color = lineCol,
                start = Offset(layout.clefW, yy),
                end = Offset(widthPx, yy),
                strokeWidth = 0.9f,
            )
        }

        // percussion clef
        if (showClef) {
            val tx = layout.clefW - 12f
            drawRect(
                color = lineCol,
                topLeft = Offset(tx, layout.staffTopY),
                size = androidx.compose.ui.geometry.Size(4f, layout.staffBottomY - layout.staffTopY),
            )
            drawRect(
                color = lineCol,
                topLeft = Offset(tx + 6f, layout.staffTopY),
                size = androidx.compose.ui.geometry.Size(1.5f, layout.staffBottomY - layout.staffTopY),
            )
            // Time signature is text — drawn via native canvas
            drawIntoCanvas { c ->
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#3A3A44")
                    isAntiAlias = true
                    textSize = 17f
                    typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
                }
                c.nativeCanvas.drawText("${timeSig.first}", layout.clefW + 2f, layout.staffTopY + 16f, p)
                c.nativeCanvas.drawText("${timeSig.second}", layout.clefW + 2f, layout.staffBottomY - 1f, p)
            }
        }

        // bar lines
        for (bi in 0..barCount) {
            val x = layout.innerX0 + bi * layout.barW
            drawLine(lineCol, Offset(x, layout.staffTopY), Offset(x, layout.staffBottomY), 0.9f)
        }

        // stems
        upStems.forEach { s ->
            drawLine(noteCol, Offset(s.x + 4f, s.y1), Offset(s.x + 4f, s.y2), 1.1f)
        }
        downStems.forEach { s ->
            drawLine(noteCol, Offset(s.x - 4f, s.y1), Offset(s.x - 4f, s.y2), 1.1f)
        }

        // beams
        beams.forEach { group ->
            val x1 = group.first().x + 4f
            val x2 = group.last().x + 4f
            val yy = group.minOf { it.y2 }
            drawRect(noteCol, topLeft = Offset(x1, yy), size = androidx.compose.ui.geometry.Size(x2 - x1, 2.5f))
        }

        // noteheads
        bars.forEachIndexed { bi, bar ->
            bar.forEachIndexed { si, slot ->
                if (slot.isEmpty()) return@forEachIndexed
                val cx = layout.slotX(bi * layout.slotsPerBar + si)
                slot.forEach { token ->
                    val cy = layout.yOf(token)
                    when (token) {
                        DrumToken.HIHAT_CLOSED -> drawNoteX(cx, cy, noteCol)
                        DrumToken.HIHAT_OPEN -> drawOpenHat(cx, cy, noteCol)
                        DrumToken.CRASH, DrumToken.RIDE -> drawNoteX(cx, cy, accent)
                        else -> drawNoteOval(cx, cy, noteCol)
                    }
                }
            }
        }

        // playhead
        if (showPlayhead && currentSlot in 0f..layout.slotsTotal.toFloat()) {
            val phX = layout.playheadX(currentSlot)
            drawLine(
                color = playheadCol,
                start = Offset(phX, layout.staffTopY - 22f),
                end = Offset(phX, layout.staffBottomY + 14f),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round,
            )
            drawCircle(playheadCol, radius = 3f, center = Offset(phX, layout.staffTopY - 24f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNoteOval(cx: Float, cy: Float, color: Color) {
    rotate(degrees = -22f, pivot = Offset(cx, cy)) {
        drawOval(
            color = color,
            topLeft = Offset(cx - 4.2f, cy - 3.1f),
            size = androidx.compose.ui.geometry.Size(8.4f, 6.2f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNoteX(cx: Float, cy: Float, color: Color) {
    drawLine(color, Offset(cx - 4f, cy - 4f), Offset(cx + 4f, cy + 4f), 1.6f, cap = StrokeCap.Round)
    drawLine(color, Offset(cx - 4f, cy + 4f), Offset(cx + 4f, cy - 4f), 1.6f, cap = StrokeCap.Round)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOpenHat(cx: Float, cy: Float, color: Color) {
    drawNoteX(cx, cy, color)
    drawCircle(color, radius = 2.8f, center = Offset(cx, cy - 8f), style = Stroke(width = 1.6f))
}
```

- [ ] **Step 2: Add a Preview at the bottom of `DrumStaff.kt`** (the user can visually verify by running the preview in Android Studio without a full emulator launch)

```kotlin
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF131318)
@Composable
private fun DrumStaffPreview() {
    val bars = ph.nextbank.drums.data.samples.SAMPLE_SONGS[0].bars.take(2)
    ph.nextbank.drums.ui.theme.DrumsTheme {
        DrumStaff(bars = bars, currentSlot = 11f, widthDp = 340, heightDp = 110)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/components/DrumStaff.kt
git commit -m "Add DrumStaff Compose renderer with @Preview"
```

### Task 16: DrumStaffStack (multi-line)

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/components/DrumStaffStack.kt`

- [ ] **Step 1: Create `DrumStaffStack.kt`**

```kotlin
package ph.nextbank.drums.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.data.model.DrumToken

/**
 * Renders a full song as a vertical stack of drum-staff lines, [barsPerLine] bars
 * per line. The current line is alpha 1.0; others fade to 0.55. Only the current
 * line shows the playhead.
 */
@Composable
fun DrumStaffStack(
    bars: List<List<List<DrumToken>>>,
    currentSlot: Float,
    timeSig: Pair<Int, Int> = 4 to 4,
    barsPerLine: Int = 2,
    widthDp: Int = 340,
    lineHeightDp: Int = 96,
    modifier: Modifier = Modifier,
) {
    val slotsPerBar = bars.firstOrNull()?.size ?: 16
    val totalBars = bars.size
    val lineCount = (totalBars + barsPerLine - 1) / barsPerLine
    val currentBar = (currentSlot / slotsPerBar).toInt()
    val currentLine = currentBar / barsPerLine

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (li in 0 until lineCount) {
            val startBar = li * barsPerLine
            val lineBars = bars.subList(startBar, minOf(startBar + barsPerLine, totalBars))
            val isCurrent = li == currentLine
            val targetAlpha = if (isCurrent) 1f else 0.55f
            val alpha by animateFloatAsState(targetAlpha, tween(250), label = "lineAlpha")
            val localSlot = if (isCurrent) currentSlot - startBar * slotsPerBar else -1f
            DrumStaff(
                bars = lineBars,
                currentSlot = localSlot,
                showClef = li == 0,
                showPlayhead = isCurrent,
                timeSig = timeSig,
                widthDp = widthDp,
                heightDp = lineHeightDp,
                modifier = Modifier.alpha(alpha),
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/components/DrumStaffStack.kt
git commit -m "Add multi-line DrumStaffStack component"
```

### Task 17: Hook the staff up in a temporary debug screen

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/MainActivity.kt` (replace `PlaceholderRoot` with a `DebugPlayer` for now)

- [ ] **Step 1: Replace `PlaceholderRoot` in `MainActivity.kt` with:**

```kotlin
@Composable
private fun DebugPlayer() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bank = remember { ph.nextbank.drums.audio.DrumSampleBank(context) }
    val song = ph.nextbank.drums.data.samples.SAMPLE_SONGS[0]
    val totalSlots = song.bars.size * (song.bars[0].size)
    val clock = remember { ph.nextbank.drums.audio.SongClock({ song.bpm }, totalSlots) }
    var playing by remember { mutableStateOf(false) }
    var slot by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            withFrameNanos { nano ->
                val now = nano / 1_000_000L
                slot = clock.currentSlot(now)
                clock.slotJustEntered(now)?.let { idx ->
                    val barIdx = idx / song.bars[0].size
                    val slotIdx = idx % song.bars[0].size
                    song.bars.getOrNull(barIdx)?.get(slotIdx)?.forEach(bank::play)
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(DrumsColors.Bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Debug player", color = DrumsColors.Text, style = DrumsType.screenTitle)
        ph.nextbank.drums.ui.components.DrumStaffStack(
            bars = song.bars,
            currentSlot = slot,
            timeSig = song.timeSig,
        )
        androidx.compose.material3.Button(
            onClick = {
                val now = android.os.SystemClock.elapsedRealtime()
                if (playing) clock.pause(now) else clock.play(now)
                playing = !playing
            },
        ) { Text(if (playing) "Pause" else "Play") }
    }
}
```

(Add the necessary imports: `androidx.compose.foundation.layout.*`, `androidx.compose.runtime.*`, `androidx.compose.material3.Text`, etc.)

- [ ] **Step 2: Verification checkpoint — user runs the app**

Open in Android Studio, run on emulator. Expected: violet "Debug player" headline, then the 8-bar drum staff for Smells Like Teen Spirit, then a Play button. Hitting Play starts drum-sample playback (if WAVs are bundled) and the playhead sweeps along the first 2-bar line, then the second 2-bar line fades in as it becomes current.

If the playhead drifts visibly from the audio (or audio doesn't fire), pause Milestone progress and debug before moving on.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/MainActivity.kt
git commit -m "Add temporary debug player to verify staff + clock + audio"
```

---

## Milestone 5 — Production screens (Tasks 18–26)

End state: all 5 screens render correctly per the handoff and are wired into a NavHost. The debug screen is removed.

### Task 18: CoverArt + PillTab + TransportButton + DrumHitChips components

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/components/CoverArt.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/components/PillTab.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/components/TransportButton.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/components/DrumHitChips.kt`

- [ ] **Step 1: Create `CoverArt.kt`** (procedural gradient + diagonal stripes + monogram)

```kotlin
package ph.nextbank.drums.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.ui.theme.DrumsColors

@Composable
fun CoverArt(initials: String, sizeDp: Dp, cornerDp: Dp = 12.dp, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(cornerDp)),
    ) {
        // gradient
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(DrumsColors.CoverGradFrom, DrumsColors.CoverGradTo),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
        )
        // diagonal stripes at 25% white
        rotate(degrees = -25f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            val stripeW = size.width / 8f
            for (i in -2..10) {
                drawRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(i * stripeW * 1.5f, -size.height),
                    size = Size(stripeW * 0.4f, size.height * 3f),
                )
            }
        }
        // monogram is drawn by an overlay Text composable in the call site —
        // keep this Canvas pure background.
    }
}
```

Add a wrapper `Composable` that overlays the initials in `Box` from the call site (or use `drawIntoCanvas` for text). Use `Box` overlay for simplicity:

```kotlin
@Composable
fun CoverArtWithMonogram(initials: String, sizeDp: Dp, cornerDp: Dp = 12.dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier,
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        CoverArt(initials, sizeDp, cornerDp)
        androidx.compose.material3.Text(
            text = initials,
            color = Color.White,
            style = ph.nextbank.drums.ui.theme.DrumsType.cardTitle,
        )
    }
}
```

- [ ] **Step 2: Create `PillTab.kt`** (the tab row from Library)

```kotlin
package ph.nextbank.drums.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun PillTab(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (active) DrumsColors.Text else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (!active) Modifier.border(1.dp, DrumsColors.Line, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = if (active) DrumsColors.Bg else DrumsColors.Dim,
            style = DrumsType.body.copy(
                fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
        )
    }
}
```

- [ ] **Step 3: Create `TransportButton.kt`** (FAB / primary play / ghost icon variants)

```kotlin
package ph.nextbank.drums.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import ph.nextbank.drums.ui.theme.DrumsColors

enum class TransportStyle { PRIMARY, GHOST, FAB }

@Composable
fun TransportButton(
    icon: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    style: TransportStyle = TransportStyle.GHOST,
    sizeDp: Dp = when (style) {
        TransportStyle.PRIMARY -> 64.dp
        TransportStyle.FAB -> 56.dp
        TransportStyle.GHOST -> 42.dp
    },
    modifier: Modifier = Modifier,
) {
    val bg = when (style) {
        TransportStyle.PRIMARY, TransportStyle.FAB -> DrumsColors.Accent
        TransportStyle.GHOST -> Color.Transparent
    }
    val iconCol = if (style == TransportStyle.GHOST) DrumsColors.Text else DrumsColors.AccentOn
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(bg)
            .then(if (style == TransportStyle.GHOST) Modifier.border(1.dp, DrumsColors.Line, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = icon, contentDescription = contentDescription, tint = iconCol)
    }
}
```

For icons: use `androidx.compose.material.icons.Icons.Filled.PlayArrow`, `Icons.Filled.Pause`, `Icons.Filled.Stop`, `Icons.Filled.Add`, `Icons.Filled.ArrowBack`, `Icons.Filled.MoreVert`, `Icons.Outlined.Loop`, `Icons.Outlined.Speed` (add `implementation("androidx.compose.material:material-icons-extended")` to `app/build.gradle.kts` if not already pulled by Material 3 BOM). Pass them via `rememberVectorPainter`.

- [ ] **Step 4: Create `DrumHitChips.kt`**

```kotlin
package ph.nextbank.drums.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

private val CHIP_ORDER: List<Pair<String, DrumToken>> = listOf(
    "KICK" to DrumToken.KICK,
    "SNR" to DrumToken.SNARE,
    "HH" to DrumToken.HIHAT_CLOSED,
    "CRSH" to DrumToken.CRASH,
    "RIDE" to DrumToken.RIDE,
    "TOM1" to DrumToken.TOM_HI,
    "TOM2" to DrumToken.TOM_MID,
    "FLR" to DrumToken.TOM_FLOOR,
)

@Composable
fun DrumHitChips(activeTokens: Set<DrumToken>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CHIP_ORDER.forEach { (label, token) ->
            val active = token in activeTokens
            Text(
                text = label,
                color = if (active) DrumsColors.ChipActiveText else DrumsColors.Dim,
                style = DrumsType.drumHitChip,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) DrumsColors.ChipActiveBg else DrumsColors.ChipBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/components/CoverArt.kt \
        app/src/main/java/ph/nextbank/drums/ui/components/PillTab.kt \
        app/src/main/java/ph/nextbank/drums/ui/components/TransportButton.kt \
        app/src/main/java/ph/nextbank/drums/ui/components/DrumHitChips.kt
git commit -m "Add cover art, pill tabs, transport buttons, drum-hit chips"
```

### Task 19: Navigation host + Route sealed type

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/nav/Route.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/nav/NavGraph.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/MainActivity.kt` (replace debug screen with `NavGraph()`)

- [ ] **Step 1: Create `Route.kt`**

```kotlin
package ph.nextbank.drums.ui.nav

sealed class Route(val path: String) {
    data object Library : Route("library")
    data object Upload : Route("upload")
    data class Player(val songId: String) : Route("player/$songId") {
        companion object { const val PATH = "player/{songId}" }
    }
    data class SongDetail(val songId: String) : Route("song/$songId") {
        companion object { const val PATH = "song/{songId}" }
    }
    data class Practice(val songId: String) : Route("practice/$songId") {
        companion object { const val PATH = "practice/{songId}" }
    }
}
```

- [ ] **Step 2: Create `NavGraph.kt`**

```kotlin
package ph.nextbank.drums.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ph.nextbank.drums.ui.library.LibraryScreen
import ph.nextbank.drums.ui.player.PlayerScreen
import ph.nextbank.drums.ui.practice.PracticeScreen
import ph.nextbank.drums.ui.song_detail.SongDetailScreen
import ph.nextbank.drums.ui.upload.UploadScreen

@Composable
fun NavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Route.Library.path) {
        composable(Route.Library.path) {
            LibraryScreen(
                onSongClick = { id -> nav.navigate(Route.Player(id).path) },
                onSongLongPress = { id -> nav.navigate(Route.SongDetail(id).path) },
                onAddClick = { nav.navigate(Route.Upload.path) },
            )
        }
        composable(Route.Upload.path) {
            UploadScreen(onBack = { nav.popBackStack() })
        }
        composable(
            Route.Player.PATH,
            arguments = listOf(navArgument("songId") { type = NavType.StringType }),
        ) { entry ->
            PlayerScreen(
                songId = entry.arguments!!.getString("songId")!!,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Route.SongDetail.PATH,
            arguments = listOf(navArgument("songId") { type = NavType.StringType }),
        ) { entry ->
            SongDetailScreen(
                songId = entry.arguments!!.getString("songId")!!,
                onBack = { nav.popBackStack() },
                onStartReading = { id -> nav.navigate(Route.Player(id).path) },
                onStartPractice = { id -> nav.navigate(Route.Practice(id).path) },
            )
        }
        composable(
            Route.Practice.PATH,
            arguments = listOf(navArgument("songId") { type = NavType.StringType }),
        ) { entry ->
            PracticeScreen(
                songId = entry.arguments!!.getString("songId")!!,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
```

- [ ] **Step 3: Update `MainActivity.kt`** — replace `DebugPlayer` with `NavGraph()`:

```kotlin
setContent {
    DrumsTheme { NavGraph() }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/nav/ app/src/main/java/ph/nextbank/drums/MainActivity.kt
git commit -m "Add nav host wiring all 5 screens"
```

### Task 20: LibraryScreen + LibraryViewModel

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/library/LibraryViewModel.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/library/LibraryScreen.kt`

- [ ] **Step 1: Create `LibraryViewModel.kt`**

```kotlin
package ph.nextbank.drums.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(repo: SongRepository) : ViewModel() {
    val songs: StateFlow<List<Song>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

- [ ] **Step 2: Create `LibraryScreen.kt`** (per `handoff.md §01 · Home (Library)`)

```kotlin
package ph.nextbank.drums.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.ui.components.*
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun LibraryScreen(
    onSongClick: (String) -> Unit,
    onSongLongPress: (String) -> Unit,
    onAddClick: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val songs by vm.songs.collectAsState()
    var tab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("All") }

    Box(Modifier.fillMaxSize().background(DrumsColors.Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Header row
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("LIBRARY", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
                    Spacer(Modifier.height(4.dp))
                    Text("Your kit", color = DrumsColors.Text, style = DrumsType.screenTitle)
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.4.dp, DrumsColors.Line, RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.MoreVert, contentDescription = null, tint = DrumsColors.Text) }
            }

            Spacer(Modifier.height(14.dp))

            // Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All ${songs.size}", "Recent", "Spotify").forEach { label ->
                    PillTab(label = label, active = (tab == label.takeWhile { it != ' ' } || (tab == "All" && label.startsWith("All"))),
                            onClick = { tab = label.takeWhile { it != ' ' } })
                }
            }

            Spacer(Modifier.height(14.dp))

            // "Continue practicing" hero card
            val continueSong = songs.firstOrNull()
            if (continueSong != null) {
                ContinueCard(continueSong, onClick = { onSongClick(continueSong.id) })
                Spacer(Modifier.height(14.dp))
            }

            Text("ALL SONGS", color = DrumsColors.Dim, style = DrumsType.allCapsLabel,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = { onSongClick(song.id) },
                        onLongPress = { onSongLongPress(song.id) },
                    )
                }
            }
        }

        // FAB
        TransportButton(
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = Icons.Filled.Add),
            contentDescription = "Add song",
            onClick = onAddClick,
            style = TransportStyle.FAB,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 50.dp),
        )
    }
}

@Composable
private fun ContinueCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DrumsColors.Surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArtWithMonogram(initials = song.coverInitials, sizeDp = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("⟶ CONTINUE", color = DrumsColors.Accent, style = DrumsType.allCapsLabel)
                Text(song.title, color = DrumsColors.Text, style = DrumsType.cardTitle, maxLines = 1)
                Text("${song.artist} · ${song.bpm} BPM", color = DrumsColors.Dim, style = DrumsType.caption)
            }
        }
        // mini staff preview, slot ~11
        ph.nextbank.drums.ui.components.DrumStaff(
            bars = song.bars.take(2),
            currentSlot = 11f,
            showClef = false,
            widthDp = 300,
            heightDp = 68,
        )
        // Transport (static for now)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.height(4.dp).weight(1f).clip(RoundedCornerShape(2.dp)).background(DrumsColors.BarTrack),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.34f).background(DrumsColors.Accent))
            }
            Text("1:14 / 3:32", color = DrumsColors.Dim, style = DrumsType.caption)
            TransportButton(
                icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = androidx.compose.material.icons.Icons.Filled.PlayArrow),
                contentDescription = "Play",
                onClick = onClick,
                style = TransportStyle.PRIMARY,
                sizeDp = 36.dp,
            )
        }
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit, onLongPress: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .drawBottomBorder(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverArtWithMonogram(initials = song.coverInitials, sizeDp = 40.dp, cornerDp = 8.dp)
        Column(Modifier.weight(1f)) {
            Text(song.title, color = DrumsColors.Text, style = DrumsType.rowTitle, maxLines = 1)
            Text(
                text = "${song.artist} · ${song.bpm}BPM · ${lastPlayedLabel(song)}",
                color = DrumsColors.Dim,
                style = DrumsType.caption,
            )
        }
        Icon(Icons.Filled.MoreVert, contentDescription = null, tint = DrumsColors.Dim,
            modifier = Modifier.size(14.dp))
    }
}

private fun lastPlayedLabel(song: Song): String =
    if (song.lastPlayed == null) "Imported"
    else "Last played" // Phase 1 keeps it short; relative dates can be added later.

private fun Modifier.drawBottomBorder(): Modifier = drawBehind {
    drawLine(
        color = DrumsColors.Line,
        start = androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f),
        end = androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f),
        strokeWidth = 1f,
    )
}
```

Required imports add: `androidx.compose.foundation.combinedClickable`, `androidx.compose.foundation.layout.drawBehind` (via `Modifier.drawBehind`), `androidx.compose.material.icons.filled.PlayArrow`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/library/
git commit -m "Add LibraryScreen + ViewModel"
```

### Task 21: UploadScreen (stubbed import paths)

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/upload/UploadViewModel.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/upload/UploadScreen.kt`

- [ ] **Step 1: Create `UploadViewModel.kt` (minimal — no state for v1)**

```kotlin
package ph.nextbank.drums.ui.upload

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UploadViewModel @Inject constructor() : ViewModel()
```

- [ ] **Step 2: Create `UploadScreen.kt`** (per `handoff.md §02 · Upload`)

Structural skeleton — header, intro, drop zone, source rows, processing card. All interactive elements show a Toast "Coming in a future update".

```kotlin
package ph.nextbank.drums.ui.upload

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun UploadScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    fun comingSoon() = Toast.makeText(ctx, "Coming in a future update", Toast.LENGTH_SHORT).show()

    Column(
        Modifier
            .fillMaxSize()
            .background(DrumsColors.Bg)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, DrumsColors.Line, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DrumsColors.Text) }
            Spacer(Modifier.width(12.dp))
            Text("Add a song", color = DrumsColors.Text, style = DrumsType.sectionTitle)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Drop in sheet music or pick a track. We'll detect tempo and align the playhead automatically.",
            color = DrumsColors.Dim, style = DrumsType.body,
        )

        Spacer(Modifier.height(16.dp))
        // Drop zone (stubbed)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .border(2.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
                .clickable { comingSoon() }
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(DrumsColors.Accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Color.White) }
            Text("Drop file here", color = DrumsColors.Text, style = DrumsType.cardTitle)
            Text("PDF · PNG · JPG · up to 20MB", color = DrumsColors.Dim, style = DrumsType.caption)
        }

        Spacer(Modifier.height(14.dp))
        Text("OR PICK A SOURCE", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
        Spacer(Modifier.height(8.dp))

        SourceRow(Icons.Filled.CameraAlt, true, "Take a photo", "Snap sheet music with your camera") { comingSoon() }
        Spacer(Modifier.height(8.dp))
        SourceRow(Icons.Filled.AttachFile, false, "Choose PDF or image", "Pick from your files") { comingSoon() }
        Spacer(Modifier.height(8.dp))
        SourceRow(Icons.Filled.LibraryMusic, false, "Connect Spotify", "Drum along to tracks you're playing") { comingSoon() }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, accentIcon: Boolean, title: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DrumsColors.Surface)
            .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (accentIcon) DrumsColors.Accent else DrumsColors.Surface2),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (accentIcon) Color.White else DrumsColors.Text) }
        Column(Modifier.weight(1f)) {
            Text(title, color = DrumsColors.Text, style = DrumsType.cardTitle)
            Text(sub, color = DrumsColors.Dim, style = DrumsType.caption)
        }
        Text("›", color = DrumsColors.Dim, style = DrumsType.cardTitle)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/upload/
git commit -m "Add UploadScreen with stubbed import paths"
```

### Task 22: PlayerScreen + PlayerViewModel

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/player/PlayerScreen.kt`

- [ ] **Step 1: Create `PlayerViewModel.kt`**

```kotlin
package ph.nextbank.drums.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.nextbank.drums.audio.DrumSampleBank
import ph.nextbank.drums.audio.SongClock
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import javax.inject.Inject

data class PlayerUiState(
    val song: Song? = null,
    val playing: Boolean = false,
    val currentSlot: Float = 0f,
    val metronomeOn: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repo: SongRepository,
    val bank: DrumSampleBank,
    handle: SavedStateHandle,
) : ViewModel() {
    private val songId: String = handle.get<String>("songId")!!

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    var clock: SongClock? = null
        private set

    init {
        viewModelScope.launch {
            val s = repo.findById(songId) ?: return@launch
            clock = SongClock(
                bpmProvider = { _state.value.song?.bpm ?: s.bpm },
                totalSlots = s.totalBars * s.slotsPerBar,
            )
            _state.value = _state.value.copy(song = s)
        }
    }

    fun togglePlay(nowMs: Long) {
        val c = clock ?: return
        if (c.isPlaying) c.pause(nowMs) else c.play(nowMs)
        _state.value = _state.value.copy(playing = c.isPlaying)
    }

    fun stop() {
        clock?.stop()
        _state.value = _state.value.copy(playing = false, currentSlot = 0f)
    }

    fun onFrame(nowMs: Long) {
        val c = clock ?: return
        _state.value = _state.value.copy(currentSlot = c.currentSlot(nowMs))
        c.slotJustEntered(nowMs)?.let { idx ->
            val s = _state.value.song ?: return
            val barIdx = idx / s.slotsPerBar
            val slotIdx = idx % s.slotsPerBar
            s.bars.getOrNull(barIdx)?.get(slotIdx)?.forEach(bank::play)
        }
    }

    fun toggleMetronome() {
        _state.value = _state.value.copy(metronomeOn = !_state.value.metronomeOn)
    }
}
```

- [ ] **Step 2: Create `PlayerScreen.kt`** (per `handoff.md §03 · Player`)

```kotlin
package ph.nextbank.drums.ui.player

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.ui.components.*
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun PlayerScreen(
    songId: String,                                 // already consumed by VM
    onBack: () -> Unit,
    vm: PlayerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val song = state.song ?: return

    // RAF-driven frame loop
    LaunchedEffect(state.playing) {
        while (state.playing) {
            androidx.compose.runtime.withFrameNanos { nano ->
                vm.onFrame(nano / 1_000_000L)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(DrumsColors.Bg),
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBox(Icons.Filled.ArrowBack, "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text("NOW READING", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
                Text(song.title, color = DrumsColors.Text, style = DrumsType.cardTitle, maxLines = 1)
            }
            IconBox(Icons.Filled.MoreVert, "More", {})
        }

        // BPM + bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("BPM", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Text("${song.bpm}", color = DrumsColors.Accent, style = DrumsType.playerBpm)
            val currentBar = (state.currentSlot / song.slotsPerBar).toInt() + 1
            Text("BAR", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Text("$currentBar", color = DrumsColors.Text, style = DrumsType.barCounter)
            Text("/${song.totalBars}", color = DrumsColors.Dim, style = DrumsType.barCounter)
        }

        // Sheet music container
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp, top = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
                .padding(top = 18.dp, bottom = 10.dp, start = 10.dp, end = 10.dp),
        ) {
            DrumStaffStack(
                bars = song.bars,
                currentSlot = state.currentSlot,
                timeSig = song.timeSig,
            )
            Text(
                "${song.timeSig.first}/${song.timeSig.second}",
                color = DrumsColors.Dim, style = DrumsType.caption,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }

        // Drum hit chips
        val activeTokens: Set<DrumToken> = remember(state.currentSlot, song) {
            val slot = state.currentSlot.toInt().coerceIn(0, song.bars.size * song.slotsPerBar - 1)
            val barIdx = slot / song.slotsPerBar
            val slotIdx = slot % song.slotsPerBar
            song.bars[barIdx][slotIdx].toSet()
        }
        DrumHitChips(
            activeTokens = activeTokens,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Transport
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                icon = rememberVectorPainter(Icons.Outlined.MusicNote),
                contentDescription = "Metronome",
                onClick = vm::toggleMetronome,
                style = if (state.metronomeOn) TransportStyle.PRIMARY else TransportStyle.GHOST,
                sizeDp = 42.dp,
            )
            TransportButton(
                icon = rememberVectorPainter(Icons.Filled.Stop),
                contentDescription = "Stop",
                onClick = vm::stop,
                sizeDp = 42.dp,
            )
            TransportButton(
                icon = rememberVectorPainter(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow),
                contentDescription = if (state.playing) "Pause" else "Play",
                onClick = { vm.togglePlay(SystemClock.elapsedRealtime()) },
                style = TransportStyle.PRIMARY,
                sizeDp = 64.dp,
            )
            TransportButton(
                icon = rememberVectorPainter(Icons.Outlined.Loop),
                contentDescription = "Loop",
                onClick = {},
                sizeDp = 42.dp,
            )
            TransportButton(
                icon = rememberVectorPainter(Icons.Outlined.Speed),
                contentDescription = "Practice",
                onClick = {},
                sizeDp = 42.dp,
            )
        }
    }
}

@Composable
private fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(1.dp, DrumsColors.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = cd, tint = DrumsColors.Text) }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/player/
git commit -m "Add PlayerScreen + ViewModel with live staff + audio"
```

### Task 23: SongDetailScreen + SongDetailViewModel

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/song_detail/SongDetailViewModel.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/song_detail/SongDetailScreen.kt`

- [ ] **Step 1: Create `SongDetailViewModel.kt`**

```kotlin
package ph.nextbank.drums.ui.song_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import javax.inject.Inject

data class SongDetailUi(
    val song: Song? = null,
    val tempoOffset: Int = 0,
    val countInBars: Int = 1,
    val metronomeMode: String = "On · soft",
    val kit: String = "Acoustic — Studio",
    val mutedDrum: String = "Hi-hat",
)

@HiltViewModel
class SongDetailViewModel @Inject constructor(
    private val repo: SongRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    private val songId: String = handle.get<String>("songId")!!
    private val _state = MutableStateFlow(SongDetailUi())
    val state: StateFlow<SongDetailUi> = _state.asStateFlow()

    init { viewModelScope.launch { _state.value = _state.value.copy(song = repo.findById(songId)) } }

    fun changeTempo(delta: Int) {
        viewModelScope.launch {
            val cur = _state.value.song ?: return@launch
            val newBpm = (cur.bpm + delta).coerceIn(40, 240)
            repo.updateBpm(cur.id, newBpm)
            _state.value = _state.value.copy(
                song = cur.copy(bpm = newBpm),
                tempoOffset = _state.value.tempoOffset + delta,
            )
        }
    }
}
```

- [ ] **Step 2: Create `SongDetailScreen.kt`** (per `handoff.md §04 · Song Detail`)

```kotlin
package ph.nextbank.drums.ui.song_detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun SongDetailScreen(
    songId: String,
    onBack: () -> Unit,
    onStartReading: (String) -> Unit,
    onStartPractice: (String) -> Unit,
    vm: SongDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val song = state.song ?: return

    Column(
        Modifier.fillMaxSize().background(DrumsColors.Bg).verticalScroll(rememberScrollState()),
    ) {
        // Cover hero (200 dp tall, gradient + diagonal stripes)
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(
                    colors = listOf(DrumsColors.CoverGradFrom, DrumsColors.CoverGradTo),
                    start = Offset(0f, 0f), end = Offset(size.width, size.height),
                ))
                // diagonal white stripes
                for (i in -10..30) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.18f),
                        start = Offset(i * 40f, 0f),
                        end = Offset(i * 40f + size.height, size.height),
                        strokeWidth = 2f,
                    )
                }
            }
            // back / more
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Color.White) }

            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            ) {
                Text("${song.totalBars} BARS · ${song.timeSig.first}/${song.timeSig.second}",
                    color = Color.White.copy(alpha = 0.85f), style = DrumsType.allCapsLabel)
                Text(song.title, color = Color.White, style = DrumsType.coverTitle)
                Text(song.artist, color = Color.White.copy(alpha = 0.9f), style = DrumsType.body)
            }
        }

        // CTA row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DrumsColors.Accent)
                    .clickable { onStartReading(song.id) }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(14.dp))
                Text("Start reading", color = Color.White, style = DrumsType.buttonLabel)
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
                    .clickable { onStartPractice(song.id) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Speed, contentDescription = "Practice", tint = DrumsColors.Text) }
        }

        // Playback section
        Section("PLAYBACK") {
            SettingRow("Tempo", "${state.tempoOffset.signedString()} BPM under original", "${song.bpm} BPM")
            SettingRow("Count-in", "Click before playback starts", "${vm.state.value.countInBars} bar")
            SettingRow("Metronome", null, state.metronomeMode)
            SettingRow("Drum kit", null, state.kit)
            SettingRow("Mute", "Practice the muted part live", state.mutedDrum)
        }

        Section("SOURCE") {
            SettingRow("Imported", "12 May 2026", "PDF · 4 pages")
            SettingRow("Tempo detection", null, "Auto · ±2 BPM")
        }
        Spacer(Modifier.height(40.dp))
    }
}

private fun Int.signedString(): String = if (this >= 0) "+$this" else "$this"

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(title, color = DrumsColors.Dim, style = DrumsType.allCapsLabel,
            modifier = Modifier.padding(vertical = 8.dp))
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingRow(label: String, sub: String?, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .drawBottomBorder(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = DrumsColors.Text, style = DrumsType.rowTitle)
            if (sub != null) Text(sub, color = DrumsColors.Dim, style = DrumsType.caption)
        }
        Text(value, color = DrumsColors.Accent, style = DrumsType.cardTitle)
    }
}

private fun Modifier.drawBottomBorder(): Modifier = androidx.compose.ui.draw.drawBehind {
    drawLine(
        color = DrumsColors.Line,
        start = Offset(0f, size.height - 0.5f),
        end = Offset(size.width, size.height - 0.5f),
        strokeWidth = 1f,
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/song_detail/
git commit -m "Add SongDetailScreen + ViewModel"
```

### Task 24: PracticeScreen + PracticeViewModel

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/practice/PracticeViewModel.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/practice/PracticeScreen.kt`

- [ ] **Step 1: Create `PracticeViewModel.kt`**

```kotlin
package ph.nextbank.drums.ui.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import javax.inject.Inject

data class PracticeUi(
    val song: Song? = null,
    val startBpm: Int = 72,
    val targetBpm: Int = 116,
    val loops: Int = 8,
    val stepBpm: Int = 6,
    val currentLoop: Int = 3,
) {
    val currentBpm: Int get() = startBpm + (currentLoop - 1) * stepBpm
}

@HiltViewModel
class PracticeViewModel @Inject constructor(
    repo: SongRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    private val songId: String = handle.get<String>("songId")!!
    private val _state = MutableStateFlow(PracticeUi())
    val state: StateFlow<PracticeUi> = _state.asStateFlow()

    init { viewModelScope.launch { _state.value = _state.value.copy(song = repo.findById(songId)) } }
}
```

- [ ] **Step 2: Create `PracticeScreen.kt`** (per `handoff.md §05 · Practice`)

```kotlin
package ph.nextbank.drums.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ph.nextbank.drums.ui.components.DrumStaff
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun PracticeScreen(songId: String, onBack: () -> Unit, vm: PracticeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val song = state.song ?: return

    Column(
        Modifier.fillMaxSize().background(DrumsColors.Bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp).clip(CircleShape).border(1.dp, DrumsColors.Line, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DrumsColors.Text) }
            Spacer(Modifier.width(12.dp))
            Text("Speed ramp", color = DrumsColors.Text, style = DrumsType.sectionTitle)
        }

        // Big BPM card
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("CURRENT TEMPO", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Text("${state.currentBpm}", color = DrumsColors.Accent, style = DrumsType.displayBpm)
            Text("BPM · LOOP ${state.currentLoop} OF ${state.loops}",
                color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Spacer(Modifier.height(12.dp))
            RangeBar(state.startBpm, state.currentBpm, state.targetBpm)
        }

        // Loop preview card
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
                .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
        ) {
            Text("LOOPING BARS 1–2", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Spacer(Modifier.height(8.dp))
            DrumStaff(bars = song.bars.take(2), currentSlot = 6f)
        }

        // Loop dots
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 1..state.loops) {
                val fill = when {
                    i < state.currentLoop -> DrumsColors.Accent.copy(alpha = 0.6f)
                    i == state.currentLoop -> DrumsColors.Accent
                    else -> DrumsColors.BarTrack
                }
                Box(
                    Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(fill),
                )
            }
        }

        // Config card
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp)),
        ) {
            ConfigRow("Start tempo", "${state.startBpm} BPM")
            ConfigRow("Target tempo", "${state.targetBpm} BPM")
            ConfigRow("Loops", "${state.loops}")
            ConfigRow("Step", "+${state.stepBpm} BPM / loop", isLast = true)
        }

        Spacer(Modifier.weight(1f))

        // Resume
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Accent)
                .clickable { /* hook up later */ }
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Resume from loop ${state.currentLoop}", color = Color.White, style = DrumsType.buttonLabel)
        }
    }
}

@Composable
private fun RangeBar(start: Int, current: Int, target: Int) {
    val pct = (current - start).coerceAtLeast(0).toFloat() / (target - start).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$start", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Text("$current", color = DrumsColors.Accent, style = DrumsType.allCapsLabel)
            Text("$target", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(DrumsColors.BarTrack),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(pct).background(DrumsColors.Accent))
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String, isLast: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp, horizontal = 16.dp)
            .let { if (!isLast) it.drawBottomBorder() else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = DrumsColors.Text, style = DrumsType.rowTitle, modifier = Modifier.weight(1f))
        Text(value, color = DrumsColors.Accent, style = DrumsType.cardTitle)
    }
}

private fun Modifier.drawBottomBorder(): Modifier = androidx.compose.ui.draw.drawBehind {
    drawLine(
        color = DrumsColors.Line,
        start = androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f),
        end = androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f),
        strokeWidth = 1f,
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/practice/
git commit -m "Add PracticeScreen + ViewModel"
```

### Task 25: Smoke instrumented test

**Files:**
- Create: `app/src/androidTest/java/ph/nextbank/drums/LibrarySmokeTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package ph.nextbank.drums

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class LibrarySmokeTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun library_shows_your_kit_and_a_song_row() {
        composeRule.onNodeWithText("Your kit").assertIsDisplayed()
        composeRule.onNodeWithText("Smells Like Teen Spirit").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Hilt test runner setup**

Add to `app/build.gradle.kts` dependencies block:

```kotlin
androidTestImplementation("com.google.dagger:hilt-android-testing:2.52")
kspAndroidTest("com.google.dagger:hilt-android-compiler:2.52")
```

Replace `testInstrumentationRunner` in `defaultConfig` with `"ph.nextbank.drums.HiltTestRunner"` and create:

```kotlin
// app/src/androidTest/java/ph/nextbank/drums/HiltTestRunner.kt
package ph.nextbank.drums

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, ctx: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, ctx)
}
```

- [ ] **Step 3: Run smoke test on emulator**

```bash
./gradlew :app:connectedDebugAndroidTest --tests LibrarySmokeTest
```
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/ app/build.gradle.kts
git commit -m "Add Hilt-aware library smoke test"
```

### Task 26: HOW_TO_RUN.md for the user

**Files:**
- Create: `docs/HOW_TO_RUN.md`

- [ ] **Step 1: Create user-facing run instructions**

```markdown
# How to run the drums app

## One-time setup

1. **Install Android Studio** (~3 GB). On Fedora:
   ```
   flatpak install -y flathub com.google.AndroidStudio
   ```
   Or download the tarball from https://developer.android.com/studio and run `bin/studio.sh`.

2. **Launch Android Studio.** On first launch it'll prompt you to download "SDK Components" — click through all defaults. Wait for "Done."

## Opening the project

1. Android Studio → **Open** → navigate to `/home/sara/claude/drums-app` → Open.
2. Wait for the "Gradle sync" progress bar (bottom-right). First sync downloads dependencies — may take 5–10 min on first run.
3. If Gradle complains about a missing JDK, click the suggested fix or set **File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** to "Embedded JDK 17".

## Running on the emulator

1. Top toolbar → **Device Manager** (phone icon) → **Create Device** → pick **Pixel 8** → choose the latest "Tiramisu" or "UpsideDownCake" system image → **Finish**.
2. Top toolbar → device dropdown → pick your new Pixel 8.
3. Click the green **▶ Run** button. Wait for the emulator to boot. The app installs and opens automatically.

## Running on your phone

1. On your phone: **Settings → About phone → tap "Build number" 7 times** to enable Developer options.
2. **Settings → System → Developer options → enable "USB debugging"**.
3. Plug your phone into the laptop with a USB cable. You'll see a prompt on the phone: tap **Allow** for USB debugging.
4. In Android Studio, your phone now appears in the device dropdown. Pick it and click **▶ Run**.

## Smoke checklist

- [ ] App opens to a screen titled "Your kit" with 5 song rows.
- [ ] Tap "Smells Like Teen Spirit" → opens Player → drum staff is visible.
- [ ] Tap the big violet play button → drum sounds play; violet line sweeps across the staff.
- [ ] Tap stop → playback resets to bar 1.
- [ ] Back arrow returns to the library.

If anything's broken, check Logcat (bottom of Android Studio) for red error lines and share the first 20.
```

- [ ] **Step 2: Commit**

```bash
git add docs/HOW_TO_RUN.md
git commit -m "Add user-facing run instructions"
```

---

## Self-review (filled in inline, no separate task)

**Spec coverage:**
- §3.1 Library — Task 20. ✓
- §3.2 Open into Player — Task 19 (nav) + Task 22 (Player). ✓
- §3.3 Song Detail with persisted settings — Task 23. ✓ (Only tempo is currently persisted; other settings are UI-only — accepted scope cut.)
- §3.4 Practice — Task 24. ✓ (Speed ramp displays correctly; "Resume" button is stubbed — accepted scope cut for Phase 1, will hit play on current bar but not auto-increment yet; flag in commit message.)
- §3.5 Add a song stubbed — Task 21. ✓
- §4 Tech stack — Tasks 1–3. ✓
- §5 Module layout — established across all tasks. ✓
- §6 5 sample songs — Task 7. ✓
- §7 Build path — Task 26 (HOW_TO_RUN.md). ✓
- §9 DoD: all 5 screens, audio+playhead sync, settings persist, app installs, tests pass, no warnings — Tasks 20–26 cover all but visual pixel-accuracy review, which is a manual checkpoint after Task 24.

**Placeholder scan:** None remaining. The `comingSoon()` toast in UploadScreen is intentional Phase-1 behavior, not a placeholder.

**Type consistency:** `Song`, `DrumToken`, `SongClock` signatures match across Tasks 6, 8, 9, 11, 22. `TransportButton` style enum used consistently across Tasks 18, 20, 22.

**Caveat noted:** Practice screen "Resume" only renders the static design — actually wiring the loop ramp into PlayerScreen is a Phase 1.5 follow-up. Plan accepts this as a scoped reduction; flag in commit message and in the spec's §9.

---

## Definition of done (recap)

When all 26 tasks are done:

- `./gradlew assembleDebug` produces a working APK in `app/build/outputs/apk/debug/`.
- Library screen lists 5 songs; tapping any opens the Player with live audio + playhead.
- Song Detail tempo change persists across cold restart.
- All unit tests pass (`./gradlew :app:testDebugUnitTest`).
- Smoke instrumented test passes on emulator (`./gradlew :app:connectedDebugAndroidTest`).
- `docs/HOW_TO_RUN.md` walks the user through installing Android Studio and running the app on either emulator or phone.
