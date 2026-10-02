package app.vanillify.catalog

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** How safe removal is, as rated by the community list (UAD-ng) or Vanillify's own checks. */
enum class Tier(val label: String) {
    RECOMMENDED("Safe to remove"),
    ADVANCED("Advanced"),
    EXPERT("Expert"),
    UNSAFE("Keep"),
    UNKNOWN("Not rated");

    companion object {
        fun parse(s: String?) = when (s?.lowercase()) {
            "recommended" -> RECOMMENDED
            "advanced" -> ADVANCED
            "expert" -> EXPERT
            "unsafe" -> UNSAFE
            else -> UNKNOWN
        }
    }
}

/** Who put the app on the phone. */
enum class Source(val label: String) {
    OEM("Manufacturer"),
    CARRIER("Carrier"),
    GOOGLE("Google"),
    AOSP("Android"),
    MISC("Third party"),
    UNKNOWN("Unknown");

    companion object {
        fun parse(s: String?) = when (s?.lowercase()) {
            "oem" -> OEM
            "carrier" -> CARRIER
            "google" -> GOOGLE
            "aosp" -> AOSP
            "misc" -> MISC
            else -> UNKNOWN
        }
    }
}

/** What Vanillify itself found an app doing. */
enum class Finding(val label: String) {
    ADWARE("Ads or silent app installs"),
    TELEMETRY("Usage data uploads"),
    PHONES_HOME("Connects home in the background"),
    LISTENING("Always-on microphone access"),
    FINANCING_LOCK("Device-financing lock"),
    LOCKED("The manufacturer blocks removing it");

    companion object {
        fun parse(s: String) = entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
    }
}

data class CatalogEntry(
    val pkg: String,
    val tier: Tier,
    val source: Source,
    val description: String,
    /** Removing these too is needed for this one to work fully, per the community list. */
    val dependencies: List<String>,
    /** These break if this one goes. */
    val neededBy: List<String>,
    val findings: Set<Finding>,
    /** Vanillify's own verified note, shown above the community description. */
    val note: String?,
) {
    val fromVanillify get() = note != null || findings.isNotEmpty()
}

/**
 * The community package list from Universal Android Debloater Next Generation
 * (GPL-3.0, ~5,400 packages across manufacturers and carriers), with Vanillify's
 * overlay on top: corrections and findings verified on real phones.
 */
class Catalog private constructor(private val entries: Map<String, CatalogEntry>) {

    operator fun get(pkg: String): CatalogEntry? = entries[pkg]

    val size get() = entries.size

    companion object {
        fun load(context: Context): Catalog {
            val uad = JSONObject(context.assets.open("uad_lists.json").bufferedReader().use { it.readText() })
            val overlay = JSONObject(context.assets.open("vanillify_overlay.json").bufferedReader().use { it.readText() })
            return Catalog(merge(uad, overlay))
        }

        internal fun merge(uad: JSONObject, overlay: JSONObject): Map<String, CatalogEntry> {
            val out = HashMap<String, CatalogEntry>(uad.length() + overlay.length())
            for (pkg in uad.keys()) {
                val o = uad.getJSONObject(pkg)
                out[pkg] = CatalogEntry(
                    pkg = pkg,
                    tier = Tier.parse(o.optString("removal")),
                    source = Source.parse(o.optString("list")),
                    description = o.optString("description").trim(),
                    dependencies = o.optJSONArray("dependencies").strings(),
                    neededBy = o.optJSONArray("neededBy").strings(),
                    findings = emptySet(),
                    note = null,
                )
            }
            for (pkg in overlay.keys()) {
                if (pkg.startsWith("_")) continue // "_comment" and friends
                val o = overlay.getJSONObject(pkg)
                val base = out[pkg]
                out[pkg] = CatalogEntry(
                    pkg = pkg,
                    tier = if (o.has("removal")) Tier.parse(o.getString("removal")) else base?.tier ?: Tier.UNKNOWN,
                    source = if (o.has("list")) Source.parse(o.getString("list")) else base?.source ?: Source.UNKNOWN,
                    description = if (o.has("description")) o.getString("description") else base?.description.orEmpty(),
                    dependencies = base?.dependencies.orEmpty(),
                    neededBy = base?.neededBy.orEmpty(),
                    findings = o.optJSONArray("findings").strings().mapNotNull(Finding::parse).toSet(),
                    note = o.optString("note").takeIf { it.isNotBlank() },
                )
            }
            return out
        }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { getString(it) }
    }
}
