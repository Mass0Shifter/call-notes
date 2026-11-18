# CallNotes — Implementation Handoff

**Goal:** An Android app for a Samsung Galaxy S23 (Android 14) that records the user's own
calls (cellular + WhatsApp/Telegram/other VoIP), then transcribes each recording and produces
a summary with action points / todos for work, exportable to notes.

This document is written so a fresh coding session (with a working Android SDK and the ability
to push to a repo + run CI) can take over and finish without re-deriving context.

---

## 0. Status of this repo

Already written (compilable skeleton — see tree below):
- Gradle project (AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09, minSdk 29 / targetSdk 34)
- `AndroidManifest.xml` with all services, receiver, QS tile, share-target
- `CallNotesApp` (notification channels)
- Data model + JSON file store
- RecorderService (foreground `microphone` service)
- CallDetectorService (AccessibilityService for VoIP detection)
- PhoneStateReceiver (cellular call detection)
- RecordTileService (Quick Settings manual record)
- Transcriber + Summarizer (OpenAI/Groq Whisper + chat completions over OkHttp)
- Compose UI (MainActivity + list/detail/settings)
- `.github/workflows/build.yml` → builds a debug APK artifact on push

**Remaining work is marked with `TODO(handoff)` in code and listed in §5 below.**

## 1. The recording reality (do not fight this — it drove the design)

On a non-rooted S23 / Android 14:
- **User's own voice:** always captured cleanly.
- **Cellular (GSM) calls — other party:** Android gives apps no access to the call downlink.
  BUT Samsung's own built-in call recorder (Phone app → Settings → Record calls) writes WAV/M4A
  files to shared storage. **Strategy: let Samsung record, then import those files** (§4.3).
  This is the most reliable other-party audio on this device.
- **VoIP (WhatsApp/Telegram/etc.) — other party:** no API access to the VoIP stream.
  The only non-root capture is the **microphone**, which picks up the earpiece/loudspeaker.
  → Works well **on speakerphone**, poorly at the ear. We expose a clear in-app hint telling
  the user to use speaker for VoIP, and record with `MediaRecorder`/`AudioRecord` on
  `MIC` (fallback `VOICE_COMMUNICATION`). Do not promise more than this.
- `VOICE_CALL` / `VOICE_DOWNLINK` audio sources require a system/privileged app — out of scope.

### Consent / legal
Recording calls the user is party to is legal in one-party-consent jurisdictions; other places
require informing the other party. We ship an **optional spoken announcement** at record start
(`SettingsStore.announceRecording`) and a persistent notification. Default is silent capture of
the user's own calls. Keep this a user choice — do not design the app to defeat the other
party's awareness.

## 2. Architecture

```
Call starts
 ├─ cellular → PhoneStateReceiver (OFFHOOK) ─┐
 ├─ VoIP     → CallDetectorService          ─┤→ start RecorderService (FGS, microphone)
 └─ manual   → RecordTileService            ─┘        │
                                                      ▼
                                          MediaRecorder → .m4a in app filesExternalDir
                                                      │  (on stop)
                                                      ▼
                                   Recording row saved to JSON store (RecordingStore)
                                                      │  (user taps "Transcribe", or auto)
                                                      ▼
                        Transcriber (Whisper) → transcript text ──► Summarizer (LLM)
                                                      │                    │
                                                      ▼                    ▼
                                           store.transcript        store.summary + actionItems
                                                      │
                                                      ▼
                                   Detail screen → Export/Share (markdown) → Notes/Docs/email
```

Processing runs as a short foreground task (`CHANNEL_PROCESSING`) or WorkManager (optional upgrade).

## 3. Package layout (`com.nacham.callnotes`)

| File | Responsibility |
|------|----------------|
| `CallNotesApp` | notification channels |
| `audio/RecorderService` | FGS, owns MediaRecorder, writes file, creates store row |
| `audio/AudioStore` | resolves output dir / filenames |
| `call/CallDetectorService` | AccessibilityService; maps foreground pkg → call active |
| `call/PhoneStateReceiver` | cellular OFFHOOK/IDLE → start/stop service |
| `call/RecordTileService` | QS tile toggles manual recording |
| `call/CallEvents` | shared enum + constants (packages to watch, actions) |
| `data/Recording` | data class (id, path, source, contact?, startedAt, durationMs, transcript, summary, actionItems, status) |
| `data/RecordingStore` | JSON-file persistence + StateFlow list |
| `ai/Transcriber` | POST audio to Whisper endpoint, return text |
| `ai/Summarizer` | POST transcript to chat endpoint, return summary + action items JSON |
| `ai/AiConfig` | provider base URLs, model names |
| `util/SettingsStore` | DataStore: apiKey, provider, autoProcess, announceRecording, voipHint seen |
| `ui/MainActivity` | Compose host, permission flow, share-intent intake |
| `ui/screens/*` | Home (list), Detail, Settings, Onboarding |
| `ui/CallNotesViewModel` | glue store ↔ transcriber ↔ summarizer |

