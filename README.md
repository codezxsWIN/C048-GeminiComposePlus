# Gemini Compose+

**Roll number C048 · Mobile Application Development Assignment 1**

Gemini Compose+ is an upgraded Gemini chat built entirely with Kotlin and Jetpack Compose. Its differentiator is transparent context: users can see approximate request size, exclude private messages from future AI requests, and protect important details during context trimming.

## Highlights

- Premium Material 3 LazyColumn chat with stable Room IDs, timestamps, adaptive layout, dark mode, loading, retry, and safe auto-scroll
- Genuine multi-turn Gemini requests behind a repository interface
- Immutable StateFlow ChatUiState, lifecycle-aware collection, and stateless screen composables
- Voice-to-draft input through Android RecognizerIntent
- Room-backed messages/request states and DataStore-backed draft/instructions
- Context Health Meter, Memory Firewall, and Important Detail Protection
- Persistent custom AI instructions and allowed-context chat summaries
- Privacy & Security Center with credential status and deletion controls
- AES-256-GCM key encryption using Android Keystore
- Slash commands: /summarize, /formal, and /reset-instructions
- R8 minification/resource shrinking plus unit and Compose UI tests

## Requirements

- Android Studio with JDK 17
- Android SDK 35
- Android 8.0 / API 26 or newer device
- Gemini API key for live responses

The project contains no handwritten Java. Room/KSP may generate interoperable sources during a build.

## API-key setup

1. Copy local.properties.example to local.properties in the repository root.
2. Keep the sdk.dir line Android Studio creates and add:

~~~properties
GEMINI_API_KEY=replace_with_your_own_key
~~~

3. Sync and rebuild.

If the property is absent, Gradle checks the GEMINI_API_KEY environment variable. If neither exists, the project still builds and tests without a secret; a safe setup error appears only when a live request is attempted.

local.properties is ignored by Git. Never place a key in Kotlin, XML, README files, screenshots, logs, or commits. Revoke any key that was ever posted publicly.

## Security model

At first launch, the app creates a 256-bit AES key inside Android Keystore and encrypts the configured Gemini key with AES/GCM/NoPadding. Only ciphertext and its non-secret IV are stored in a dedicated Preferences DataStore. The repository decrypts it in memory immediately before a Gemini call. The encrypted record is excluded from backup, and app backup is disabled.

This protects data at rest, not perfect client-side secrecy. A key compiled into a mobile app can be extracted from its APK or inspected at runtime. A production app should use an authenticated backend, restrict and rotate keys, rate-limit requests, and consider Firebase App Check. R8 is defence in depth, not a secret vault.

The starter Google AI client SDK is retained for assignment compatibility behind GeminiRepository. Firebase AI Logic is the intended future production migration point.

## Context rules

Messages are ordered by timestamp and stable ID. Failed requests, summaries, and excluded messages are removed before a request. Protected details are reserved first; newest ordinary messages fill the remaining conservative 24,000-token app budget. The current draft is sent exactly once.

The meter is explicitly approximate and does not claim to expose Gemini's tokenizer. API context-limit errors are handled separately.

Summaries use only Included or Protected messages. They are stored locally as excluded artifacts and never recursively sent back to Gemini.

## Build and test

~~~powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
~~~

Run instrumented tests with a device/emulator:

~~~powershell
.\gradlew.bat connectedDebugAndroidTest
~~~

Voice input needs an installed speech service. Recognized text becomes an editable draft and is never sent automatically.

## Assignment checklist

- [x] Kotlin, Jetpack Compose, Material 3
- [x] LazyColumn, stable keys, controlled auto-scroll
- [x] StateFlow and collectAsStateWithLifecycle
- [x] Stateless UI with hoisted callbacks
- [x] Loading, validation, typed errors, retry
- [x] Window-size adaptive layout and API 26 theme fallback
- [x] RecognizerIntent voice input
- [x] Preferences DataStore and Room persistence
- [x] Local property/environment API-key fallback and blank example
- [x] AES-256-GCM Android Keystore persistence
- [x] ViewModel, context, and Compose tests
- [x] R8/resource shrinking release build
- [x] Honest security documentation

## Competition demo

1. Send two related prompts to demonstrate real multi-turn context.
2. Exclude one message and open the Context Drawer.
3. Protect an important detail and show the counters.
4. Add and reset custom instructions.
5. Summarize the allowed chat.
6. Use voice input and edit the resulting draft.
7. Restart to show persisted history and draft.
8. Open Privacy & Security Center.
9. Resize to tablet width to reveal the context rail.

Use synthetic demo data only.

## Git and pull request

Development branch: C048-development

~~~powershell
git status
git add .
git diff --cached
git commit -m "C048: complete Gemini Compose+ assignment"
git push -u origin C048-development
~~~

Create a pull request in codezxsWIN/C048-GeminiComposePlus from C048-development into master titled:

> C048: Build Gemini Compose+ upgraded chat experience

Exclude local.properties, build folders, signing files, logs, and real user data from the final ZIP.
