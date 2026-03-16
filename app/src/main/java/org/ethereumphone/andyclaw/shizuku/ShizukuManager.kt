package org.ethereumphone.andyclaw.shizuku

import android.content.pm.PackageManager
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ShizukuManager {

    companion object {
        private const val TAG = "ShizukuManager"
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val MAX_OUTPUT_CHARS = 50_000
        // Binder transaction code for newProcess in IShizukuService
        private const val TRANSACT_NEW_PROCESS = 8
    }

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    private val _uid = MutableStateFlow(-1)
    val uid: StateFlow<Int> = _uid.asStateFlow()

    val isReady: Boolean get() = _isAvailable.value && _isPermissionGranted.value

    val privilegeLevel: String
        get() = when (_uid.value) {
            0 -> "root"
            2000 -> "adb"
            else -> "unknown"
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku binder received")
        _isAvailable.value = true
        refreshPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.i(TAG, "Shizuku binder died")
        _isAvailable.value = false
        _isPermissionGranted.value = false
        _uid.value = -1
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "Shizuku permission result: granted=$granted")
            _isPermissionGranted.value = granted
            if (granted) {
                try {
                    _uid.value = Shizuku.getUid()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get Shizuku uid", e)
                }
            }
        }

    fun init() {
        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)

            if (Shizuku.pingBinder()) {
                _isAvailable.value = true
                refreshPermission()
            }
            Log.i(TAG, "Initialized: available=${_isAvailable.value}, permission=${_isPermissionGranted.value}")
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku not installed or not available: ${e.message}")
        }
    }

    fun requestPermission() {
        if (!_isAvailable.value) return
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                _isPermissionGranted.value = true
                _uid.value = Shizuku.getUid()
            } else {
                Shizuku.requestPermission(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request Shizuku permission", e)
        }
    }

    private fun refreshPermission() {
        try {
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            _isPermissionGranted.value = granted
            if (granted) {
                _uid.value = Shizuku.getUid()
                Log.i(TAG, "Permission granted, uid=${_uid.value} (${privilegeLevel})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check Shizuku permission", e)
        }
    }

    data class CommandResult(
        val exitCode: Int,
        val output: String,
        val truncated: Boolean = false,
    )

    fun executeCommand(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): CommandResult {
        if (!isReady) {
            throw IllegalStateException(
                "Shizuku is not ready (available=${_isAvailable.value}, permission=${_isPermissionGranted.value})"
            )
        }

        val process = newProcess(arrayOf("sh", "-c", command), null, null)

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
        val output = StringBuilder()

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (output.length < MAX_OUTPUT_CHARS) {
                    output.appendLine(line)
                }
            }
            while (errorReader.readLine().also { line = it } != null) {
                if (output.length < MAX_OUTPUT_CHARS) {
                    output.appendLine(line)
                }
            }

            val completed = process.waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroy()
                return CommandResult(exitCode = -1, output = "Command timed out after ${timeoutMs}ms")
            }

            return CommandResult(
                exitCode = process.exitValue(),
                output = output.toString().take(MAX_OUTPUT_CHARS),
                truncated = output.length > MAX_OUTPUT_CHARS,
            )
        } finally {
            reader.close()
            errorReader.close()
            process.destroy()
        }
    }

    /**
     * Create a new process via the Shizuku binder using transactRemote.
     * This is the same as the internal Shizuku.newProcess() but accessed
     * through the public transactRemote API.
     */
    private fun newProcess(cmd: Array<String>, env: Array<String>?, dir: String?): ShizukuRemoteProcess {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            data.writeStringArray(cmd)
            data.writeStringArray(env)
            data.writeString(dir)
            Shizuku.transactRemote(data, reply, 0)
            reply.readException()
            return ShizukuRemoteProcess.CREATOR.createFromParcel(reply)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean up Shizuku listeners", e)
        }
    }
}
