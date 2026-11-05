# Travel Planner for Android ("Voyager")

Native Android companion to the [travel-planner](https://github.com/rupakc/travel-planner) web app:
same account, same backend, same plans. Kotlin, Jetpack Compose, Material 3. MIT licence.

Status: foundation (build system, core modules, app shell, placeholder feature screens).

## Build

Requirements: JDK 21, Android SDK with platforms 36 and 37 (compileSdk 37, targetSdk 36).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleLocalDebug          # local flavour: http://10.0.2.2:8001 (emulator → host backend)
./gradlew assembleProdRelease -PPROD_BASE_URL=https://your-host   # HTTPS only
./gradlew spotlessCheck detekt lintLocalDebug testLocalDebugUnitTest assembleLocalDebug assembleProdRelease   # what CI runs
```

Run the backend locally (`./start.sh` in the web repo) and change the server in Settings → Server
if you use a phone on your LAN. Push notifications need `app/google-services.json` (optional).

## Docs

- `docs/superpowers/specs/2026-11-05-android-app-design.md` — design
- `docs/reference/` — backend API contract and web UI inventory
- `docs/Foundation-Interfaces.md` — interfaces feature modules build on
- `docs/Release.md` — signing and release notes
- `MEMORY.md` — decision log
