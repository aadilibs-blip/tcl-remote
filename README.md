# TV Remote (Android)

A Kotlin + Jetpack Compose remote for Android TVs (built around a TCL Android TV), laid out like the TCL remote:
mute/power, favorites/assistant/settings, round D-pad with OK, back/home/live TV, volume pill, P+/P−,
recents/info/all apps, and app shortcuts.

It uses the Android TV Remote protocol v2 (the same one the Google TV app uses). There are no third-party
libraries beyond AndroidX: the protocol code is in `app/src/main/java/com/aadil/tclremote/net/`.

## Build

1. Install Android Studio (current stable).
2. File → Open → select this `TclRemote` folder and let Gradle sync.
3. Plug in your phone (USB debugging on) or start an emulator, then press Run.

Android Studio creates the Gradle wrapper itself on first sync. To get an installable file, use
Build → Build APK(s); the APK ends up in `app/build/outputs/apk/debug/`.

## First use

1. Phone and TV on the same Wi-Fi. On the TV, make sure "Android TV Remote Service" isn't blocked.
2. Open the app. Your TV should appear in the list (or type its IP address).
3. Tap it. The TV shows a 6-character code. Type it in the app. You only do this once per TV.
4. The app reconnects to that TV automatically next time. The phone's volume buttons control the TV volume while the remote is open.

## Files

| File | What it does |
|---|---|
| `ui/RemoteApp.kt` | All screens: TV picker, pairing code, the remote |
| `RemoteViewModel.kt` | Connection flow: discover → pair (first time) → connect → send keys |
| `net/Discovery.kt` | Finds TVs on the network (`_androidtvremote2._tcp`) |
| `net/PairingSession.kt` | One-time pairing on port 6467 |
| `net/RemoteClient.kt` | Key presses and app launches on port 6466 |
| `net/ClientIdentity.kt` | The phone's certificate, kept in the Android Keystore |
| `net/Proto.kt` | Tiny protobuf reader/writer and message framing |
| `net/Keys.kt` | Android key codes used by the buttons |

## Known limits

- Written without being compiled or run against a real TV. If Gradle or the TV connection fails, send the error text and it can be fixed quickly.
- Netflix, Prime Video and YouTube buttons open web links on the TV; the TV decides which app handles them.
- Some keys (Info, Recent apps, All apps) depend on what the TCL firmware supports.
- The on-screen keyboard (typing text into TV search) isn't included yet.
