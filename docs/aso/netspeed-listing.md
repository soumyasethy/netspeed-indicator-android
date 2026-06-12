# NetSpeed Indicator — Play Store Listing Pack (ship-ready)

> Source-of-truth metadata for the Google Play listing. Engineered per the Status-Bar-Suite
> ASO strategy: generic head keyword in the title, privacy hook in the short description,
> keyword-dense + Hindi long description, honest permissions disclosure. Char limits are
> enforced inline. Every claim maps to a shipped, verified feature (v1.1).

---

## 0. Decisions at a glance

| Field | Value | Chars / limit |
|---|---|---|
| **Title** | `Internet Speed Meter: Live` | 26 / 30 |
| **Developer name** (brand token) | `lazycode.ai` | — |
| **Short description** | see §2 | 73 / 80 |
| **Primary keyword** | "internet speed meter" / "net speed" | highest-volume head term |
| **Secondary** | status bar, data usage, signal, monitor, indicator | |
| **App category** | Tools | |
| **Content rating** | Everyone | |
| **Price** | Free (Rs.29 early-bird suite unlock in-app) | |

**Why "Internet Speed Meter" leads the title:** highest-volume generic query in the category,
descriptive (not trademarkable), zero impersonation risk. Brand token "NetSpeed / lazycode.ai"
is reserved for the **developer-name field**, not the title — so the 30 chars all go to the
head keyword. (Do NOT put a competitor brand or look-alike icon in the title — suspension risk.)

---

## 1. Title — primary + A/B variants (≤30 chars)

- **A (ship):** `Internet Speed Meter: Live`  — 26 chars. Head term first.
- **B (test):** `Net Speed Meter & Data Usage` — 28 chars. Two keyword clusters.
- **C (test):** `Internet Speed Meter Monitor` — 28 chars. Adds "monitor".

Rotate one variant at a time via Play **store-listing experiments** (50/50, ≥7 days, judge on
**install conversion**, not impressions). Keep the winner ≥4 weeks before retesting.

---

## 2. Short description — primary + variants (≤80 chars)

