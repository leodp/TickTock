# TickTock

TickTock is an Android app for setting the beat of a pendulum clock. It listens to the clock through the phone microphone, detects the alternating left and right beats, and shows whether the clock is running evenly.

## What It Does

- Listens to live audio from the phone microphone.
- Detects the pendulum beat pattern in real time.
- Estimates the beat period, asymmetry in milliseconds, and asymmetry percentage.
- Displays the asymmetry on a gauge from -25% to +25%.
- Highlights the beat status with color-coded feedback.

## How To Use

1. Open the app.
2. Enter an initial guess for the beat period. The default is `1200 ms`.
3. Tap `Start` and grant microphone permission if the system asks for it.
4. Wait while the app searches, locks onto the beat, and updates the measurements.
5. Read the period, asymmetry in `ms`, and asymmetry in `%`.
6. Tap `Stop` when you want to stop listening.

## Preview

The current app image preview is available at [images/TickTock.jpg](images/TickTock.jpg).

## Distribution

- Primary source and releases are on GitHub.
- Release artifacts should be attached to GitHub releases, not committed into the source tree.
- F-Droid listing text metadata is available in [fastlane/metadata/android/en-US](fastlane/metadata/android/en-US).
- F-Droid submission package is available in [fdroid](fdroid).
- First F-Droid inclusion is manual, then updates can be tracked from GitHub tags.
- The Android app has no internet permission, no network code path, and no analytics or tracking SDKs.

## License

GNU GPL v3. See [COPYING](COPYING).
