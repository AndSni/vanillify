package app.vanillify.shell

import kotlin.system.exitProcess

/**
 * Shizuku "user service": Shizuku starts this class in its own process with the
 * shell uid, which has the same rights as `adb shell`. Keep it free of Android app
 * state: it has no Context and no access to Vanillify's storage.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        ShellExec.shutdown()
        exitProcess(0)
    }

    override fun protocol(): Int = ShellProtocol.VERSION

    override fun exec(command: String, timeoutMs: Long): String? = ShellExec.run(command, timeoutMs)
}
