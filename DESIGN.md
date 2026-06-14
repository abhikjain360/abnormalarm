# Abnormalarm — Design Document

A precise, low-overhead Android alarm clock for technical users. Native Kotlin + Jetpack Compose, Android 15+.

> **Status:** scaffold + repeat engine + reliability core BUILT and compiling into a working
> `app-debug.apk`. Persistence, UI, calendar layer, reliability onboarding, and timers are part of
> the product surface. See `CLAUDE.md` for the engineering handoff (what's done / stubbed / next)
> and the build order in §15. This doc remains the source of truth for behavior.

---

## 1. Goals & non-goals

**Goals**
- **Fire exactly on time, always.** This is the whole reason the app exists. No "set it 3–5 min early and pray."
- **Very low idle cost.** No always-on service and no background CPU loop. Google Calendar API sync
  uses low-frequency WorkManager polling plus manual refresh; everything else is event-driven.
  Target well under 100 MB RAM (realistically ~30–60 MB for a Compose app; near-zero when
  backgrounded).
- **Deep, opt-in configurability** for a power user, but a creation flow that asks for *nothing but the time* by default.
- **Google Calendar → alarms**, using the Google Calendar API when the device does not expose
  Google events through Android's public calendar provider.
- **Multiple parallel timers**, with saved durations, durable countdown state, and a built-in
  numpad duration editor.
- **Home screen widget**, with a large local clock, date, next alarm, and up to two optional
  secondary time zones.

**Non-goals**
- No iOS / other platforms.
- No stopwatch, world clock, bedtime, or other non-alarm/non-timer feature.
- No telemetry. Network is used only for the opt-in Google Calendar API backend; local
  `CalendarContract` calendars still work without network when the device exposes them.

---

## 2. Why it will actually be on time (the core mechanism)

The reliability problem in other apps comes from using **inexact / repeating** alarms, which Android batches and defers (especially in Doze).

**Abnormalarm uses `AlarmManager.setAlarmClock()` exclusively.** Properties (verified against Android docs):
- The system **never adjusts** its delivery time.
- The system **leaves low-power / Doze** if needed to deliver it on the dot.
- It surfaces the next alarm to the OS (status-bar icon, lock screen).

**Single-fire + self-reschedule model:** there are *no* OS-level repeating alarms. Every alarm —
manual or calendar — is scheduled as **one** `setAlarmClock()` for **one** exact timestamp. When it
fires (or is skipped/snoozed), we compute the **next** occurrence and schedule exactly one more.
Repeats therefore cost nothing structurally and are perfectly exact. Running timers use the same
single exact one-shot principle, but they are countdowns, not repeat-series alarms: each running
timer owns one exact expiry trigger and is cancelled/replaced when paused, reset, edited, or deleted.

**Permission:** `USE_EXACT_ALARM` (a *normal* permission introduced for alarm-clock apps in API 33+). Granted at install, **no runtime prompt, no Settings detour** — because the app's core function genuinely is alarms. We will **not** use `SCHEDULE_EXACT_ALARM` (that's the one requiring a user grant).

**Survives reboot:** a `BOOT_COMPLETED` receiver re-materializes all alarms after restart. (This alone fixes the "alarm vanished after reboot" class of bugs.)

---

## 3. Architecture overview

Single-activity Compose app. Clear layers:

