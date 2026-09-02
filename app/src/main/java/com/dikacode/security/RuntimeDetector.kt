// @dikaacode
package com.dikacode.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Runtime environment detection.
 * Detects debuggers, hooking frameworks, and suspicious runtime modifications.
 * Uses detection-only approach — no destructive actions.
 */
object RuntimeDetector {
    private const val TAG = "RuntimeDetector"

    // ─── Debugger detection ───────────────────────────────────────

    /**
     * Check if a debugger is currently attached.
     */
    fun isDebuggerAttached(): Boolean {
        return try {
            // Standard Android debugger check
            if (Debug.isDebuggerConnected()) {
                return true
            }

            // Check for JDWP thread (Java Debug Wire Protocol)
            val threadName = Thread.currentThread().name
            if (threadName.contains("JDWP") || threadName.contains("Debug")) {
                return true
            }

            // CheckTracerPid — Linux-specific check
            val tracerPid = readFromFile("/proc/self/status")
                .firstOrNull { it.startsWith("TracerPid:") }
                ?.substringAfter(":")
                ?.trim()
                ?.toIntOrNull() ?: 0

            tracerPid != 0
        } catch (e: Exception) {
            false
        }
    }

    // ─── Hooking framework detection ──────────────────────────────

    private val HOOKING_FRAMEWORKS = listOf(
        "XposedBridge",
        "de.robv.android.xposed",
        "com.saurik.substrate",
        "com.topjohnwu.magisk",
        "frpclib",
        "frida",
        "frida-agent",
        "gadget",
        "gmain",
        "linjector",
        "hookify",
        "ShadowHook",
        "ReLinker"
    )

    private val HOOKING_LIBRARIES = listOf(
        "libxposed_art.so",
        "libxposed_art64.so",
        "libfrida-gadget.so",
        "libfrida-gadget.so.16",
        "libsubstrate-droid.so",
        "libsupol.so",
        "libsandhook.so",
        "liblspd.so"
    )

    /**
     * Detect known hooking frameworks.
     */
    fun detectHookingFrameworks(): List<String> {
        val detected = mutableListOf<String>()

        // 1. Check classpath for known hooking classes
        for (framework in HOOKING_FRAMEWORKS) {
            try {
                Class.forName(framework)
                detected.add(framework)
            } catch (_: ClassNotFoundException) {
                // Not present — good
            }
        }

        // 2. Check for known hooking libraries in memory maps
        try {
            val maps = readFromFile("/proc/self/maps")
            for (line in maps) {
                for (lib in HOOKING_LIBRARIES) {
                    if (line.contains(lib)) {
                        detected.add(lib)
                    }
                }
            }
        } catch (_: Exception) {
            // Maps not readable — not necessarily suspicious
        }

        // 3. Check for Frida server by common ports
        try {
            val ssOutput = executeCommand("ss -tlnp")
            if (ssOutput.contains("27042") || ssOutput.contains("27043")) {
                detected.add("frida-server-port")
            }
        } catch (_: Exception) {
            // ss not available
        }

        return detected.distinct()
    }

    /**
     * Check for injected/hooked native libraries in loaded memory.
     */
    fun detectInjectedLibraries(): List<String> {
        val injected = mutableListOf<String>()

        try {
            val maps = readFromFile("/proc/self/maps")
            val suspiciousPatterns = listOf(
                "frida",
                "xposed",
                "substrate",
                "hook",
                "inject",
                "gadget",
                "agent"
            )

            for (line in maps) {
                val lowerLine = line.lowercase()
                for (pattern in suspiciousPatterns) {
                    if (lowerLine.contains(pattern) && lowerLine.contains(".so")) {
                        val libName = line.substringAfterLast("/").substringBefore(" ")
                        if (libName.isNotBlank()) {
                            injected.add(libName)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Maps not readable
        }

        return injected.distinct()
    }

    // ─── Environment checks ───────────────────────────────────────

    /**
     * Basic emulator detection.
     */
    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.PRODUCT.contains("sdk")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.HOST.contains("Build")
                || Build.PRODUCT.contains("vbox")
    }

    /**
     * Basic root detection.
     */
    fun isRooted(): Boolean {
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        return rootPaths.any { File(it).exists() } || checkSuCommand()
    }

    private fun checkSuCommand(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.waitFor()
            !result.isNullOrEmpty()
        } catch (_: Exception) {
            false
        }
    }

    // ─── Full runtime check ───────────────────────────────────────

    /**
     * Perform comprehensive runtime environment check.
     * Returns list of detected signals.
     */
    fun performRuntimeCheck(context: Context): List<SecuritySignal> {
        val signals = mutableListOf<SecuritySignal>()

        // Debugger check
        if (isDebuggerAttached()) {
            signals.add(SecuritySignal.SignalDebuggerAttached())
        }

        // Hooking framework check
        val hookingFrameworks = detectHookingFrameworks()
        if (hookingFrameworks.isNotEmpty()) {
            for (framework in hookingFrameworks) {
                signals.add(SecuritySignal.SignalHookingFrameworkDetected(framework))
            }
        }

        // Injected library check
        val injectedLibs = detectInjectedLibraries()
        if (injectedLibs.isNotEmpty()) {
            for (lib in injectedLibs) {
                signals.add(SecuritySignal.SignalInjectedLibraryDetected(lib))
            }
        }

        // Emulator check
        if (isEmulator()) {
            signals.add(SecuritySignal.SignalEmulatorDetected())
        }

        // Root check
        if (isRooted()) {
            signals.add(SecuritySignal.SignalRootDetected())
        }

        return signals
    }

    // ─── Utility methods ──────────────────────────────────────────

    private fun readFromFile(path: String): List<String> {
        return try {
            File(path).readLines()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun executeCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output
        } catch (_: Exception) {
            ""
        }
    }
}
