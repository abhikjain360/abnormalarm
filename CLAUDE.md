# Abnormalarm — working notes for Claude

A precise, low-overhead Android alarm clock (native Kotlin + Compose, Android 15+). **`DESIGN.md` is
the source of truth** for every product/architecture decision — read it before changing behavior.
This file is the engineering handoff: what exists, what's stubbed, how to build, and the traps.

## TL;DR for the next session
**All build-order steps (1–10) are implemented and the app runs end-to-end on the Pixel 9 emulator
(API 36).** Persistence (Room), the full UI (list / 24-hour clock-dial picker / edit+Advanced /
settings), the calendar layer, ring polish (snooze, auto-silence, missed, volume ramp), the upcoming
notification + Skip, the full-screen-intent gate, and the one-time reliability onboarding are all
wired and smoke-tested (create→persist-across-process-death→schedule verified; `setAlarmClock` +
non-wakeup upcoming trigger confirmed in `dumpsys alarm`). 18 engine unit tests pass. Calendar now has
two backends: the legacy Android `CalendarContract` provider and a multi-account OAuth-backed Google
Calendar API fallback, added after the POCO exposed no Google events through `CalendarContract`. The
remaining work is **on-device acceptance** (especially reliability on the POCO F7 Ultra / HyperOS 2),
Google Cloud OAuth setup/verification, the empirical checks in `DESIGN.md §14`, and polish. The time picker is
**24-hour only** (no AM/PM) per the owner's preference — see [[android-alarm-confirmed-decisions]].

## Build / run
This repo uses Nix for the build toolchain and the **host** Android SDK + emulator (DESIGN.md §11).
```bash
direnv allow                 # once; or prefix commands with: nix develop .
./gradlew :app:assembleDebug :app:testDebugUnitTest   # build APK + run engine tests
emulator -avd draftbros_-_Pixel_9 &                   # host emulator (or launch from Android Studio)
./gradlew installDebug                                # install to the running emulator
```
- Toolchain: **AGP 9.2.0, Gradle 9.4.1, Kotlin 2.3.10, Compose BOM 2026.05.00, JDK 17**,
  `minSdk 35 / compileSdk 36 / targetSdk 36`. All versions in `gradle/libs.versions.toml`.
- `nix develop` exports `ANDROID_HOME=$HOME/Library/Android/sdk` (host SDK; not Nix-vendored).

## What's implemented and working
- **Dev env**: `flake.nix`, `.envrc`, `.gitignore`.
- **Gradle scaffold**: version catalog, wrapper, build files. Builds a real `app-debug.apk`.
  KSP + Room + DataStore + WorkManager + navigation-compose + Google Play Services auth are wired.
  **KSP is `2.3.9`** (KSP2's
  plain-version line, decoupled from the Kotlin patch — the old `<kotlin>-<ksp>` scheme ended at
  2.2.21; `2.3.9` pairs fine with Kotlin 2.3.10).
- **Repeat engine** (`domain/schedule/NextOccurrence.kt`): pure `nextOccurrence` + `nextTrigger`,
  all `RepeatRule` modes + `RepeatEnd`. **18 unit tests pass** (`NextOccurrenceTest`) covering
  skip-short-months, Feb-29 leap, last-weekday, DST, end conditions, and `OnceOnDate`. Most-tested
  code in the repo — keep it that way; add a test for any rule change.
- **Domain model** (`domain/model/`): `Alarm`, `RepeatRule` (incl. `OnceOnDate` for calendar alarms),
  `RepeatEnd`, `RingSettings`, `AlarmSource`. **Pure Kotlin — no Android imports.** Keep it so.
- **Persistence** (`data/`): Room v2 — `db/AlarmEntity` (RingSettings `@Embedded`; `Converters` encode
  RepeatRule/RepeatEnd/LocalTime/AlarmSource as pipe-delimited strings), `AlarmDao`, `AbnormalarmDb`,
  `RoomAlarmRepository`. Calendar rows carry string provider/calendar/event keys for Google API ids,
  while the old numeric `calendarEventId` is retained for local-provider compatibility. `settings/SettingsRepository`
  over DataStore (calendar feed + connected Google account emails + local/Google per-calendar toggles,
  upcoming lead minutes, reliability-shown flag). Verified to survive process death.
- **Reliability core** (`scheduling/`): `AlarmScheduler` (single-fire `setAlarmClock`, snooze one-offs,
  upcoming non-wakeup trigger, distinct request-code namespaces), `AlarmReceiver` (reschedule-next
  FIRST then ring; handles snooze re-fire), `RescheduleReceiver` (boot/update/time/zone →
  `rescheduleAll` + calendar resync), `UpcomingReceiver` (lead notification + Skip).
- **Ring path** (`ring/`): `Ringer` (USAGE_ALARM audio + volume ramp, `VibratorManager`, torch),
  `RingService` (foreground `mediaPlayback`, snooze scheduling, auto-silence → missed notification),
  `RingActivity` (full-screen over lock screen, shows label/time, snooze hidden when disabled).
