# TickTock Android App

TickTock is an Android 11+ application that analyzes pendulum clock beats in real time and reports beat asymmetry.

## What The App Does

- Records live audio from the phone microphone.
- Detects alternating pendulum beats (L and R).
- Computes period, asymmetry in milliseconds, and asymmetry percentage.
- Shows asymmetry on a gauge from -25% to +25%.
- Uses arrow color coding:
	- Green for absolute asymmetry below 2.5%
	- Yellow for absolute asymmetry from 2.5% to 10%
	- Red for absolute asymmetry above 10%

## How To Use

1. Open the app.
2. Set `Initial guess period` in milliseconds (default `1200`).
3. Tap `Start` and allow microphone permission.
4. Wait while the app searches and refines beat parameters.
5. Read `Period`, `Asymmetry [ms]`, and `Asymmetry [%]`.
6. Tap `Stop` to end audio acquisition.

## Build (Release v1.0)

1. Install Android Studio (or Android SDK + JDK 17).
2. From project root, generate wrapper if needed:

```powershell
gradle wrapper
```

3. Build release APK:

```powershell
.\gradlew.bat clean assembleRelease
```

4. Release output:

- `app/build/outputs/apk/release/app-release-unsigned.apk`

## License

GNU GPL v3. See COPYING.
