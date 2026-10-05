# Vanillify — project instructions

Vanillify is an Android debloat and privacy tool that works through Shizuku (adb-level rights, no
root). Kotlin + Compose, stock Android look (Material You, system font), GPL-3.0-only, **no
INTERNET permission**. App id `com.vanillify.app` (registered with Google's developer
verification, cert below); code package `app.vanillify`.

## How the user works with you

- Work autonomously; ask only for decisions that are theirs (publishing, names, design).
- **Don't drive the UI with `adb shell input tap`.** The user tests the UI by hand. To see a state,
  launch a debug build with the debug-only extras in `MainActivity` and use `adb exec-out screencap -p`:
  `--es tab home|apps|privacy|history`, `--ez review true`, `--es sheet <package>`.
- Warn before starting or driving an emulator; it's shared with other projects.
- Verify, don't assume: check builds, URLs and signatures by actually fetching and comparing.
- Public repo: commit as `AndSni <snukzz@gmail.com>` (set in this repo's local git config).

## Environment

- `export JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2` before `./gradlew`
  (the system Java 25 has no javac). AGP 8.5.2, Kotlin 2.0.21, Compose BOM 2024.12.01, Gradle 9.3.0.
- Checks before every commit: `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`.
- The user's phone: Motorola Edge 60 Pro, Android 16, Shizuku installed (its server stops at
  every reboot). The user's real Vanillify history lives on it: never uninstall the app there.
  Install over it with a build signed by the release key (`adb install -r`).

## Design and icons

- The icon is the user's own drawing: `design/vanillify-icon-final.svg` (colour) and
  `design/vanillify-icon-final_singlecolor.svg` (themed / notification). Palette "Plum & orange":
  `#6A3FA0 → #2A1650`, accent `#FF9F43`, camera dot `#5E3792`, white phone.
- `tools/build_icons.py` regenerates every drawable, `design/icon/*.svg` and the store images,
  with optical centring. Never hand-edit the generated drawables.
- Avoid: four-point "AI" sparkles, blue + yellow (EU / Walmart), red five-point stars.

## Releases and F-Droid (proven on SysReadout Launcher / Monitor and Trailkeeper Offgrid)

- APKs are never committed. `release.yml` publishes a rolling pre-release `latest`;
  `release-tag.yml` publishes a permanent release per `vX.Y.Z` tag with the asset named exactly
  `Vanillify.apk` (`gh release create file#label` only sets a label: copy to the final name).
- Signing: `vanillify-release.jks` + `keystore.properties` in the repo root (gitignored, PKCS12,
  store password = key password). Cert SHA-256
  `5e991d051c9ad7047b0aaec601bf08a93a887ea9486cf08fdd27e57dfea0502a`. Also in the repo's Actions
  secrets (RELEASE_KEYSTORE_B64 / _PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD).
- `.fdroid.yml`: `subdir: app`, `commit:` the **full hash** of the release commit, only the
  current version under `Builds`, `UpdateCheckMode: Tags ^v[0-9.]+$`. Keep the fdroiddata copy
  (`metadata/com.vanillify.app.yml` in the snukzz/fdroiddata fork) in sync by hand until merged.
- Before an fdroiddata MR: read its `.gitlab/merge_request_templates/App inclusion.md` and fill it
  exactly; run `fdroid readmeta`, `rewritemeta`, `checkupdates`, `lint` in a scratch copy.
  fdroiddata's CI formatting: `AutoName:` after the Changelog block, no blank line between `Repo:`
  and `Binaries:`. Whether `Changelog`/`Binaries` URLs wrap onto a second line depends on their
  length: Vanillify's fit on one line (CI's rewritemeta said so in round 1), SysReadout's longer ones
  had to wrap. Trust CI's rewritemeta diff over any rule. Fork CI shows 0 jobs: ask maintainers in a
  note to trigger the pipeline.
- Release builds: R8 on with `-dontobfuscate` (keep rule for `ShellService`, which Shizuku calls by
  reflection), `vcsInfo.include = false`, `dependenciesInfo` off, EmojiCompat initializer removed
  (no network at first start). Unsigned when there's no `keystore.properties` (F-Droid's build).
- Reproducibility: compare a clean build (`./gradlew clean assembleRelease`) with CI's; check with
  `fdroidserver.apksigcopier.do_copy(signed, unsigned, out)` and compare hashes.
- Store screenshots come from the commit F-Droid builds: new images need a new version.
