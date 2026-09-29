# TickTock

TickTock is an Android app for setting the beat of a pendulum clock. It listens to the clock through the phone microphone, detects the alternating left and right beats, and shows whether the clock is running evenly.

## What It Does

- Listens to live audio from the phone microphone.
- Detects the pendulum beat pattern in real time.
- Estimates the beat period, asymmetry in milliseconds, and asymmetry percentage.
- Displays the asymmetry on a gauge from -25% to +25%.
- Highlights the beat status with color-coded feedback.
- Logs every detected beat with millisecond precision relative to the first beat.
- Shows the filtered mean period and observed standard deviation while tracking and after stopping, calculated as twice the average beat interval.
- Saves the beat log automatically in the user's `Documents` folder when stopped.

## How To Use

1. Open the app.
2. Enter an initial guess for the beat period. The default is `1200 ms`.
3. Tap `Start` and grant microphone permission if the system asks for it.
4. Wait while the app searches, locks onto the beat, and updates the measurements.
5. Read the period, asymmetry in `ms`, and asymmetry in `%`.
6. While tracking, read the live `Mean period [ms]` value and its observed standard deviation after each detected beat.
7. Tap `Stop` when you want to stop listening and save the beat log.
8. Read the final filtered mean period and observed standard deviation in the `Mean period [ms]` box.
9. Find the log in `Documents` with a name such as `ticktockBeats_2026-09-14_15-42-07.txt`.

Each line in the text file contains one beat time in seconds, formatted to three decimal places. The first detected beat is always `0.000`.
During tracking, the displayed mean period is twice the average of adjacent beat intervals after removing the first 10% and filtering values outside the median +/- five standard deviations. At stop, the last 10% is removed as well. The value after `+/-` is the observed standard deviation of the retained period intervals.

## Preview

The current app image preview is available at [images/TickTock.jpg](images/TickTock.jpg).

## Distribution

- Primary source and releases are on GitHub.
- The standard release APK is created at `app/build/outputs/apk/release/app-release.apk`.
- Release artifacts should be attached to GitHub releases, not committed into the source tree.
- F-Droid listing text metadata is available in [fastlane/metadata/android/en-US](fastlane/metadata/android/en-US).
- F-Droid submission package is available in [fdroid](fdroid).
- F-Droid builds use the standard Gradle release output and can track updates from GitHub tags.
- The Android app has no internet permission, no network code path, and no analytics or tracking SDKs.

## License

GNU GPL v3. See [COPYING](COPYING).
