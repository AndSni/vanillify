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
- **Privacy settings.** Background Wi-Fi and Bluetooth scanning, uploads of your apps and
  Wi-Fi scans to Google, crash reports, and encrypted Private DNS that blocks ads and trackers.
- **Microphone blocks** for the apps that run all the time and so aren't covered by Android's
  "only while using the app".
- **Per-app firewall** (Android 14+) using Android's own firewall: no VPN slot taken.
- **Kept that way.** Android forgets microphone and network blocks at every restart; Vanillify
  puts them back as soon as Shizuku runs again, and reminds you if it isn't.
- **Every change can be undone** from History.
- **One tap.** "Vanillify" lists every recommended change with a checkbox; nothing happens until
  you confirm.

Vanillify has **no internet permission**. It can't send anything anywhere.

## Building

```sh
export JAVA_HOME=/path/to/jdk-21
./gradlew testDebugUnitTest assembleDebug
```

`tools/update_uad_list.py` refreshes the bundled community list.

## License

GPL-3.0-or-later. The bundled package list (`app/src/main/assets/uad_lists.json`) is from
Universal Android Debloater Next Generation, also GPL-3.0.