- **UI** (`ui/`): navigation-compose host; `list/` (alarm list, enable/skip/delete), `edit/`
  (24-hour clock-dial `TimePicker` + expandable Advanced with a full `RepeatPicker` for every mode,
  end condition, ringtone picker, snooze/auto-silence steppers, vibrate/flashlight/ramp toggles),
  `settings/` (calendar master + per-calendar toggles, lead-time, reliability/FSI status rows),
  `reliability/ReliabilityOnboarding` (one-time §12 sheet). **Time is 24-hour only — no AM/PM.**
- **Calendar** (`data/calendar/`): `CalendarRepository` (CalendarContract selection rules + reminder
  expansion w/ 5-min fallback), `GoogleCalendarApiRepository` (Calendar API `calendarList.list` +
  `events.list` over short-lived Google Identity Services tokens, one token per connected account),
  `CalendarSync` (idempotent 48 h horizon reconcile into `OnceOnDate` alarm rows; per-instance
  dismiss = disable), `CalendarObserver` (permission-guarded ContentObserver), `CalendarSyncWorker`
  (~5-hour periodic + unique on-demand). `MainActivity.onResume` enqueues on-demand sync so Google API event
  deletions are reconciled when returning from Google Calendar; Settings Refresh also enqueues sync.
- **Notifications** (`notifications/Notifications`): 3 channels + upcoming(+Skip)/missed posters +
  `canUseFullScreenIntent` gate.
- **Theme**: Catppuccin Mocha → Material 3 dark, primary = Mauve. Dark only.

## What's left (no longer code-blocked — verification & release)
1. **On-device acceptance**, especially **reliability on the POCO F7 Ultra (HyperOS 2)** — stock
   Android / the Pixel 9 emulator won't reproduce HyperOS background-killing.
2. **Google Cloud OAuth setup** for Calendar API: Android OAuth clients for debug and release SHA-1,
   Calendar API enabled, every connected Google account added as a test user while consent screen is
   in Testing.
3. **Empirical checks in `DESIGN.md §14`**: calendar default-reminder rows syncing, FSI auto-grant,
   FGS-type acceptance from an exact-alarm broadcast, MIUI/HyperOS deep-link component resolution.
4. **Release hardening**: local release signing is wired through ignored `keystore.properties` and
   `signing/abnormalarm-release.jks`; still verify Play/App Bundle setup if publishing.
5. **Nice-to-haves**: `RepeatEnd.OnDate` isn't exposed in the end-picker UI yet (engine supports it;
   only Never/AfterCount are surfaced); snooze one-offs aren't cancelled by `cancel()` (a snooze can
   still fire after disabling mid-snooze — low-impact edge).

## Traps already hit / things to know
- **AGP 9 has built-in Kotlin.** Do NOT apply `org.jetbrains.kotlin.android` anywhere — it collides
  ("Cannot add extension with name 'kotlin'"). The Compose compiler plugin IS still applied. JVM
  target derives from `compileOptions.targetCompatibility` (no `kotlin{}` block needed).
- **The wrapper** was generated with Nix's Gradle (8.14.3) in a throwaway dir then copied in, because
  AGP 9.2 needs Gradle 9.4.1 which the bootstrap Gradle can't configure. To change the wrapper, do
  the same dance (see git history of `gradle/wrapper/`).
- **Domain must stay Android-free.** Put Room entities/converters in `data/`, never in `domain/`.
- **Google Calendar API requires OAuth, not API keys.** API keys only identify a Google Cloud project;
  private user calendar data requires a scoped OAuth access token. This app uses the read-only
  `https://www.googleapis.com/auth/calendar.readonly` scope through Google Identity Services and stores
  no refresh token. Settings supports adding multiple Google accounts; calendar toggles are account-
  scoped as `accountEmail|calendarId`.
- **Real device ≠ emulator.** Dev/iterate on the Pixel 9 emulator; but reliability ACCEPTANCE must
  happen on the owner's **POCO F7 Ultra (HyperOS 2)** — stock Android won't reproduce HyperOS
  background-killing. The §12 onboarding (Autostart + battery "No restrictions") is the manual fix;
  no app can bypass it programmatically.
- **Debug APK is ~65 MB** (unshrunk; `material-icons-extended` + Compose tooling). Release R8 +
  resource shrinking cut this hugely. If size matters, drop `material-icons-extended` and use only
  the icons you need. Note: APK size ≠ the runtime-RAM goal, which this architecture already meets.

## Layout
`domain/` pure model + engine · `data/` persistence · `scheduling/` exact-alarm core ·
`ring/` ring service/activity/ringer · `notifications/` channels · `ui/` Compose + theme ·
`App.kt` DI container + Application · `MainActivity.kt` single activity.