```
com.abhikjain360.abnormalarm
├── App.kt                      // Application: notif channels, WorkManager init
├── MainActivity.kt             // single activity, Compose host + navigation
├── domain/
│   ├── model/                  // Alarm, RepeatRule (sealed), RingSettings, etc.
│   └── schedule/
│       └── NextOccurrence.kt   // pure fn: rule + "after" -> next Instant (or null)
├── data/
│   ├── db/                     // Room: AlarmEntity, AlarmDao, AbnormalarmDb
│   ├── settings/               // DataStore (global prefs)
│   ├── AlarmRepository.kt
│   ├── TimerRepository.kt
│   └── calendar/
│       ├── CalendarRepository.kt   // ContentResolver queries
│       ├── GoogleCalendarApiRepository.kt // OAuth-backed Google Calendar REST reads
│       └── CalendarObserver.kt     // ContentObserver registration
├── scheduling/
│   ├── AlarmScheduler.kt       // thin wrapper over AlarmManager.setAlarmClock
│   ├── TimerScheduler.kt       // one exact expiry trigger per running timer
│   ├── AlarmReceiver.kt        // fires: starts ring; computes+schedules next
│   ├── TimerReceiver.kt        // timer expiry -> persisted ringing state + ring UI
│   ├── RescheduleReceiver.kt   // BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME(ZONE)_CHANGED -> rescheduleAll()
│   ├── UpcomingReceiver.kt     // posts "next alarm" notification (lead window)
│   └── CalendarSyncWorker.kt   // ~5-hour WorkManager sync + on-demand
├── reliability/
│   └── ReliabilitySetup.kt     // one-time device-aware setup + passive status (see §12)
├── ring/
│   ├── RingActivity.kt         // full-screen Compose ring screen
│   ├── RingService.kt          // foreground service while ringing
│   └── Ringer.kt               // sound + vibration + torch control
├── notifications/
│   └── Notifications.kt        // channels, upcoming(+Skip), missed, ringing
└── ui/
    ├── theme/                  // Catppuccin Mocha -> Material 3 dark roles
    ├── list/                   // AlarmListScreen
    ├── edit/                   // AlarmEditScreen + advanced section
    ├── timer/                  // TimerListScreen + numpad duration editor
    ├── widget/                 // App widget provider + configuration
    └── settings/               // SettingsScreen
```

**Why this is light:** no always-on service, no work loop. The process is dormant between alarms and
running timers; the OS holds scheduled exact entries and a `ContentObserver` registration (both
kernel/system-side, zero app CPU). Timer countdown text ticks only while the Timer screen is visible.
The only time we run a foreground service is during the seconds/minutes an alarm or timer is actively
ringing.

---

## 4. Repeat engine

A single pure function drives everything:

```kotlin
fun nextOccurrence(rule: RepeatRule, after: ZonedDateTime): ZonedDateTime?  // null = no more
```

`RepeatRule` (sealed) — the ten confirmed modes:

