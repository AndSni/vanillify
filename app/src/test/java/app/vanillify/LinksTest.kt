package app.vanillify

import app.vanillify.device.AppState
import app.vanillify.device.InstalledApp
import app.vanillify.device.LibraryUse
import app.vanillify.device.LinkIndex
import app.vanillify.device.LinkKind
import app.vanillify.device.SystemRole
import app.vanillify.device.SystemUse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {

    private fun app(
        pkg: String,
        requested: List<String> = emptyList(),
        defines: Map<String, Boolean> = emptyMap(),
        sharedUserId: String? = null,
    ) = InstalledApp(pkg, pkg, 10_000, true, AppState.ENABLED, false, false, requested, defines, sharedUserId)

    private val apps = listOf(
        // Check-in provider with a signature permission, used by two Moto apps.
        app("com.moto.checkin", defines = mapOf("com.moto.permission.CHECKIN" to true, "com.moto.permission.SHARE" to false)),
        app("com.moto.battery", requested = listOf("com.moto.permission.CHECKIN", "android.permission.INTERNET")),
        app("com.moto.camera", requested = listOf("com.moto.permission.CHECKIN", "com.moto.permission.SHARE")),
        app("com.other.app", requested = listOf("com.moto.permission.SHARE")),
        app("android", defines = mapOf("android.permission.INTERNET" to false)),
        app("com.google.android.gms", sharedUserId = "com.google.uid.shared"),
        app("com.google.android.gsf", sharedUserId = "com.google.uid.shared"),
        app("com.lib.provider"),
        app("com.lib.user"),
    )

    private val index = LinkIndex(
        apps,
        libraries = listOf(LibraryUse("com.lib", "com.lib.provider", listOf("com.lib.user"))),
        community = { pkg -> if (pkg == "com.moto.camera") listOf("com.lib.provider", "com.not.installed") to emptyList() else null },
        system = SystemUse(
            roles = mapOf("com.moto.camera" to setOf(SystemRole.NOTIFICATIONS)),
            overlays = mapOf("com.moto.battery.overlay" to "com.moto.battery"),
        ),
    )

    @Test fun signaturePermissionIsAStrongLink() {
        val checkin = index.of("com.moto.checkin")
        assertEquals(setOf("com.moto.battery", "com.moto.camera"), checkin.neededBy.map { it.pkg }.toSet())
        assertTrue(checkin.neededBy.all { it.kind == LinkKind.PERMISSION })
        assertEquals(listOf("com.moto.checkin"), index.of("com.moto.battery").needs.map { it.pkg })
    }

    @Test fun ordinaryPermissionIsAnOptionalFeature() {
        val checkin = index.of("com.moto.checkin")
        // com.moto.camera also holds the strong permission, so it isn't listed twice.
        assertEquals(listOf("com.other.app"), checkin.usedBy.map { it.pkg })
        assertEquals(listOf("com.moto.checkin"), index.of("com.other.app").uses.map { it.pkg })
        assertEquals(setOf("CHECKIN", "SHARE"), checkin.neededBy.first { it.pkg == "com.moto.camera" }.via)
    }

    @Test fun androidsOwnPermissionsArentLinks() {
        assertTrue(index.of("android").usedBy.isEmpty())
    }

    @Test fun librariesSharedIdsOverlaysRolesAndCommunity() {
        assertEquals(LinkKind.LIBRARY, index.of("com.lib.user").needs.single().kind)
        assertEquals(listOf("com.google.android.gsf"), index.of("com.google.android.gms").sharesIdWith)
        assertEquals(listOf("com.moto.battery.overlay"), index.of("com.moto.battery").overlays)
        assertEquals("com.moto.battery", index.of("com.moto.battery.overlay").overlayFor)
        assertNull(index.of("com.moto.battery").overlayFor)
        assertEquals(setOf(SystemRole.NOTIFICATIONS), index.of("com.moto.camera").roles)
        // Community dependencies only count when the app is on this phone.
        assertTrue(index.of("com.moto.camera").needs.any { it.pkg == "com.lib.provider" && it.kind == LinkKind.COMMUNITY })
        assertTrue(index.of("com.moto.camera").needs.none { it.pkg == "com.not.installed" })
    }
}
