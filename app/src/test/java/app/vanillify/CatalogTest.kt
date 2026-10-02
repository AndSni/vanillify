package app.vanillify

import app.vanillify.catalog.Catalog
import app.vanillify.catalog.Finding
import app.vanillify.catalog.Source
import app.vanillify.catalog.Tier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs against the real bundled lists, so a broken overlay entry fails the build. */
class CatalogTest {

    private val assets = File("src/main/assets")
    private val merged = Catalog.merge(
        JSONObject(File(assets, "uad_lists.json").readText()),
        JSONObject(File(assets, "vanillify_overlay.json").readText()),
    )

    @Test fun communityListLoads() {
        assertTrue("expected thousands of entries, got ${merged.size}", merged.size > 5_000)
        assertEquals(Source.GOOGLE, merged.getValue("com.google.android.gms").source)
    }

    @Test fun overlayCorrectsCommunityList() {
        // UAD-ng calls this an app bundle; on current Motorola phones it is a financing lock.
        val paks = merged.getValue("com.motorola.paks")
        assertEquals(Tier.EXPERT, paks.tier)
        assertTrue(Finding.FINANCING_LOCK in paks.findings)
        assertTrue(paks.description.startsWith("PAKSFinance"))
    }

    @Test fun overlayKeepsCommunityFieldsItDoesNotSet() {
        val battery = merged.getValue("com.motorola.batterycare")
        assertEquals(Source.OEM, battery.source) // from UAD-ng
        assertTrue(Finding.PHONES_HOME in battery.findings) // from the overlay
        assertTrue(battery.note!!.contains("Firebase"))
    }

    @Test fun everyOverlayFindingIsKnown() {
        val overlay = JSONObject(File(assets, "vanillify_overlay.json").readText())
        for (pkg in overlay.keys()) {
            if (pkg.startsWith("_")) continue
            val findings = overlay.getJSONObject(pkg).optJSONArray("findings") ?: continue
            for (i in 0 until findings.length()) {
                val name = findings.getString(i)
                assertTrue("$pkg: unknown finding '$name'", Finding.parse(name) != null)
            }
        }
    }
}
