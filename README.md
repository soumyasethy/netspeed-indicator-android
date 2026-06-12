<div align="center">

# 📶 NetSpeed Indicator

### Real-time internet speed, right in your status bar.

A fast, private, beautifully animated network-speed meter for Android — live
download/upload speed as a status-bar icon, a floating bubble, home-screen widgets,
and an animated in-app dashboard. **No `INTERNET` permission. Your data never leaves
the device.**

<br>

[![Download APK](https://img.shields.io/badge/⬇%20Download-APK%20(latest%20release)-2563EB?style=for-the-badge)](../../releases/latest)
&nbsp;
![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Size](https://img.shields.io/badge/APK-~1.3%20MB-7C3AED?style=for-the-badge)
![No Internet](https://img.shields.io/badge/Privacy-zero%20INTERNET%20perm-EC4899?style=for-the-badge)

</div>

---

## 📸 Screenshots

<div align="center">

| Animated hero dashboard | Status-bar speed icon | Floating speed bubble |
|:---:|:---:|:---:|
| <img src="docs/screenshots/01-hero.png" width="240"/> | <img src="docs/screenshots/02-statusbar-icon.png" width="240"/><br><sub>full-height chip; glyphs punched out so it never tints to a blank box</sub> | <img src="docs/screenshots/03-floating-bubble.png" width="240"/><br><sub>live speed floating over any app — your colours, style &amp; upload</sub> |

| Notification + signal % | Icon styles & colours | Custom colour picker |
|:---:|:---:|:---:|
| <img src="docs/screenshots/04-notification.png" width="240"/> | <img src="docs/screenshots/05-icon-styles.png" width="240"/> | <img src="docs/screenshots/06-color-picker.png" width="240"/> |

| Home-screen widget + bubble | | |
|:---:|:---:|:---:|
| <img src="docs/screenshots/07-home-widget.png" width="240"/><br><sub>any of 5 widget styles, one tap from the app</sub> | | |

</div>

---

## 🎨 15 live themes × 6 colour skins

Pick a **theme** for the motion and a **skin** for the palette — they compose. A skin
repaints the *whole* app in one identity: hero, chips, toggles, the home-screen widgets,
even the status-bar chip. Six of the combinations:

<div align="center">

| Aurora · Liquid | Tier · Tier flow | Carbon pulse · ECG |
|:---:|:---:|:---:|
| <img src="docs/screenshots/hero-aurora-liquid.png" width="240"/> | <img src="docs/screenshots/hero-tier-tierflow.png" width="240"/> | <img src="docs/screenshots/hero-carbon-ecg.png" width="240"/> |

| Glasswave · Kinetic | Neo-brutal · Brutalist | Terminal · Terminal |
|:---:|:---:|:---:|
| <img src="docs/screenshots/hero-glass-kinetic.png" width="240"/> | <img src="docs/screenshots/hero-brutal-brutalist.png" width="240"/> | <img src="docs/screenshots/hero-terminal-terminal.png" width="240"/> |

</div>

> Themes: Kinetic · Tier flow · Liquid · ECG · Dial · Radar · Particles · Curtains ·
> Material You · Sky · Bento · Terminal · Brutalist · Glass · Speedtest.
> Skins: Tier · Aurora · Carbon pulse · Glasswave · Neo-brutal · Terminal.

---

## ✨ Features

### Status-bar speed icon
- **5 styles** — Arrows ↕, Arrows ↔ (side-by-side), Stacked, Compact, and **Auto ⇅**
  (shows whichever direction is busier, one at a time).
- **Unit display** your way — `84k` (short), `84 KB/s` (full), or number-over-unit.
- **Custom colours** — background, text, and an **outline** chip (colour + 1–3 px width).
- **Size slider** + respects your system font scale.
- **Auto-hide when idle** — the icon disappears after 30 s of no traffic and snaps
  back the instant data flows.

### 🫧 Floating speed bubble
- A small **draggable chip** that floats over every app. Because it's *our* surface (not
  the OS-tinted status-bar slot) it renders the **exact same icon** you configured —
  style, unit, background, text colour, outline **and the upload value** — in full colour.
- Independently **size-configurable**; tap it to open the app; remembers where you drop it.

### 🏠 Home-screen widgets (×5)
- **Hero** (gradient banner), **Dial**, **Rings**, **Pill**, **Weather**.
- An **“Add to Home screen”** row sits right under the live hero — tap any style and the
  launcher drops that widget straight onto your home screen. Or use the launcher's own
  widget picker.

### 🎨 Animated dashboard
- **15 live themes** (Kinetic, Tier flow, Liquid, ECG, Dial, Radar, Particles, Curtains,
  Material You, Sky, Bento, Terminal, Brutalist, Glass, Speedtest) × **6 colour skins**
  (Tier, Aurora, Carbon, Glasswave, Neo-brutal, Terminal).
- Smooth, gemini-style **flowing gradients** (constant-velocity, reduced-motion aware).
- A Material 3 **design system** whose primary/secondary/tertiary colours all derive
  from the active skin.

### 📊 Insight
- **Smoothed** readout (no jittery numbers), **Wi-Fi / mobile signal %** in the panel,
  **today / 30-day history / lifetime** usage, and a daily-quota ring.

### 🔒 Privacy
- Ships with **no `android.permission.INTERNET`** — a verifiable guarantee the app
  *cannot* phone home. Speed is read from kernel `TrafficStats` counters. No ads,
  no trackers, no analytics SDKs.

---

## ⬇️ Install (from GitHub)

1. Open the **[latest release](../../releases/latest)** and download the `.apk`.
2. On your phone, tap the file → allow **“Install unknown apps”** for your browser /
   Files app if prompted.
3. Open **NetSpeed Indicator**, flip **“Show speed in status bar”** on, and grant the
   notification permission. Done.

> First-launch tip: if your phone (Samsung One UI / MIUI) collapses status-bar
> notification icons into a single dot, the app’s **“Icon not showing? (status-bar dot)”**
> card walks you through the one system toggle to fix it.

---

## 🛠️ Build from source

Requires **JDK 17** (newer JDKs crash the Kotlin compiler used here).

```bash
export JAVA_HOME=/path/to/jdk-17
git clone git@github.com:soumyasethy/netspeed-indicator-android.git
cd netspeed-indicator-android
./gradlew assembleDebug            # debug APK (installable, debuggable)
./gradlew testDebugUnitTest        # unit tests
./gradlew assembleRelease          # release APK (signed if keystore.properties present)
```

Release signing is read from a gitignored `keystore.properties` at the repo root:
```properties
storeFile=keystore/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## 🧱 Tech

Kotlin · Jetpack Compose + Material 3 · DataStore · foreground service (`specialUse`) ·
Android Canvas · Lottie (local animation rendering only) · `min SDK 26 / target 35` ·
Gradle Kotlin DSL + version catalog · **no ads, no trackers, no network SDKs** — and
still zero `INTERNET` permission.

Architecture and design notes live in [`docs/context/`](docs/context/CONTEXT.md).
