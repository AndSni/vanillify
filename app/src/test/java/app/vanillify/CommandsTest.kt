package app.vanillify

import app.vanillify.device.Commands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Outputs below were captured from a Motorola Edge 60 Pro on Android 16. */
class CommandsTest {

    @Test fun quotesForSh() {
        assertEquals("'com.example'", Commands.quote("com.example"))
        assertEquals("''", Commands.quote(""))
        assertEquals("'it'\\''s'", Commands.quote("it's"))
    }

    @Test fun packageResults() {
        assertTrue(Commands.uninstalled("Success\n"))
        assertFalse(Commands.uninstalled("Failure [DELETE_FAILED_INTERNAL_ERROR]\n"))
        assertTrue(Commands.disabled("Package com.motorola.moto new state: disabled-user\n"))
        assertTrue(Commands.enabled("Package com.motorola.moto new state: enabled\n"))
        assertTrue(Commands.reinstalled("Package com.facebook.system installed for user: 0\n"))
        assertFalse(Commands.reinstalled(null))
    }

    @Test fun settingValues() {
        assertNull(Commands.settingValue("null\n"))
        assertEquals("1", Commands.settingValue("1\n"))
        assertEquals("", Commands.settingValue("\n"))
    }

    @Test fun microphoneModes() {
        assertEquals("foreground", Commands.uidMode("Uid mode: RECORD_AUDIO: foreground\n", "RECORD_AUDIO"))
        assertEquals("ignore", Commands.uidMode("Uid mode: RECORD_AUDIO: ignore\nRECORD_AUDIO: allow; time=+1h ago\n", "RECORD_AUDIO"))
        assertEquals("default", Commands.uidMode("No operations.\nDefault mode: allow\n", "RECORD_AUDIO"))
        assertNull(Commands.uidMode("Error: No UID for com.nonexistent.pkg in user 0\n", "RECORD_AUDIO"))
    }

    @Test fun firewall() {
        assertEquals(false, Commands.chainState("chain:disabled\n"))
        assertEquals(true, Commands.chainState("chain:enabled"))
        assertNull(Commands.chainState("Unknown command: get-chain3-enabled"))
        assertEquals(true, Commands.netAllowed("com.motorola.batterycare:allow\n"))
        assertEquals(false, Commands.netAllowed("com.motorola.batterycare:deny"))
    }

    @Test fun briefErrors() {
        assertEquals("Failure: package is non-disable", Commands.brief("Failure: package is non-disable\n"))
        val trace = """
            Exception occurred while executing 'disable-user':
            java.lang.SecurityException: Cannot disable a protected package: com.motorola.paks
                    at com.android.server.pm.PackageManagerService.setEnabledSettings(PackageManagerService.java:4006)
        """.trimIndent()
        assertEquals("SecurityException: Cannot disable a protected package: com.motorola.paks", Commands.brief(trace))
        assertEquals("no answer from the shell", Commands.brief(null))
    }
}
