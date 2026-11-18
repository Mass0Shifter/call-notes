# CallNotes

Android app that records your own calls (cellular + WhatsApp/Telegram/other VoIP) on a
Samsung Galaxy S23, then transcribes them and generates a summary + action items for work.

> **Start here if you're continuing this build:** read [`HANDOFF.md`](HANDOFF.md). It has the
> architecture, the per-file responsibilities, the Android recording constraints, and the
> `TODO(handoff)` checklist of what's left.

## What works vs. what's left
This repo is a **compilable skeleton** with the full structure, services, AI pipeline, and UI in
place. Remaining items are marked `TODO(handoff)` in the code and listed in HANDOFF §5
(recorder background-start fallback, Samsung recording import, share-intent import, onboarding).

## Build

### Android Studio (easiest)
Open the project root, let it sync (this also generates the Gradle wrapper), then Run on a device
or **Build → Build Bundle(s)/APK(s) → Build APK(s)**.

### GitHub Actions
Push to `main`. `.github/workflows/build.yml` builds `app-debug.apk` and uploads it as an
artifact. Download it from the run's **Artifacts** section and sideload it.

### Command line
```bash
gradle wrapper --gradle-version 8.9   # once, if gradlew is absent
./gradlew assembleDebug               # APK at app/build/outputs/apk/debug/app-debug.apk
```

## On-device setup
1. Install the APK; grant **Microphone** + **Notifications**.
2. **Settings → Accessibility → CallNotes → On** (enables VoIP call detection).
3. Add the **Record call** Quick Settings tile (edit tiles in the shade).
4. In the app's **Settings**, pick Groq or OpenAI and paste a Whisper API key.
5. For WhatsApp/Telegram, use **speakerphone** so the other party is captured.
6. For normal calls, enable Samsung's own call recording; the app imports those files.

## Recording limitations (important)
On a non-rooted S23 the OS blocks apps from the call audio stream. Your own voice always records;
the other party is reliably captured only via Samsung's built-in recorder (cellular) or on
speakerphone (VoIP). See HANDOFF §1.

## Legal
Recording calls you take part in is legal in one-party-consent jurisdictions; elsewhere you must
tell the other party. The app has an optional spoken announcement and always shows a recording
notification. Use responsibly and per your local law.

## Stack
Kotlin, Jetpack Compose (Material 3), AccessibilityService + QS tile + foreground mic service,
OkHttp to OpenAI-compatible Whisper + chat endpoints. No backend; your API key stays on device.
