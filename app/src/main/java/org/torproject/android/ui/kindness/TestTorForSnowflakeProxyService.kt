package org.torproject.android.ui.kindness

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import org.torproject.android.util.DiskUtils
import org.torproject.jni.TorService

class TestTorForSnowflakeProxyService : TorService() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
    }

    companion object {
        const val TAG = "SnowflakeTestTorService"

        fun launchTorTestingService(
            orbotActivity: Activity,
            torStatusReceiver: BroadcastReceiver,
        ): ServiceConnection {
            Log.d(TAG, "Preparing to launch tor testing service...")

            // 1. Write a barebones torrc to disk
            writeMinimalTorrcToDisk(orbotActivity)

            // 2. Subscribe to status events
            ContextCompat.registerReceiver(
                orbotActivity,
                torStatusReceiver,
                IntentFilter(ACTION_STATUS),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            val serviceConnection = getServiceConnection()

            // 3. Bind the Service, starting tor...
            orbotActivity.bindService(
                Intent(orbotActivity, TestTorForSnowflakeProxyService::class.java),
                serviceConnection,
                BIND_AUTO_CREATE
            )

            return serviceConnection
        }


        private fun writeMinimalTorrcToDisk(orbotActivity: Activity) {
            // write the bare minimum torrc needed to directly connect to the tor network
            val minimalTorrc = listOf(
                "RunAsDaemon 1",
                "AvoidDiskWrites 1"
            ).joinToString("\n")
            val torrcFile = getTorrc(orbotActivity)
            DiskUtils.flushTextToFile(torrcFile, minimalTorrc, append = false)
        }

        // Activities are connected to Services via these objects, we bind and unbind to this
        // Service via a reference to the object returned by this method...
        private fun getServiceConnection(): ServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binding: IBinder?) {
                Log.d(TAG, "ServiceConnection: onServiceConnected")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "ServiceConnection: onServiceDisconnected")
            }
        }
    }
}