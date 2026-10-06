package org.torproject.android.service.circumvention

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.torproject.android.util.Prefs
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

object SmartConnect {

    private val ioScope: CoroutineScope by lazy {
        CoroutineScope(Dispatchers.IO)
    }

    private val mainScope: CoroutineScope by lazy {
        CoroutineScope(Dispatchers.Main)
    }

    private var progress = 0
    private var connectionTimeout = TimeSource.Monotonic.markNow()
    private var connectionGuard: Timer? = null

    /**
     * Pol: transports already attempted during the current automatic run.
     */
    private val tried = mutableSetOf<Transport>()

    /**
     * Pol: order in which automatic mode walks through the transports after the first one
     * (MOAT's pick or the last transport that worked) didn't reach 100%.
     */
    private val chain = listOf(
        Transport.SNOWFLAKE,
        Transport.CUSTOM,
        Transport.WEBTUNNEL,
        Transport.OBFS4,
        Transport.MEEK,
    )

    private fun normalize(transport: Transport): Transport = when (transport) {
        Transport.SNOWFLAKE_AMP, Transport.SNOWFLAKE_SQS -> Transport.SNOWFLAKE
        else -> transport
    }

    private fun isUsable(context: Context, transport: Transport): Boolean = when (transport) {
        Transport.CUSTOM -> Prefs.bridgesList.isNotEmpty()
        Transport.WEBTUNNEL -> !BuiltInBridges.getInstance(context)?.webtunnel.isNullOrEmpty()
        else -> true
    }

    private fun nextTransport(context: Context): Transport? =
        chain.firstOrNull { it !in tried && isUsable(context, it) }

    @JvmStatic
    fun handle(context: Context, startTor: () -> Exception?, reconfigure: () -> Boolean, stopTor: (e: Exception?) -> Unit, completed: () -> Unit) {
        progress = 0
        tried.clear()

        if (!Prefs.smartConnect) {
            val exception = startTor()
            return if (exception != null) stopTor(exception) else completed()
        }

        ioScope.launch {
            val lastGood = Prefs.lastGoodTransport

            if (lastGood != null && lastGood != Transport.NONE && isUsable(context, lastGood)) {
                // Pol: something worked last time, start with it and skip the MOAT lookup.
                Prefs.transport = lastGood
            } else {
                var conf: Pair<Transport, List<String>>? = null

                try {
                    conf = AutoConf.`do`(context)
                }
                catch (_: Throwable) {}

                Prefs.transport = conf?.first ?: Transport.NONE

                conf?.second?.let {
                    if (it.isNotEmpty()) {
                        Prefs.bridgesList = it
                    }
                }
            }

            tried.add(normalize(Prefs.transport))

            try {
                Prefs.transport.start(context)
            }
            catch (_: Exception) {
                // The guard below moves on to the next transport when this one gets nowhere.
            }

            val exception = startTor()

            if (exception != null) {
                mainScope.launch {
                    stopTor(exception)
                }

                return@launch
            }

            // Assume everything's fine for the next 30 seconds.
            connectionAlive()

            connectionGuard = Timer()
            connectionGuard?.schedule(object : TimerTask() {
                override fun run() {
                    // We have a working connection: remember what did it for next time.
                    // Pol keeps automatic mode on, so a transport that later gets blocked
                    // is simply skipped on the next connect.
                    if (progress >= 100) {
                        stopConnectionGuard()
                        Prefs.lastGoodTransport = Prefs.transport

                        mainScope.launch {
                            completed()
                        }

                        return
                    }

                    if (TimeSource.Monotonic.markNow() < connectionTimeout) return

                    connectionAlive()

                    Prefs.transport.stop()

                    while (true) {
                        val next = nextTransport(context)

                        if (next == null) {
                            stopConnectionGuard()

                            // Nothing worked: forget the last good transport, so the next
                            // attempt asks MOAT for fresh settings and bridges.
                            Prefs.lastGoodTransport = null

                            mainScope.launch {
                                stopTor(Exception("Smart Connect failed"))
                            }

                            return
                        }

                        tried.add(next)
                        Prefs.transport = next

                        try {
                            Prefs.transport.start(context)
                            break
                        }
                        catch (_: Exception) {}
                    }

                    if (!reconfigure()) {
                        // That didn't start? Ok, then try next transport immediately.
                        connectionTimeout = TimeSource.Monotonic.markNow()
                    }
                }
            }, 1000, 1000)
        }
    }

    @JvmStatic
    fun updateProgress(progress: Int) {
        if (!Prefs.smartConnect) return

        if (progress > this.progress) {
            connectionAlive()
            this.progress = progress
        }
    }

    @JvmStatic
    fun cancel() {
        stopConnectionGuard()
    }

    private fun connectionAlive() {
        connectionTimeout = TimeSource.Monotonic.markNow() + Prefs.smartConnectTimeout.seconds
    }

    private fun stopConnectionGuard() {
        connectionGuard?.cancel()
        connectionGuard?.purge()
        connectionGuard = null
    }
}