Leads with the exact primary keyword, then the privacy differentiator (the suite's moat).

- **A (ship):** `Live internet speed in your status bar. No ads. No tracking. No INTERNET.` — 73
- **B (test):** `Net speed meter for your status bar — private, no ads, no internet access.` — 74
- **C (test):** `Internet speed meter + data usage in the status bar. Private. No ads.` — 69

App Radar finding: adding the target keyword to the short description correlated with ranking
gains in 84.2% of studied cases — so the exact phrase stays in here, not just the title.

---

## 3. Long description (fully indexed by Google Play; ≤4000 chars)

> Paste verbatim. First 2 lines (shown above the fold) repeat the primary keyword. Keyword
> density ≈ 1 exact match / 250 chars — dense but not stuffed. Hindi/Hinglish synonyms included
> for the India ranking advantage. Current length ≈ 2,909 chars (headroom for localized edits).

```
Internet Speed Meter shows your live internet speed right in the status bar — download and upload, updated every second. A clean, private net speed indicator with no ads and no internet access at all.

NetSpeed Indicator is a tiny, battery-friendly internet speed meter that turns your status bar into a real-time net speed monitor. See exactly how fast your Wi-Fi or mobile data is moving, the moment it moves.

⚡ LIVE SPEED IN THE STATUS BAR
• Real-time download + upload speed as a status-bar icon
• Pick your style: arrows, side-by-side, stacked, compact, or auto
• Choose units (KB/MB), background, text colour and outline — your icon, your way
• Smoothed readout so the number stays steady, not jittery

🫧 FLOATING SPEED BUBBLE
• A draggable speed chip that floats over any app — game, video, browser
• Shows the same style, colours and upload value you picked
• Resize it, drop it anywhere, tap to open the app

🏠 HOME-SCREEN WIDGETS (5 styles)
• Hero banner, Dial, Rings, Pill and Weather widgets
• One-tap "Add to Home screen" — no menu digging
• Live download, upload, today's data usage and a daily-quota ring

📊 SPEED + DATA INSIGHTS
• Wi-Fi and mobile signal strength shown in percent
• Today, 30-day history and lifetime data usage
• Peak speed and a live sparkline of recent activity

🎨 14 LIVE THEMES × 6 COLOUR SKINS
• Animated gradient dashboard with smooth, flowing motion
• Aurora, Carbon, Glasswave, Neo-brutal, Terminal and Tier skins
• A whole-app look that repaints every screen in one identity

🔒 TRUE PRIVACY — NO INTERNET PERMISSION
• This app ships with NO android.permission.INTERNET
• It physically CANNOT send your data anywhere — a guarantee you can verify
• No ads, no trackers, no third-party SDKs, no account, no sign-up
• Speed is read from your phone's own traffic counters, on-device only

Why people use NetSpeed Indicator:
• Spot a slow or stalled connection instantly
• Check if a download is really moving
• Watch your data usage so you don't blow your daily limit
• Test Wi-Fi vs mobile speed at a glance

A real-time internet speed meter, net speed indicator, data usage monitor and status-bar speed display — in one small, private app.

— Also in Hindi / हिंदी —
इंटरनेट स्पीड मीटर: अपने स्टेटस बार में लाइव इंटरनेट स्पीड देखें। यह नेट स्पीड इंडिकेटर डाउनलोड और अपलोड स्पीड हर सेकंड दिखाता है — बिना विज्ञापन और बिना इंटरनेट अनुमति के। वाई-फाई और मोबाइल डेटा की स्पीड, सिग्नल और डेटा उपयोग एक ही जगह। आपका डेटा आपके फ़ोन में ही रहता है।

PERMISSIONS WE USE (and why)
• POST_NOTIFICATIONS — the speed icon is drawn as a notification icon; that's how it appears in the status bar
• ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE — to label Wi-Fi vs mobile and show signal %
• SYSTEM_ALERT_WINDOW — only for the optional floating bubble
• Foreground service — to keep the speed updating reliably
We do NOT request the INTERNET permission. Your data never leaves the device.
```

---

## 4. Keyword targets (indexed via title + descriptions + reviews)

Primary cluster: `internet speed meter`, `net speed`, `speed meter`, `net speed indicator`,
`internet speed test` (avoid claiming a "test/server" feature we don't have — use "meter/monitor").
Secondary: `status bar speed`, `data usage`, `data monitor`, `network speed`, `wifi speed`,
`mobile data speed`, `speed indicator`, `bandwidth monitor`, `signal strength`.
India long-tail (low competition): `internet speed meter hindi`, `net speed status bar`,
`data usage daily`, `wifi signal meter`.

**Reviews are indexed** — prompt happy users (after their "aha" moment, e.g. first time the
bubble shows live speed) to mention features by name: "speed in status bar", "no ads",
"floating bubble". Fresh review velocity > absolute rating for ranking.

---

## 5. Data Safety form answers (Play Console)

The no-INTERNET design makes this section a **marketing asset** — fill it as the strongest
possible privacy posture:

- **Does your app collect or share user data?** → **No.** (All speed/usage data is read and
  stored on-device; nothing is transmitted — there is no INTERNET permission to transmit with.)
- **Is data encrypted in transit?** → N/A (no data leaves the device).
- **Can users request data deletion?** → Data is local; uninstalling removes it. Settings also
  reset usage.
- **Committed to Play Families policy?** → as applicable.
- Add a one-line privacy policy URL (host on the lazycode.ai site): "NetSpeed Indicator collects
  no data and has no internet access."

> ⚠️ This posture is exclusive to the **utility** apps. The future LLM assistant app will send
> notification content to the cloud and MUST answer Data Safety differently (collects/shares =
> Yes, with consent + disclosure). Do not copy this form to that app.

---

## 6. Graphics checklist (assets already captured)

| Asset | Source file | Caption overlay |
|---|---|---|
| Screenshot 1 | `docs/screenshots/01-hero.png` | "Live speed dashboard — 14 themes" |
| Screenshot 2 | `docs/screenshots/02-statusbar-icon.png` | "Speed right in your status bar" |
| Screenshot 3 | `docs/screenshots/03-floating-bubble.png` | "Floating bubble over any app" |
| Screenshot 4 | `docs/screenshots/04-notification.png` | "Wi-Fi & mobile signal %" |
| Screenshot 5 | `docs/screenshots/07-home-widget.png` | "5 home-screen widgets" |
| Screenshot 6 | `docs/screenshots/05-icon-styles.png` | "Your icon, your colours" |
| Feature graphic (1024×500) | `docs/aso/feature-graphic.png` ✅ | Aurora gradient + status-bar mock + "No ads · No trackers · No INTERNET" |

First 2 screenshots carry conversion — lead with the status-bar icon (the core promise) and the
hero. Add a 1–2 word caption overlay on each (localized to Hindi for the India listing).

---

## 7. Compliance / risk notes

- Title/desc use only **descriptive, generic** terms — no competitor brand token, no "unofficial"
  hack, no trademarked name. Safe under Play Impersonation & IP policy.
- Every feature claim is shipped and verified in v1.1 (status-bar chip, bubble, 5 widgets, signal
  %, usage history, no-INTERNET). No vaporware claims → no "misleading" review flag.
- "Speed test" is deliberately avoided as a feature claim (we meter live traffic; we don't run a
  server speed test) — keeps claims honest and review-proof.
- Localized listing (Hindi `hi-IN`): translate Title/Short/Long + screenshot captions to earn the
  India ranking advantage. The Hindi block above can seed the localized long description.
```