## 4. Key implementation notes

### 4.1 Foreground service + mic from background
Target 34: the service is type `microphone` (declared) and needs `FOREGROUND_SERVICE_MICROPHONE`.
Starting a mic FGS **from the background** is restricted on 14. An AccessibilityService *is* a
valid trigger context, but to be safe we start recording from the Accessibility/PhoneState
callback immediately and call `startForeground()` within the service's `onCreate` with the
microphone type. If a background-start exception is caught, fall back to posting a full-screen
"Tap to record" notification. See `RecorderService.start()` `TODO(handoff)`.

### 4.2 VoIP detection
`CallDetectorService` listens for `TYPE_WINDOW_STATE_CHANGED`. When the foreground package is one
of `CallEvents.VOIP_PACKAGES` **and** `AudioManager.mode == MODE_IN_COMMUNICATION`, treat a call
as active; when mode leaves communication, stop. Debounce 1.5s. The AudioManager.mode check is
what distinguishes an actual call from just opening the app. `TODO(handoff)`: tune per-app, and
optionally detect the in-call UI via node text ("End call") for apps that don't flip audio mode
promptly.

### 4.3 Importing Samsung's own call recordings
Samsung writes to `Recordings/Call/` (Android 13+) or `Sounds/` / `Call/` on older/skinned
builds. Use `MediaStore.Audio` queries filtered by `RELATIVE_PATH LIKE '%Call%'` and import new
files since last scan. Needs `READ_MEDIA_AUDIO`. `TODO(handoff)`: implement `CallImport.scan()`
and a "Import phone-call recordings" button on Home. This is the reliable path for cellular
other-party audio.

### 4.4 Transcription
`Transcriber` posts multipart to an OpenAI-compatible `/audio/transcriptions` (model
`whisper-1`) — works with OpenAI or Groq (`whisper-large-v3`, much cheaper/faster). Base URL and
key from `SettingsStore`. Keep the key on-device only. For fully offline, a later upgrade is
whisper.cpp via JNI — out of scope for v1, noted in README.

### 4.5 Summary + action items
`Summarizer` sends the transcript to a chat/completions endpoint with a system prompt that
returns strict JSON: `{ "summary": "...", "action_items": ["..."], "notes": "..." }`. Parse and
store. Prompt is in `ai/Summarizer.PROMPT`.

### 4.6 Export
Detail screen builds a markdown block (contact, date, summary, checkbox action items, full
transcript) and fires `ACTION_SEND` text/markdown so the user drops it into Google Keep/Docs/
email. `TODO(handoff)`: optional direct Google Drive/Docs via their connector later.

## 5. Remaining TODO(handoff) checklist
1. Finish `RecorderService` start/stop + background-start fallback.
2. Implement `CallImport` (MediaStore scan) + Home button.
3. Wire `CallNotesViewModel.process()` (transcribe → summarize → persist, with progress).
4. Onboarding screen: request RECORD_AUDIO, POST_NOTIFICATIONS, enable Accessibility, add QS tile,
   set API key, VoIP-speaker hint.
5. Fill Detail + Settings composables (skeletons present).
6. App icon (placeholder vector present) and strings.
7. Generate Gradle wrapper jar (`gradle wrapper --gradle-version 8.9`) — CI does this; for local,
   open in Android Studio once.
8. Decide auto-process vs manual (SettingsStore.autoProcess) default = manual to control cost.

## 6. Build & ship
- **Android Studio:** open the project root, let it sync, Run on device, or Build → Build APK.
- **CI:** push to any repo; `.github/workflows/build.yml` runs `./gradlew assembleDebug` (after
  generating the wrapper) and uploads `app-debug.apk` as an artifact. Download it, enable "Install
  unknown apps" on the S23, install.
- Signing for a release APK: add a keystore + `signingConfigs` later; debug APK is fine for
  personal sideloading.

## 7. First-run setup on the phone (for the end user)
1. Install APK, grant Microphone + Notifications.
2. Settings → Accessibility → CallNotes → On (enables VoIP call detection).
3. Add the "Record call" Quick Settings tile (swipe down → edit tiles).
4. Enter a Whisper API key (OpenAI or Groq) in app Settings.
5. For WhatsApp/Telegram, use **speakerphone** for best other-party capture.
6. For normal calls, turn on Samsung's own call recording; the app imports those files.
