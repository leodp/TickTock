# F-Droid Release Notes

This repository includes draft F-Droid metadata at [metadata/com.ticktock.app.yml](metadata/com.ticktock.app.yml).

## What Is Prepared Here

- App metadata file with package id, source URL, license, and build recipe.
- Changelog reference to [CHANGELOG.md](CHANGELOG.md).
- Tagged release flow for `v1.0.0`.

## Final Publication Steps

1. Fork `fdroiddata`.
2. Copy [metadata/com.ticktock.app.yml](metadata/com.ticktock.app.yml) into the fork's `metadata/` directory.
3. Run `fdroid checkupdates` and `fdroid rewritemeta` in `fdroiddata`.
4. Open a merge request to `f-droid/fdroiddata`.

F-Droid publication is completed after the fdroiddata merge and build pipeline acceptance.
