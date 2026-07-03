# Changelog

All notable changes to TickTock are documented here.

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