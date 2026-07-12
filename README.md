# Omnitrix Watch App

A fan-made, fully offline dial app for Android smartwatches (built/tested for the **Modio 4**,
Android 9, square face). Home screen shows a rotating dial with a core symbol. Tap the symbol
to open a 10-slot alien grid; tap an alien to see it glow and (once you add sound files) play a
clip. Long-press the core symbol to cycle **NORMAL → DNA SCAN (yellow) → LOW POWER (red)**.

No copyrighted show artwork, logos, or audio is included in this repo — everything ships with
generic placeholder shapes so it builds and runs immediately. You add your own images/sounds
(e.g. from your own `benwatch` repo/assets) as described below.

## 1. Add your own artwork & sounds

Drop files into these two folders using **exactly** these names (extension can be `.png`,
`.webp`, or `.jpg` for images; `.mp3`, `.ogg`, or `.wav` for sounds):

```
app/src/main/res/drawable/alien_1.png   ... alien_10.png
app/src/main/res/raw/alien_1.mp3        ... alien_10.mp3
```

That's it — `Alien.kt` looks these up by name at runtime, so nothing else needs to change.
If a file is missing, the app just falls back to the built-in placeholder silhouette (and stays
silent for that alien) instead of crashing.

Also edit the display names in `app/src/main/java/com/benwatch/omnitrix/Alien.kt`
(`AlienRoster.ALIENS`) to whatever your own line-up is called.

> Note on your `benwatch` GitHub repo: since it's your own repository, the simplest workflow is
> to clone it locally and copy the image/sound files across into the two folders above, renaming
> them to `alien_1`…`alien_10` as you go. I didn't auto-pull from it here since I can't verify
> what's licensed for redistribution in it — copying your own files in manually keeps that
> squarely under your control.

## 2. Adjust for your exact screen

The photo you sent shows a square-ish 1.8"-class panel. Modio 4-family watches commonly ship at
**320×360** or **360×360**. The layouts here are already resolution-independent (percentage-based
`ConstraintLayout` sizing, no hardcoded pixel dimensions), so you shouldn't need to change
anything — but if text looks too big/small once it's on the actual watch, tweak the `sp` values in:

```
app/src/main/res/values/strings.xml   (just text)
app/src/main/res/layout/*.xml         (textSize attributes)
```

## 3. Build automatically with GitHub Actions

The workflow at `.github/workflows/android-build.yml` is already set to run automatically:

- On every push to `main`/`master`
- On every pull request
- On demand via **Actions → Build Omnitrix Watch APK → Run workflow**

After a run finishes, open the run → **Artifacts** → download `omnitrix-watch-debug-apk`. That's
a `.apk` you can install straight on the watch.

To push this project to your own repo:

```bash
cd BenOmnitrixWatch
git init
git add .
git commit -m "Omnitrix watch app"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

The Action needs no secrets or extra setup — it just needs Actions enabled on the repo
(Settings → Actions → General → allow all actions).

## 4. Install the APK on the Modio 4 (fully offline app, no Play Store needed)

Since these watches don't have Google Mobile Services, sideload it one of these ways:

- **ADB (recommended):** enable Developer Options + USB debugging on the watch (Settings → About
  → tap Build Number 7 times), connect via USB or `adb connect <watch-ip>:5555` over Wi-Fi, then:
  ```bash
  adb install app-debug.apk
  ```
- **File manager on the watch:** copy the APK onto the watch's internal storage (USB cable or a
  Bluetooth/Wi-Fi file transfer app) and open it from a file manager app to install.
- **Third-party store:** sideload via Aptoide/Aurora Store if the watch has one preinstalled.

The app requests **no internet permission** — everything (images, sounds, animations) is bundled
in the APK and runs 100% offline.

## 5. What's already implemented

- Home screen dial: drag anywhere to manually rotate the outer ring; tap the core symbol to
  glow-pulse and open the alien grid.
- 10 alien slots in a grid; tapping one opens a detail screen with a pulsing glow ring and plays
  its sound (once you've added `alien_N.mp3` files).
- Long-press the core symbol on the home screen to cycle modes:
  - **NORMAL** — green idle dial
  - **DNA SCAN** — yellow theme with a rotating scan-line sweep
  - **LOW POWER** — red/dim theme with slower, reduced animation
  - Mode persists across app restarts (`ModeManager` / `SharedPreferences`).
- Everything is built with plain Android Views (no network calls anywhere), so it's compatible
  with Android 9 (`minSdk 26`) and runs fine on low-RAM watch hardware.

## 6. Project structure

```
app/src/main/java/com/benwatch/omnitrix/
  MainActivity.kt          home screen
  OmnitrixDialView.kt       custom-drawn rotating dial + touch handling
  AlienSelectActivity.kt    10-alien grid screen
  AlienDetailActivity.kt    glow + sound playback screen
  AlienAdapter.kt           RecyclerView adapter for the grid
  Alien.kt                  alien data + resource name lookups
  WatchMode.kt              NORMAL / DNA_SCAN / LOW_POWER + persistence
app/src/main/res/
  layout/                   activity_main, activity_alien_select, activity_alien_detail, item_alien
  drawable/                 placeholder icons, glow gradients (green/yellow/red)
  values/                   colors, strings, theme
.github/workflows/
  android-build.yml         CI build → downloadable APK artifact
```

## 7. Opening in Android Studio (optional, if you want to edit/preview locally)

Open the `BenOmnitrixWatch` folder as an existing project in Android Studio (Hedgehog or newer).
It'll sync Gradle automatically. Run on an emulator with a square 320×360 or 360×360 custom AVD
skin to preview roughly how it'll look on the watch, or just install the CI-built APK directly on
the device.
