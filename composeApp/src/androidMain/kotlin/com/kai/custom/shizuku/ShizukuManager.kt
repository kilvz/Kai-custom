package com.kai.custom.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ShizukuManager {
    private const val MAX_OUTPUT_LENGTH = 15_000
    private const val SERVICE_TIMEOUT_SECONDS = 15L

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val permissionListeners = mutableListOf<(Boolean) -> Unit>()

    private var serviceBinder: IBinder? = null
    private var serviceConnection: ServiceConnection? = null
    private var serviceArgs: Shizuku.UserServiceArgs? = null

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        synchronized(permissionListeners) {
            val granted = grantResult == 0
            permissionListeners.toList().forEach { it(granted) }
        }
    }

    private val binderDeathRecipient = IBinder.DeathRecipient {
        serviceBinder = null
    }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {
        }
    }

    val isAvailable: Boolean
        get() = try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }

    val hasPermission: Boolean
        get() = try {
            Shizuku.checkSelfPermission() == 0
        } catch (_: Exception) {
            false
        }

    fun requestPermission(onResult: ((Boolean) -> Unit)? = null) {
        if (onResult != null) {
            synchronized(permissionListeners) {
                permissionListeners.add(onResult)
            }
        }
        try {
            Shizuku.requestPermission(10001)
        } catch (_: Exception) {
        }
    }

    suspend fun runCommand(
        command: String,
        timeoutSeconds: Long = 30,
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isAvailable) {
                return@withLock mapOf(
                    "success" to false,
                    "error" to "Shizuku is not available. Install Shizuku from https://shizuku.rikka.app and start it via ADB.",
                )
            }
            if (!hasPermission) {
                return@withLock mapOf(
                    "success" to false,
                    "error" to "Shizuku permission not granted. The system will prompt for permission — accept it.",
                )
            }

            ensureServiceConnected()

            val binder = serviceBinder
            if (binder == null) {
                return@withLock mapOf(
                    "success" to false,
                    "error" to "Failed to connect to Shizuku command service",
                )
            }

            try {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ICommandService.DESCRIPTOR)
                    data.writeString(command)
                    data.writeLong(timeoutSeconds * 1000L)
                    binder.transact(ICommandService.TRANSACTION_executeCommand, data, reply, 0)
                    val resultJson = reply.readString()
                    if (resultJson != null) {
                        val result = json.decodeFromString<CommandResultDto>(resultJson)
                        val map = result.toMap().toMutableMap()
                        map["stdout"] = (map["stdout"] as? String)?.take(MAX_OUTPUT_LENGTH) ?: ""
                        map["stderr"] = (map["stderr"] as? String)?.take(MAX_OUTPUT_LENGTH) ?: ""
                        map.toMap()
                    } else {
                        mapOf("success" to false, "error" to "Empty response from service")
                    }
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            } catch (e: Exception) {
                serviceBinder = null
                mapOf(
                    "success" to false,
                    "error" to "Command execution failed: ${e.message}",
                )
            }
        }
    }

    private fun ensureServiceConnected() {
        if (serviceBinder != null) return
        if (serviceArgs != null && serviceConnection != null) {
            try {
                Shizuku.unbindUserService(serviceArgs!!, serviceConnection!!, true)
            } catch (_: Exception) {
            }
            serviceConnection = null
            serviceArgs = null
        }

        val latch = CountDownLatch(1)
        var binder: IBinder? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder = service
                serviceBinder = service
                try {
                    service.linkToDeath(binderDeathRecipient, 0)
                } catch (_: Exception) {
                }
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                serviceBinder = null
            }
        }

        val args = Shizuku.UserServiceArgs(
            ComponentName("com.kai.custom", "com.kai.custom.shizuku.CommandService")
        ).apply {
            processNameSuffix("command_service")
            daemon(false)
            debuggable(false)
            version(1)
        }

        try {
            Shizuku.bindUserService(args, connection)
            serviceConnection = connection
            serviceArgs = args

            if (!latch.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                try {
                    Shizuku.unbindUserService(args, connection, true)
                } catch (_: Exception) {
                }
                serviceBinder = null
                serviceConnection = null
                serviceArgs = null
            }
        } catch (e: Exception) {
            serviceBinder = null
            serviceConnection = null
            serviceArgs = null
        }
    }

    fun stopService() {
        try {
            if (serviceBinder != null) {
                try {
                    val data = Parcel.obtain()
                    try {
                        data.writeInterfaceToken(ICommandService.DESCRIPTOR)
                        serviceBinder?.transact(ICommandService.TRANSACTION_destroy, data, null, 0)
                    } finally {
                        data.recycle()
                    }
                } catch (_: Exception) {
                }
            }

            val conn = serviceConnection
            val args = serviceArgs
            if (conn != null && args != null) {
                Shizuku.unbindUserService(args, conn, true)
            }
        } catch (_: Exception) {
        }
        serviceBinder = null
        serviceConnection = null
        serviceArgs = null
    }
}
