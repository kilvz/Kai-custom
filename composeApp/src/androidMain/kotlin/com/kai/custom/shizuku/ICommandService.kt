package com.kai.custom.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

abstract class ICommandService : Binder(), IInterface {

    companion object {
        const val DESCRIPTOR = "com.kai.custom.shizuku.ICommandService"
        const val TRANSACTION_executeCommand = 1
        /** Shizuku server sends this to kill the UserService process */
        const val TRANSACTION_destroy = 16777115
    }

    override fun asBinder(): IBinder = this

    init {
        attachInterface(this, DESCRIPTOR)
    }

    abstract fun executeCommand(command: String, timeoutMs: Long): String
    abstract fun destroy()

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            TRANSACTION_executeCommand -> {
                data.enforceInterface(DESCRIPTOR)
                val command = data.readString() ?: return false
                val timeoutMs = data.readLong()
                val result = executeCommand(command, timeoutMs)
                reply?.writeString(result)
                return true
            }
            TRANSACTION_destroy, 16777114 -> {
                data.enforceInterface(DESCRIPTOR)
                destroy()
                return true
            }
        }
        return super.onTransact(code, data, reply, flags)
    }
}
