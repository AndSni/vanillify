package app.vanillify.catalog

/** One system setting with a private value. "On" in the UI means the private value is set. */
data class Tweak(
    val id: String,
    val title: String,
    val summary: String,
    /** What you give up; null when nothing noticeable. */
    val tradeoff: String?,
    val namespace: String,
    val key: String,
    val privateValue: String,
    val recommended: Boolean = true,
)

data class DnsProvider(val id: String, val name: String, val hostname: String?, val summary: String)

/** Something that keeps an always-on microphone, suggested for blocking. */
data class MicTarget(val pkg: String, val why: String, val tradeoff: String, val recommended: Boolean)

object Privacy {

    val tweaks = listOf(
        Tweak(
            "wifi_scan", "Stop Wi-Fi scanning when Wi-Fi is off",
            "Phones keep scanning nearby Wi-Fi networks even with Wi-Fi switched off, and the results are used for location tracking.",
            "Location can be slightly slower to find indoors.",
            "global", "wifi_scan_always_enabled", "0",
        ),
        Tweak(
            "ble_scan", "Stop Bluetooth scanning when Bluetooth is off",
            "Same as Wi-Fi scanning, but with Bluetooth beacons.",
            "Location can be slightly slower to find indoors.",
            "global", "ble_scan_always_enabled", "0",
        ),
        Tweak(
            "wifi_wakeup", "Don't turn Wi-Fi on automatically",
            "Turning Wi-Fi back on near saved networks relies on the background scanning above.",
            "Turn Wi-Fi on yourself when you get home.",
            "global", "wifi_wakeup_enabled", "0",
        ),
        Tweak(
            "net_reco", "Don't send Wi-Fi scans to Google",
            "Network recommendations send the networks around you to Google to rate them.",
            null,
            "global", "network_recommendations_enabled", "0",
        ),
        Tweak(
            "upload_apk", "Don't upload unknown apps to Google",
            "Play Protect uploads apps it doesn't recognise, such as ones from F-Droid, to Google. Scanning stays on.",
            null,
            "global", "upload_apk_enable", "0",
        ),
        Tweak(
            "verifier_consent", "Turn off \"Improve harmful app detection\"",
            "Stops sending information about your apps to Google. Play Protect scanning stays on.",
            null,
            "global", "package_verifier_user_consent", "-1",
        ),
        Tweak(
            "crash_global", "Don't offer to send crash reports to Google",
            "Turns off the \"send report\" step after an app crashes.",
            null,
            "global", "send_action_app_error", "0",
        ),
        Tweak(
            "crash_secure", "Don't send crash reports (per-user setting)",
            "The same switch, stored a second time per user on many phones.",
            null,
            "secure", "send_action_app_error", "0",
        ),
    )

    val dnsProviders = listOf(
        DnsProvider("off", "Off", null, "Your carrier's or Wi-Fi network's DNS, unencrypted."),
        DnsProvider("mullvad", "Mullvad", "base.dns.mullvad.net", "Encrypted, no logging. Blocks ads, trackers and malware in every app."),
        DnsProvider("adguard", "AdGuard", "dns.adguard-dns.com", "Encrypted. Blocks ads and trackers in every app."),
        DnsProvider("quad9", "Quad9", "dns.quad9.net", "Encrypted, non-profit. Blocks malware, not ads."),
    )

    val micTargets = listOf(
        MicTarget(
            "com.google.android.googlequicksearchbox",
            "Listens for \"Hey Google\" in the background.",
            "Voice search and talking to Assistant or Gemini stop working. Typing still works.",
            recommended = false,
        ),
        MicTarget(
            "com.google.android.as",
            "Android System Intelligence runs all the time with microphone access for ambient audio features.",
            "Features that react to sounds around you stop.",
            recommended = true,
        ),
        MicTarget(
            "com.google.android.gms",
            "Play Services runs all the time and holds microphone access for Voice Match and nearby audio tokens.",
            "Voice Match for Assistant stops working.",
            recommended = true,
        ),
    )

    /** The app-op behind the microphone permission. */
    const val MIC_OP = "RECORD_AUDIO"
}
