package org.ethereumphone.andyclaw

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.ethereumphone.andyclaw.skills.termux.TermuxCommandRunner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class TermuxConnectionTest {

    private val TAG = "TermuxConnectionTest"
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun step1_termuxIsInstalled() {
        Log.i(TAG, "=== Step 1: Check Termux installation ===")
        val pm = context.packageManager
        val info = try {
            pm.getPackageInfo("com.termux", 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Termux NOT installed")
            fail("Termux is not installed")
            return
        }
        Log.i(TAG, "Termux installed: v${info.versionName} (code ${info.longVersionCode})")
        Log.i(TAG, "  packageName: ${info.packageName}")
        Log.i(TAG, "  targetSdk: ${info.applicationInfo?.targetSdkVersion}")
        assertNotNull(info)
    }

    @Test
    fun step2_runCommandPermission() {
        Log.i(TAG, "=== Step 2: Check RUN_COMMAND permission ===")
        val granted = context.packageManager.checkPermission(
            "com.termux.permission.RUN_COMMAND",
            context.packageName,
        ) == PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "RUN_COMMAND permission granted: $granted")

        // Also check if the permission exists at all
        try {
            val permInfo = context.packageManager.getPermissionInfo("com.termux.permission.RUN_COMMAND", 0)
            Log.i(TAG, "Permission info: group=${permInfo.group}, protection=${permInfo.protection}")
        } catch (e: Exception) {
            Log.w(TAG, "Permission info not found: ${e.message}")
        }
        assertTrue("RUN_COMMAND permission not granted", granted)
    }

    @Test
    fun step3_runCommandServiceResolves() {
        Log.i(TAG, "=== Step 3: Check RunCommandService resolves ===")
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
        }
        val resolved = context.packageManager.resolveService(intent, 0)
        Log.i(TAG, "Service resolves: ${resolved != null}")
        if (resolved != null) {
            Log.i(TAG, "  serviceInfo: ${resolved.serviceInfo?.name}")
            Log.i(TAG, "  exported: ${resolved.serviceInfo?.exported}")
        }
        assertNotNull("RunCommandService does not resolve", resolved)
    }

    @Test
    fun step4_rawBroadcastRoundtrip() {
        Log.i(TAG, "=== Step 4: Test raw broadcast roundtrip (no Termux) ===")
        // Test that our own broadcast receiver works at all
        val action = "org.ethereumphone.andyclaw.TEST_BROADCAST_${UUID.randomUUID()}"
        val latch = CountDownLatch(1)
        var received = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                Log.i(TAG, "Broadcast received! action=${intent.action}")
                Log.i(TAG, "  extras: ${intent.extras?.keySet()?.joinToString()}")
                received = true
                latch.countDown()
            }
        }

        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        Log.i(TAG, "Registered receiver for action: $action")

        // Send broadcast to ourselves
        val testIntent = Intent(action).apply { setPackage(context.packageName) }
        context.sendBroadcast(testIntent)
        Log.i(TAG, "Sent test broadcast")

        val ok = latch.await(5, TimeUnit.SECONDS)
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}

        Log.i(TAG, "Broadcast received: $received (waited: $ok)")
        assertTrue("Self-broadcast not received within 5s", received)
    }

    @Test
    fun step5_pendingIntentBroadcastRoundtrip() {
        Log.i(TAG, "=== Step 5: Test PendingIntent broadcast roundtrip (no Termux) ===")
        val action = "org.ethereumphone.andyclaw.TEST_PI_${UUID.randomUUID()}"
        val latch = CountDownLatch(1)
        var received = false
        var receivedExtras: String? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                Log.i(TAG, "PendingIntent broadcast received! action=${intent.action}")
                Log.i(TAG, "  extras keys: ${intent.extras?.keySet()?.joinToString()}")
                receivedExtras = intent.extras?.keySet()?.joinToString()
                received = true
                latch.countDown()
            }
        }

        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)

        val resultIntent = Intent(action).apply { setPackage(context.packageName) }
        val pi = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            resultIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE,
        )

        // Simulate what Termux does: send the PendingIntent with extras
        val fillIntent = Intent().apply {
            putExtra("stdout", "test_output")
            putExtra("stderr", "")
            putExtra("exitCode", 0)
        }
        pi.send(context, 0, fillIntent)
        Log.i(TAG, "Sent PendingIntent")

        val ok = latch.await(5, TimeUnit.SECONDS)
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}

        Log.i(TAG, "PendingIntent received: $received, extras: $receivedExtras")
        assertTrue("PendingIntent broadcast not received within 5s", received)
    }

    @Test
    fun step6_pendingIntentImmutableRoundtrip() {
        Log.i(TAG, "=== Step 6: Test PendingIntent with FLAG_IMMUTABLE (matches TermuxCommandRunner) ===")
        val action = "org.ethereumphone.andyclaw.TEST_IMMUTABLE_${UUID.randomUUID()}"
        val latch = CountDownLatch(1)
        var received = false
        var gotExtras = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                Log.i(TAG, "IMMUTABLE PendingIntent received! action=${intent.action}")
                val extras = intent.extras
                Log.i(TAG, "  extras: ${extras?.keySet()?.joinToString()}")
                val stdout = extras?.getString("stdout")
                val exitCode = extras?.getInt("exitCode", -999)
                Log.i(TAG, "  stdout=$stdout, exitCode=$exitCode")
                gotExtras = stdout != null
                received = true
                latch.countDown()
            }
        }

        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)

        val resultIntent = Intent(action).apply { setPackage(context.packageName) }
        val pi = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            resultIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Send with extras — with IMMUTABLE, the fill intent extras may be ignored
        val fillIntent = Intent().apply {
            putExtra("stdout", "test_output")
            putExtra("stderr", "")
            putExtra("exitCode", 0)
        }
        pi.send(context, 0, fillIntent)
        Log.i(TAG, "Sent IMMUTABLE PendingIntent with fill extras")

        val ok = latch.await(5, TimeUnit.SECONDS)
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}

        Log.i(TAG, "Received: $received, gotExtras: $gotExtras")
        if (received && !gotExtras) {
            Log.e(TAG, "!!! IMMUTABLE PendingIntent drops fill extras! This is likely the bug.")
            Log.e(TAG, "!!! Termux sends result via PendingIntent.send(ctx, 0, fillIntent)")
            Log.e(TAG, "!!! With FLAG_IMMUTABLE, the fillIntent extras are IGNORED on API 31+")
        }
        assertTrue("IMMUTABLE PendingIntent broadcast not received", received)
        assertTrue("IMMUTABLE PendingIntent dropped fill extras — this is the Termux callback bug", gotExtras)
    }

    @Test
    fun step7_termuxCommandRunner() {
        Log.i(TAG, "=== Step 7: Test TermuxCommandRunner.run() ===")
        val runner = TermuxCommandRunner(context)

        Log.i(TAG, "isTermuxInstalled: ${runner.isTermuxInstalled()}")
        val (version, code) = runner.getVersionInfo()
        Log.i(TAG, "version: $version, code: $code")

        if (!runner.isTermuxInstalled()) {
            Log.w(TAG, "Skipping — Termux not installed")
            return
        }

        val result = runBlocking {
            runner.run("echo 'hello_from_test' && id", timeoutMs = 20_000)
        }

        Log.i(TAG, "exitCode: ${result.exitCode}")
        Log.i(TAG, "stdout: '${result.stdout}'")
        Log.i(TAG, "stderr: '${result.stderr}'")
        Log.i(TAG, "internalError: ${result.internalError}")
        Log.i(TAG, "isSuccess: ${result.isSuccess}")

        if (result.exitCode == -1 && result.stdout.isBlank()) {
            Log.e(TAG, "Got exit -1 with empty stdout — callback never received")
            Log.e(TAG, "Check step 6 — if FLAG_IMMUTABLE drops extras, that's the root cause")
        }

        assertEquals("Expected exit code 0", 0, result.exitCode)
        assertTrue("Expected stdout to contain hello_from_test", result.stdout.contains("hello_from_test"))
    }
}
