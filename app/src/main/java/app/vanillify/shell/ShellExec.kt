package app.vanillify.shell

import java.io.IOException
import java.io.Reader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/** Bumped whenever [IShellService]'s behaviour changes, so an older running helper gets replaced. */
object ShellProtocol {
    const val VERSION = 1
}

/**
 * What the Shizuku helper does, free of Android classes so it can be unit-tested.
 * Every call is bounded in time and a failure is null, never a plausible-looking empty answer.
 */
object ShellExec {

    /** Longest output passed back; binder replies over ~1 MB fail outright. */
    const val MAX_OUTPUT = 512 * 1024

    private val readers: ExecutorService = Executors.newCachedThreadPool(
        ThreadFactory { r -> Thread(r, "shell-read").apply { isDaemon = true } },
    )

    /** stdout+stderr of `sh -c command`, or null when it can't start or runs past [timeoutMs]. */
    fun run(command: String, timeoutMs: Long): String? {
        val process = try {
            ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        } catch (e: IOException) {
            return null
        }
        val output = readers.submit<String> { process.inputStream.bufferedReader().use { readCapped(it, MAX_OUTPUT) } }
        return try {
            output.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) { // timeout, interrupted, read failure
            null
        } finally {
            process.destroyForcibly()
            output.cancel(true)
        }
    }

    fun shutdown() {
        readers.shutdownNow()
    }

    /** Keeps the first [limit] chars and drains the rest so the process can finish. */
    internal fun readCapped(reader: Reader, limit: Int): String {
        val out = StringBuilder()
        val buf = CharArray(8192)
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            val room = limit - out.length
            if (room > 0) out.appendRange(buf, 0, minOf(n, room))
        }
        return out.toString()
    }
}
