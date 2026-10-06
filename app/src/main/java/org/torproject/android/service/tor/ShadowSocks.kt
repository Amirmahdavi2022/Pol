package org.torproject.android.service.tor

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.net.ServerSocket

/**
 * ShadowSocks client wrapper
 *
 * Following Gradle configuration is absolutely necessary, in order to have Android extract
 * the packaged libraries so we can execute the `sslocal` binary masquerading as one:
 *
 * ```
 * android {
 *     packaging {
 *         jniLibs {
 *             useLegacyPackaging = true
 *         }
 *     }
 * }
 * ```
 *
 * Unfortunately, this seems the only way. An alternative would be to package them as assets,
 * but on modern Androids, app private storage is mounted non-executable.
 *
 * This is how the demo ShadowSocks-Android application does it, too.
 *
 * See https://github.com/shadowsocks/shadowsocks-android
 *
 * The binaries are taken from there.
 *
 * We currently cannot replicate the build process, as the used Gradle plugin
 * "org.mozilla.rust-android-gradle.rust-android" is incompatible with newer Gradle versions
 * like ours.
 */
object ShadowSocks {

    const val SCHEME = "ss"

    private var process: Process? = null

    /**
     * Starts the packaged Rust ShadowSocks client as a subprocess from a file masquerading
     * as a library.
     */
    @JvmStatic
    fun start(context: Context, serverUrl: String): String? {
        if (!isShadowSocksSupported()) return null

        stop()

        val file = File(context.applicationInfo.nativeLibraryDir, "libsslocal.so")

        // Get an available port by opening and immediately closing a ServerSocket.
        val port = ServerSocket(0).use { it.localPort }
        val address = "localhost:$port"

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            Files.walk(Paths.get(file.parent))
//                .forEach { Log.d("ShadowSocks", it.toString()) }
//        }

        val cmd = listOf(
            file.absolutePath,
            "-b", address,
            "--server-url", serverUrl
        )

        Log.d("ShadowSocks", cmd.joinToString(" "))

        process = ProcessBuilder(cmd)
            .directory(context.noBackupFilesDir)
            .redirectErrorStream(true)
            .start()

        return address
    }

    @JvmStatic
    fun stop() {
        if (process != null) {
            Log.d("ShadowSocks", "Stop running ShadowSocks client.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process?.destroyForcibly()
            } else {
                process?.destroy()
            }

            process = null
        }
    }

    @JvmStatic
    fun isShadowSocksSupported(): Boolean {
        return Build.SUPPORTED_ABIS.firstOrNull()?.equals("x86") != true
    }
}