| # | Mode | Params | Notes |
|---|------|--------|-------|
| 1 | One-time | — | fires once, then disabled |
| 2 | Every N days | `n` | n=1 ⇒ daily |
| 3 | Specific weekdays | `Set<DayOfWeek>` | e.g. Mon/Wed/Fri |
| 4 | Every N weeks on weekdays | `n`, `Set<DayOfWeek>`, anchor week | e.g. every 2nd week Tue/Thu |
| 5 | Specific dates of month | `Set<Int>` (1–31) | **skip** months lacking that date |
| 6 | Nth weekday of month | `nth` (1–4 / last), `DayOfWeek` | `TemporalAdjusters.dayOfWeekInMonth` |
| 7 | Every N months on a date | `n`, `dayOfMonth` | skip short months (per #5 rule) |
| 8 | Yearly | `month`, `day` | Feb 29 ⇒ only leap years |
| 9 | Days-before-end-of-month | `n` (0 = last day) | `lengthOfMonth() - n` |

**End condition** (optional, default *never*): `Until(date)` or `AfterCount(n)`. `nextOccurrence` returns `null` once exceeded ⇒ alarm auto-disables.

**Edge cases are unit-tested, not hand-waved:** 31st in February (skip), Feb-29 yearly (leap only), DST spring-forward/fall-back (use `ZonedDateTime`, accept the OS's gap/overlap resolution), midnight rollovers. The `nextOccurrence` function is the most-tested unit in the codebase.

---

## 5. Calendar integration

**Sources:**
- **Device calendars:** Android's `CalendarContract` content provider. This remains the lowest-cost
  path for local/OEM/Exchange calendars and for devices that still expose Google events through the
  public provider. Requires `READ_CALENDAR`.
- **Google Calendar API:** OAuth-backed read-only access to Google Calendar, used because the POCO
  F7 Ultra / current Google Calendar stack syncs real Google events into Google Calendar's private
  `com.google.android.calendar` provider while `CalendarContract` only exposes local/birthday
  calendars. Requires `INTERNET`, Google Play services authorization, a Google Cloud project with
  Calendar API enabled, and the `https://www.googleapis.com/auth/calendar.readonly` scope.

**Which events become alarms:**
- Qualify if **`SELF_ATTENDEE_STATUS = ATTENDEE_STATUS_ACCEPTED`**, OR you are the **organizer**, OR the event has **no attendees** (personal event).
- **Exclude** declined / tentative / needs-action (invited-but-no-response).
- **Exclude all-day events.**

**Lead time / how many alarms per event:**
- **One alarm per reminder row** in `CalendarContract.Reminders` (`METHOD_ALERT`/`METHOD_DEFAULT` only — ignore email/SMS). `MINUTES` gives the offset, so a 10-min and a 1-day reminder ⇒ two alarms.
- If `Events.HAS_ALARM = 0` or no usable alert reminder ⇒ **fallback: one alarm 5 min before**.

**Per-calendar filtering:** settings list device calendars and Google API calendars with separate
toggles; **all on initially**. Google calendars are grouped by connected account and keyed as
`accountEmail|calendarId` so multiple Google accounts can expose colliding calendar IDs safely. A
single master toggle controls calendar alarms as a feature.

**Refresh triggers:**
1. **`ContentObserver`** on the calendar URI → OS notifies us the instant a device calendar provider changes anything.
2. **`BOOT_COMPLETED`** → re-materialize after reboot.
3. **Periodic `WorkManager`** job about every **5 hours** → cheap Google API safety net + rolls the horizon forward. It is OS-batched, so not minute-exact, but targets roughly **4-5 syncs/day**.
4. **On app foreground/resume** and Settings **Refresh**. This is especially important for Google
   API calendars because Android does not send this app a provider notification when remote Google
   Calendar events are created, changed, or deleted.

**Horizon:** only events within a **rolling ~48 h window** get real `setAlarmClock` entries (no point scheduling a meeting 3 weeks out). The periodic worker, foreground sync, manual refresh, and observer keep the window populated.

**Google API auth model:** the app requests a short-lived OAuth access token via Google Identity
Services when the user taps "Add account". The flow can be repeated for multiple Google accounts.
Subsequent syncs ask Google Play services for another short-lived token silently per connected
account when consent is still valid. No refresh token is stored on-device. If one account needs user
interaction, background sync preserves that account's existing Google rows and Settings shows that
authorization is needed.

**Why not API keys:** API keys identify the calling Google Cloud project and quota/billing context;
they do not grant access to private user calendars. Private Google Calendar data requires OAuth
consent and a scoped access token.

---

## 6. Ringing experience

When an alarm fires, `AlarmReceiver`:
1. Grabs a short wakelock, **immediately computes + schedules the next occurrence** (so a crash mid-ring can't break the series).
2. Starts `RingService` (foreground) which owns the sound/vibration/torch.
3. Surfaces `RingActivity` two ways for reliability: it **directly `startActivity`s** the ring screen *and* sets a **full-screen intent** on the ringing notification. The full-screen intent only auto-launches the activity when the keyguard is locked or the screen is off — while the device is unlocked and interactive (the common timer case) the OS downgrades it to a heads-up, and some OEMs (HyperOS) throttle it even when locked. The direct launch makes the splash appear *consistently* whether the phone is locked, off, or in active use. It relies on the short-lived background-activity-launch grant a foreground service started from an exact alarm carries and, for the unlocked/interactive case, the **`SYSTEM_ALERT_WINDOW`** ("display over other apps") grant; if background activity launch is blocked it is a silent no-op and the full-screen intent still covers the locked/screen-off path. `RingActivity` itself uses `showWhenLocked` + `turnScreenOn` (manifest attrs and the API calls) and `FLAG_KEEP_SCREEN_ON`. Timers go through the exact same path.

**Ring behavior (all per-alarm, defaults in bold):**
| Setting | Default | Configurable |
|---|---|---|
| Sound | **system default alarm ringtone** | any device ringtone via system picker |
| Volume | **blast to full system alarm volume immediately** | optional gradual ramp (~30 s) |
| Vibration | **on** | toggle off |
| Flashlight flash | **off** | on (torch strobe ~1–2 Hz; no CAMERA permission needed; skipped if camera busy) |
| Snooze | **on, 10 min, unlimited** | duration + on/off per alarm |
| Auto-silence | **after 10 min → mark missed** | duration per alarm |
| Dismiss challenge (math/shake/QR) | **not built** (skipped for v1) | — |

Audio plays on `STREAM_ALARM` (bypasses ringer/DND by design — alarms are exempt). No screen-flash (would fight the dismiss/snooze controls).

Timers reuse the same low-level `Ringer` implementation and full-screen/foreground ringing path,
but their UX is timer-specific:
- The ring screen says **Timer**, shows the timer label or saved duration, and has **Dismiss** only
  (no snooze).
- Dismissing a timer returns that saved timer to idle so it can be started again.
- If a timer auto-silences, it is returned to idle and a missed/finished notification can be posted.
- Multiple timers may run in parallel. If more than one expires while another is already ringing,
  each expiry is persisted; the ring service can continue with the next still-ringing timer after
  the current one is dismissed.

---

## 7. Notifications

Three notification types (Material, Mocha-tinted):
- **Upcoming** — appears within a **configurable lead window before the alarm (default 1 h)**; carries a **Skip** action. Scheduled via a separate lightweight (non-wakeup) trigger at `fireTime − leadWindow`, so it costs nothing until then. On **Android 16 (API 36+)** it is promoted to an ongoing **Live Update**: a status-bar chip with a system-driven chronometer counting down to the fire time (no app polling). Being ongoing it is non-dismissible, so it is cleared explicitly when the alarm rings, is skipped, or is disabled/deleted/edited (`AlarmScheduler.scheduleUpcoming` clears any stale posted chip when it re-arms a *future* lead trigger, never when already inside the lead window).
- **Missed** — posted when an alarm auto-silences un-dismissed, so you know you slept through it.
- **Ringing** — the foreground-service notification backing the full-screen ring activity. On **Android 16 (API 36+)** it also requests promoted-ongoing treatment (it is already ongoing + max-priority on a high-importance channel) for an elevated lock-screen / always-on-display / status-bar chip.

**Android 16 "Live Updates":** promoted-ongoing notifications require the install-time `POST_PROMOTED_NOTIFICATIONS` permission, an ongoing notification with a content title and **no** custom RemoteViews, and a channel of at least `IMPORTANCE_DEFAULT`. The Upcoming channel is therefore `DEFAULT` but **silenced** (no sound/vibration) so the chip is eligible without making noise an hour early — safe to change because the app is pre-release with no installed base. Promotion uses `NotificationCompat.Builder.setRequestPromotedOngoing(true)` and is gated on `Build.VERSION.SDK_INT >= 36`; on API 35 the behavior is unchanged (a plain, dismissible reminder). Chip visibility on HyperOS is an on-device verification item (§14).

**Skip next occurrence:** every alarm (manual *and* calendar) exposes "skip next" both **in-app** and **from the Upcoming notification**, pre-emptively (before it rings). Skipping advances the series by one occurrence; the alarm resumes normally after. **No** global/group skip — it's strictly per-alarm.

**Individual calendar-alarm dismiss:** you can suppress one specific upcoming calendar instance ("not this meeting") while the feed keeps working for everything else.

---

## 8. UI

- **Home tabs:** Alarms and Timers are sibling top-level tabs. Settings remains reachable from the
  top bar. The app still opens to Alarms by default because alarms are the primary feature.
- **Alarm list:** manual + calendar alarms in one list; calendar ones tagged with a calendar icon + event title, time/repeat read-only (owned by the event). Each row: enable/disable switch, next-fire time, swipe/skip affordance.
- **Create / edit:** opens straight on the **clock-dial time picker** (drag the hand for hour, then minute; free dragging — 30→00 is one flick). **Pick time → Save → done.** Everything else (repeat, label, snooze, auto-silence, sound, vibration, ramp, torch, end condition) lives behind an **expandable "Advanced"** section, each with the defaults above. *Creation never requires anything but the time.*
- **Timer list:** saved timers in one list, each with duration/remaining time and state-specific
  controls (start, pause, reset/stop, delete). Multiple timers can be running at once.
- **Timer create / edit:** always opens a **built-in duration numpad**, never the circular analog
  alarm picker and never the system keyboard as the primary duration input. The numpad uses familiar
  clock-app entry: digits fill a `HH:MM:SS` duration from right to left. The bottom row includes
  both **0** and **00**. Editing an existing saved timer opens the same built-in numpad initialized
  from that timer's saved duration.
- **Settings:** calendar master toggle, per-calendar toggles, upcoming-notification lead window, permission status/links (exact-alarm, notifications, full-screen-intent, calendar).

**Time picker:** Material 3 `TimePicker` in **clock (dial) mode** — explicitly *not* text entry, *not* scroll-wheel spinners.

**Timer duration picker:** custom Compose numpad — explicitly *not* Material `TimePicker`, not the
alarm clock dial, and not a soft-keyboard text field.

**Home screen widget:** a dark widget matching the app theme. The main area shows a large local
24-hour clock. Under it: local date, an alarm icon, and the next scheduled Abnormalarm alarm
(`Today HH:mm`, `Tomorrow HH:mm`, or a short weekday/date fallback). The bottom row can show zero,
one, or two configured secondary time zones; each has a 24-hour clock and a user-editable label
(defaulting to the selected zone's city name). Widget clocks are implemented with platform
`TextClock` views so minute ticks are host-driven rather than app polling.

---

## 9. Theme — Catppuccin Mocha (dark only)

Dark-only, **no toggle, no dynamic color** ("vampire-friendly"). Material 3 color roles mapped from the official Mocha palette (MIT-licensed; NOTICE file included). Exact values:

```
Base    #1e1e2e   Mantle  #181825   Crust   #11111b
Text    #cdd6f4   Subtext1#bac2de   Subtext0#a6adc8
Surface0#313244   Surface1#45475a   Surface2#585b70
Overlay0#6c7086   Overlay1#7f849c   Overlay2#9399b2
Mauve   #cba6f7 (PRIMARY)
Blue    #89b4fa   Lavender#b4befe   Sapphire#74c7ec   Sky #89dceb
Teal    #94e2d5   Green   #a6e3a1   Yellow  #f9e2af   Peach #fab387
Red     #f38ba8   Maroon  #eba0ac   Pink    #f5c2e7   Flamingo #f2cdcd   Rosewater #f5e0dc
```

M3 role mapping (initial): `primary`=Mauve, `background`=Base, `surface`=Mantle, elevated `surfaceContainer*`=Surface0/1/2, `onSurface`=Text, `onSurfaceVariant`=Subtext0, `error`=Red, `tertiary`=Teal (tweakable).

---

## 10. Permissions (manifest)

| Permission | Why | Prompt? |
|---|---|---|
| `USE_EXACT_ALARM` | exact alarms (alarm-clock app) | none (install-time normal) |
| `RECEIVE_BOOT_COMPLETED` | reschedule after reboot | none |
| `POST_NOTIFICATIONS` | all notifications (API 33+) | runtime, once |
| `POST_PROMOTED_NOTIFICATIONS` | promoted-ongoing Live Updates (API 36) | none (install-time normal) |
| `USE_FULL_SCREEN_INTENT` | ring screen over lock screen | see note |
| `SYSTEM_ALERT_WINDOW` | launch the ring screen full-screen even while unlocked/in-use and on OEMs that throttle full-screen intents | runtime, via a system screen (offered in onboarding + Settings) |
| `FOREGROUND_SERVICE` + type | ringing service | none |
| `READ_CALENDAR` | calendar feed | runtime, only when feature enabled |

**⚠️ Full-screen intent on Android 14+:** the grant is auto-given to apps whose core function is alarms/calling, but we'll defensively check `NotificationManager.canUseFullScreenIntent()` and, if denied, deep-link the user to enable it. Because the full-screen intent alone is unreliable (downgraded to a heads-up while unlocked/interactive; throttled on some OEMs), `RingService` *also* directly `startActivity`s the ring screen, backed by **`SYSTEM_ALERT_WINDOW`** for the unlocked/in-use case (see §6). Both the full-screen-intent gate and the "display over other apps" grant surface as status rows + deep-links in onboarding and Settings → Background reliability.

**⚠️ Foreground-service type:** Android 14+ requires a declared FGS type. We'll use **`mediaPlayback`** (we are playing alarm audio) or `specialUse` with justification — **to verify which the platform accepts cleanly for an alarm started from an exact-alarm broadcast.** Apps holding exact-alarm permission are exempt from the background-FGS-start restriction when the service is started from the alarm.

---

## 11. Dev environment (Nix + host SDK + host emulator)

"Best of both worlds": **Nix provides the reproducible build toolchain; the host provides the SDK + the hardware-accelerated emulator.**

- **`flake.nix`** dev shell provides: a pinned **JDK** (17, matching your Zulu install; bumpable), plus convenience CLI (`gradle` is via the project's Gradle wrapper, so reproducible per-project). It **does not** vendor a multi-GB Android SDK.
- It **exports `ANDROID_HOME=$HOME/Library/Android/sdk`** (your existing host SDK — already has platform 35/36, build-tools 36.x) and validates required packages are present, printing a clear message if not.
- **`.envrc`**: `use flake` (direnv auto-loads the shell on `cd`).
- The **emulator stays host-side**: your existing **Pixel 9 AVD** runs via `~/Library/Android/sdk/emulator/emulator` on Hypervisor.framework — no Nix emulator, no GPU pain. Build with Gradle inside the Nix shell, `adb install` to the running emulator.

**SDK levels:** `minSdk 35`, `compileSdk`/`targetSdk` **36** (build-tools 36.x present). AGP/Gradle/Kotlin/Compose-BOM pinned in a Gradle **version catalog** (`libs.versions.toml`) at latest stable, verified to compile against API 36 during setup.

**Build/run loop:**
```
direnv allow                 # one-time
emulator -avd draftbros_-_Pixel_9 &     # or launch from Android Studio
./gradlew installDebug       # build (Nix toolchain) + install to emulator
```

---

## 12. Background reliability — one-time, device-aware setup

**Principle:** alarms must survive reboots and aggressive background management on *any* device, with **at most one** unobtrusive setup prompt — **never repeated nagging**. Framing is neutral and task-oriented ("so your alarms ring on time"); we never blame or name the manufacturer.

**Reschedule receivers (device-agnostic).** The scheduling layer re-materializes all alarms on every event that can clear the OS's registered alarms, all routing to one `rescheduleAll()`:
- `BOOT_COMPLETED` (reboot)
- `ACTION_MY_PACKAGE_REPLACED` (app update/reinstall)
- `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` (clock/zone change → recompute fire times)

**First-run setup — shown exactly once.**
- On first launch we run a single reliability check and, only if something actionable is detected, show **one** guided sheet. A persisted flag (`reliability_prompt_shown`) guarantees it never auto-appears again, whatever the outcome (dismissed, completed, or ignored).
- Each action **deep-links to the exact system screen** — no "go dig through Settings":
  1. **Run without battery limits** → `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (a system dialog, works on HyperOS and stock). This state *is* queryable via `PowerManager.isIgnoringBatteryOptimizations()`, so the step is shown only if not already granted.
  2. **Allow autostart** → deep-link straight to the OEM autostart screen when present (e.g. MIUI/HyperOS `com.miui.securitycenter` → `AutoStartManagementActivity`), wrapped in try/catch. Autostart state is **not** queryable by any public API, so this is a guided step (shown once), not a pass/fail check.
- Copy (neutral): *"To make sure your alarms ring exactly on time, let Abnormalarm start automatically and run without battery limits."*

**Detection & robustness.** OEM deep-links are gated on `Build.MANUFACTURER`/`BRAND` (xiaomi/poco/redmi → MIUI intents; trivially extensible to oppo/vivo/oneplus/realme/samsung). **Every** OEM intent is try/catch-guarded; if the component name doesn't resolve (it varies across HyperOS versions), we silently fall back to the app's standard App-Info screen so a button never dead-ends.

**Ongoing — no nagging.** Instead of re-prompting, a passive **"Background reliability"** row in Settings always reflects current detectable status (battery-optimization on/off; "next alarm registered?" read from `AlarmManager.getNextAlarmClock()`), with the same deep-link buttons. So if a system update silently resets a toggle, you can re-check **on your own terms** — the app updates this row via a silent on-open self-check but **never** pops a dialog for it again.

**Kill diagnostics (read-only, no permission).** `reliability/StartupDiagnostics` reads `ApplicationExitInfo` (`getHistoricalProcessExitReasons`) and `ApplicationStartInfo` (`getHistoricalProcessStartReasons`, API 35) for the app's own package — no permission needed — to *explain* recent behavior in the same Settings section: a **"Last stopped"** line (neutral reason + time, with a count when the system has stopped the app repeatedly) and a **"Last started by"** line (e.g. "Alarm" — confirms the OS is cold-starting us to ring). These cannot *prevent* OEM background-killing; they make it visible, so the owner can tell when allowing autostart / removing battery limits is actually paying off. Framing stays neutral (never names or blames the manufacturer). Which `REASON_*` code HyperOS emits for its background kills is an on-device verification item (§14).

**Honest limit (stated in-app once, neutrally):** autostart/force-stop are OS-enforced and can't be flipped programmatically by any app, and may reset after a major system update; the Settings status row is the quiet way to notice and re-fix.

---

## 13. Performance & correctness notes

- **Idle is near-zero:** dormant process; only OS-held `setAlarmClock` entries + one `ContentObserver`. The only planned background wakeup is the **~5-hour** calendar sync worker, deferrable and batched by WorkManager.
- **Timers are also event-driven:** a running timer is one exact OS trigger plus one Room row with an
  `endsAtMillis`; no service runs while counting down. Paused timers store remaining duration and
  own no OS trigger.
- **Widget is host-driven:** widget clocks use platform `TextClock`; the app updates the widget only
  when configuration or scheduled-alarm state changes.
- **Room** is in-process SQLite with no background threads when idle; queried only on UI/edit/reschedule.
- **Memory:** Compose + Room + a foreground service only while ringing. Expect tens of MB active, negligible when backgrounded.
- **Crash-safety:** next alarm occurrence is scheduled *before* ringing starts; running timers persist
  their expiry time. `BOOT_COMPLETED` + app-update/time-change receivers reconcile alarms and timers,
  so no alarm or running timer can be silently lost.

---

## 14. Open items to verify empirically (early, before building on them)
1. Calendar **default-reminder** rows: for device-provider calendars, do defaults appear as explicit
   `Reminders` rows? (`adb content query`)
2. **Full-screen-intent** auto-grant on API 35 emulator; wire the fallback regardless.
3. **FGS type** the platform accepts for an alarm-audio service started from an exact-alarm broadcast.
4. **MIUI/HyperOS deep-link intents** (autostart, battery) resolve on the POCO F7 Ultra's HyperOS 2 version; confirm the try/catch fallback to App-Info works when they don't.
5. **Google Calendar API OAuth setup**: verify debug/release SHA-1 Android OAuth clients, Calendar
   API enabled, every connected account added as a test user while consent is in Testing, and the
   Settings "Add account" flow.
6. **Acceptance testing must run on the real POCO F7 Ultra**, not just the Pixel 9 emulator — stock Android won't reproduce HyperOS background behavior. Pixel emulator = dev/iteration; POCO = reliability acceptance.
7. **Android 16 Live Update chip visibility**: confirm the silenced `IMPORTANCE_DEFAULT` Upcoming
   channel (and the ringing notification) actually render as a status-bar / AOD chip on HyperOS 2. If
   not, raise importance or accept shade-only — promotion is best-effort and the OS decides.
8. **`ApplicationExitInfo` reason codes on HyperOS**: verify which `REASON_*` constant HyperOS reports
   for its background kills (likely `SIGNALED`/`OTHER`/`USER_REQUESTED`) so the "Last stopped"
   classification in `StartupDiagnostics` reads accurately on the real device.

---

## 15. Build order (proposed)
1. Project scaffold: `flake.nix` + `.envrc`, Gradle + version catalog, manifest, Application, Mocha theme, empty Compose nav.
2. Data layer: Room entities/DAO, `AlarmRepository`, settings DataStore.
3. `nextOccurrence` engine **+ unit tests** (all 9 modes + end conditions + edge cases).
4. Scheduling: `AlarmScheduler`, `AlarmReceiver` (fire→reschedule), `RescheduleReceiver` (boot/update/time).
5. Ring path: `RingService` + `Ringer` (sound/vibe/torch) + `RingActivity` full-screen; snooze/auto-silence/missed.
6. Notifications: channels, upcoming(+Skip), missed.
7. UI: list, clock-dial picker, create/edit + Advanced, settings.
8. Calendar: `CalendarRepository`, Google Calendar API fallback, selection rules, `ContentObserver`,
   `CalendarSyncWorker`, per-calendar toggles, individual dismiss.
9. Reliability setup (§12): one-time guided sheet + deep-links + passive Settings status row.
10. Polish, verify the open items, manual test on the Pixel 9 AVD, then **reliability acceptance on the real POCO F7 Ultra**.
11. Timers: saved timer persistence, parallel running timers, numpad create/edit, exact expiry
    receiver, timer ring UX, and emulator smoke test.
12. Home screen widget: large local clock, date + next alarm, optional secondary time zones, and
    configuration flow.
```
