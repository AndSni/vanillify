<img src="metadata/en-US/images/icon.png" width="96" alt="Vanillify icon">

# Vanillify: Debloat & Privacy

Make your Android phone vanilla again, without root and without a computer.

Vanillify removes the apps your phone came with that you never asked for, stops background
data sharing and always-on listening, and keeps it that way. It works on any Android phone
through [Shizuku](https://shizuku.rikka.app/), which gives it the same rights as `adb shell`.

- **Remove preinstalled apps.** Every system app with its name, icon and a safety rating from
  the [Universal Android Debloater](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation)
  community list (about 5,400 packages from all major manufacturers and carriers), plus
  Vanillify's own findings verified on real phones. Apps are removed for your user only, so
  nothing on the system partition changes and everything can come back.
- **See what depends on what.** Before you remove an app: which apps depend on it, what it
  depends on, and whether the system uses it as your home screen, keyboard, phone, SMS or
  browser app. Vanillify warns you before you remove something other apps need.
- **Privacy settings.** Background Wi-Fi and Bluetooth scanning, uploads of your apps and
  Wi-Fi scans to Google, crash reports, and encrypted Private DNS that blocks ads and trackers.
- **Microphone blocks** for the apps that run all the time and so aren't covered by Android's
  "only while using the app".
- **Per-app firewall** (Android 14+) using Android's own firewall: no VPN slot taken.
- **Kept that way.** Android forgets microphone and network blocks at every restart; Vanillify
  puts them back as soon as Shizuku runs again, and reminds you if it isn't.
- **Every change can be undone** from History, one at a time or several together.
- **One tap.** "Vanillify" lists every recommended change with a checkbox; nothing happens until
  you confirm.

Vanillify has **no internet permission**. It can't send anything anywhere.

## Install

- **GitHub:** [download the latest release](https://github.com/AndSni/vanillify/releases/latest/download/Vanillify.apk).
- **F-Droid:** submitted, coming soon.

Then install [Shizuku](https://shizuku.rikka.app/), start it (wireless debugging, no computer
needed), open Vanillify and tap **Allow** when Shizuku asks. Without Shizuku you can still browse
every app and its rating; changes need it.

Releases are signed with certificate SHA-256
`5e991d051c9ad7047b0aaec601bf08a93a887ea9486cf08fdd27e57dfea0502a`.

## Support Vanillify

Vanillify is free, open source and has no ads, and it stays that way. If it made your phone
better, you can help:

- ☕ **[Buy me a coffee on Ko-fi](https://ko-fi.com/asnidev).** No account needed: pay with
  Google Pay, Apple Pay, a card or PayPal.
- ⭐ Star this repository and tell others about it.
- Report apps that Vanillify rates wrongly, or ones it doesn't know yet, in
  [Issues](https://github.com/AndSni/vanillify/issues). Findings from real phones are what
  make the app lists better.

## Building

```sh
export JAVA_HOME=/path/to/jdk-17-or-21
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

- `tools/update_uad_list.py` refreshes the bundled community list.
- `tools/build_icons.py` regenerates every icon and store image from the two Inkscape drawings in
  `design/` (needs `skia-pathops` and `svgelements` from pip, Inkscape and the Noto Sans font).

## Releasing a version

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Add `metadata/en-US/changelogs/<versionCode>.txt`: one plain paragraph.
3. Run `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`.
4. Commit and push to `main` (CI publishes the rolling `latest` build).
5. Put the full hash of that commit (`git rev-parse HEAD`, never a tag) in `.fdroid.yml`'s
   `commit:`, update `versionName`/`versionCode`/`CurrentVersion`/`CurrentVersionCode`, commit,
   push.
6. Tag the release commit from step 4 and push the tag: `git tag vX.Y.Z <hash> && git push origin vX.Y.Z`.
   `release-tag.yml` publishes the permanent release F-Droid compares its own build against.
7. Download `https://github.com/AndSni/vanillify/releases/download/vX.Y.Z/Vanillify.apk` and check
   `apksigner verify --print-certs` shows the certificate above.

## License

GPL-3.0-only. The bundled package list (`app/src/main/assets/uad_lists.json`) is from
Universal Android Debloater Next Generation, also GPL-3.0.
