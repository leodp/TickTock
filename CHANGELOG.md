# Changelog

All notable changes to TickTock are documented here.

## 1.0.7 - 2026-10-02

### Changed

- Increased audio capture precision by preferring 192 kHz mono input, with runtime fallbacks for lower supported rates.
- Improved beat timing by confirming envelope peaks and ignoring the initial transient peak.
- Added gradual post-lock gain for quiet signals.

## 1.0.6 - 2026-09-30

### Changed

- Enabled upside-down portrait orientation so the app rendering can rotate with the device while positioning the microphone.
- Replaced the generated launcher icon with the supplied `Orologio.jpg` image and a white adaptive-icon background.

## 1.0.5 - 2026-09-29

### Changed

- Stabilized beat detection after the measured rhythm becomes consistent, while continuing to track and calculate asymmetry.
- Restored the standard Android Gradle release output at `app/build/outputs/apk/release/app-release.apk`.
- Updated the F-Droid metadata for version code 6 and tag `v1.0.5`.

## 1.0.4 - 2026-09-15

### Added

- Added live mean-period updates after each detected beat while tracking.
- Added the observed standard deviation alongside the mean period, with one decimal place, during tracking and after stopping.
- Added live filtering that removes the initial 10% of beat intervals and rejects values outside the median +/- five standard deviations. The final stopped calculation also removes the last 10%.

## 1.0.3 - 2026-09-14

### Added

- Added automatic beat logging with millisecond precision and first-beat zero offset.
- Added a stop-time filtered average tick duration to the tracking screen.
- Added automatic saving of beat logs to the user's Documents folder on Stop.
- Added a three-second save confirmation popup.

### Changed

- Beat log filenames now include minutes and seconds to distinguish recordings made within the same hour.

## 1.0.2 - 2026-07-03

### Changed

- Added the official Gradle distribution SHA-256 checksum to the wrapper configuration to satisfy F-Droid's Gradle wrapper integrity check.

## 1.0.1 - 2026-07-03

### Changed

- Removed the unused Google Material dependency and switched the app theme to AppCompat.
- Confirmed the app has no internet permission, no network code path, and no analytics or tracking SDKs.
- Updated the release build to produce an installable signed APK named `TickTock.apk` for both the release output and the repository root copy.
- Cleaned the F-Droid metadata and repository packaging flow so the project no longer relies on a tracked APK in source control.

## 1.0.0 - 2026-07-02

### Added

- Real-time pendulum beat analysis from the phone microphone.
- Gauge visualization for asymmetry from -25% to +25%.
- Period, asymmetry in milliseconds, and asymmetry percentage readouts.
- Start/Stop control for audio acquisition.

### Changed

- Refined the layout to improve spacing around the title, input area, gauge, and metric boxes.
- Enlarged key UI text and controls for better readability.
- Added a preview image at [images/TickTock.jpg](images/TickTock.jpg).
- Removed the repository-root APK packaging flow and the unused Google Material dependency to keep the source tree F-Droid-friendly.

### Notes

- The project is released under the GNU GPL v3.