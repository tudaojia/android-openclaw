package com.openclaw.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import com.google.gson.Gson
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * WebView → Kotlin bridge via @JavascriptInterface (§2.6).
 * All methods callable from JavaScript as window.OpenClaw.<method>().
 * All return values are JSON strings. Async operations use EventBridge (§2.8).
 * 
 * Security improvements:
 * - Command whitelist validation
 * - Rate limiting for command execution
 * - Network status detection
 */
class JsBridge(
    private val activity: MainActivity,
    private val sessionManager: TerminalSessionManager,
    private val bootstrapManager: BootstrapManager,
    private val eventBridge: EventBridge
) {
    private val gson = Gson()
    private val TAG = "JsBridge"

    // Security: Command whitelist
    private val ALLOWED_COMMANDS = setOf(
        "npm", "node", "npx", "bun", "yarn", "pnpm",
        "git", "ls", "cat", "echo", "pwd", "which", "env", "printenv",
        "mkdir", "touch", "rm", "cp", "mv", "chmod", "chown",
        "grep", "find", "head", "tail", "wc", "sort", "uniq",
        "curl", "wget", "tar", "unzip", "zip",
        "python", "python3", "pip", "pip3",
        "openclaw", "claude", "gemini", "codex", "opencode",
        "bash", "sh", "zsh", "fish",
        "tmux", "screen", "ttyd",
        "ssh", "scp", "rsync",
        "adb", "fastboot",
        "code-server", "vim", "nano", "emacs",
        "dufs", "http-server"
    )

    // Security: Simple rate limiter (10 commands per second)
    private val commandRateLimiter = SimpleRateLimiter(10.0)

    // Security: Blocked command patterns
    private val BLOCKED_PATTERNS = listOf(
        "rm -rf /", "rm -rf /*", "mkfs", "dd if=",
        ":(){ :|:& };:", "chmod -R 777 /",
        "curl.*|.*bash", "wget.*|.*bash",
        "sudo rm", "su -c"
    )

    /**
     * Simple rate limiter implementation without external dependencies.
     * Uses token bucket algorithm.
     */
    private class SimpleRateLimiter(private val permitsPerSecond: Double) {
        private val lastRefillTime = AtomicLong(System.nanoTime())
        private val availablePermits = AtomicLong((permitsPerSecond * 1_000_000_000).toLong())
        private val maxPermits = (permitsPerSecond * 1_000_000_000).toLong()

        fun tryAcquire(): Boolean {
            refill()
            while (true) {
                val current = availablePermits.get()
                if (current <= 0) return false
                if (availablePermits.compareAndSet(current, current - 1_000_000_000L)) {
                    return true
                }
            }
        }

        private fun refill() {
            val now = System.nanoTime()
            val last = lastRefillTime.get()
            val elapsed = now - last
            if (elapsed > 0 && lastRefillTime.compareAndSet(last, now)) {
                val newPermits = (elapsed * permitsPerSecond / 1_000_000_000).toLong()
                var current: Long
                do {
                    current = availablePermits.get()
                    val updated = minOf(current + newPermits, maxPermits)
                    if (availablePermits.compareAndSet(current, updated)) break
                } while (true)
            }
        }
    }

    /**
     * Validate command for security.
     * Returns Pair(isValid, errorMessage)
     */
    private fun validateCommand(cmd: String): Pair<Boolean, String> {
        val trimmedCmd = cmd.trim()
        
        // Check for empty command
        if (trimmedCmd.isEmpty()) {
            return Pair(false, "Empty command")
        }

        // Check for blocked patterns
        for (pattern in BLOCKED_PATTERNS) {
            if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(trimmedCmd)) {
                return Pair(false, "Blocked pattern detected: $pattern")
            }
        }

        // Extract base command
        val baseCmd = trimmedCmd.split("\\s+".toRegex()).firstOrNull() ?: ""
        
        // Check whitelist
        if (!ALLOWED_COMMANDS.contains(baseCmd)) {
            return Pair(false, "Command not allowed: $baseCmd. Allowed commands: ${ALLOWED_COMMANDS.take(10).joinToString()}...")
        }

        return Pair(true, "")
    }

    /**
     * Launch a coroutine on Dispatchers.IO with error handling.
     * Catches all exceptions to prevent app crashes from unhandled coroutine failures.
     * Errors are logged and emitted to the WebView via EventBridge.
     */
    private fun launchWithErrorHandling(
        errorEventType: String = "error",
        errorContext: Map<String, Any?> = emptyMap(),
        block: suspend CoroutineScope.() -> Unit
    ) {
        val handler = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Coroutine error [$errorEventType]: ${throwable.message}", throwable)
            eventBridge.emit(errorEventType, errorContext + mapOf(
                "error" to (throwable.message ?: "Unknown error"),
                "progress" to 0f,
                "message" to "Error: ${throwable.message}"
            ))
        }
        CoroutineScope(Dispatchers.IO + handler).launch(block = block)
    }

    // ═══════════════════════════════════════════
    // Terminal domain
    // ═══════════════════════════════════════════

    @JavascriptInterface
    fun showTerminal() {
        // Create session if none exists (e.g., after first-time setup)
        if (sessionManager.activeSession == null) {
            val session = sessionManager.createSession()
            if (bootstrapManager.needsPostSetup()) {
                val script = bootstrapManager.postSetupScript.absolutePath
                // Delay write until after attachSession() initializes the shell process.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    session.write("bash $script\n")
                }, 500)
            }
        }
        activity.showTerminal()
    }

    @JavascriptInterface
    fun showWebView() = activity.showWebView()

    @JavascriptInterface
    fun createSession(): String {
        val session = sessionManager.createSession()
        return gson.toJson(mapOf("id" to session.mHandle, "name" to (session.title ?: "Terminal")))
    }

    @JavascriptInterface
    fun switchSession(id: String) = activity.runOnUiThread {
        sessionManager.switchSession(id)
    }

    @JavascriptInterface
    fun closeSession(id: String) {
        sessionManager.closeSession(id)
    }

    @JavascriptInterface
    fun getTerminalSessions(): String {
        return gson.toJson(sessionManager.getSessionsInfo())
    }

    @JavascriptInterface
    fun writeToTerminal(id: String, data: String) {
        val session = if (id.isBlank()) {
            sessionManager.activeSession
        } else {
            sessionManager.getSessionById(id) ?: sessionManager.activeSession
        }
        session?.write(data)
    }

    @JavascriptInterface
    fun runInNewSession(command: String) {
        // Validate command before running
        val (isValid, errorMsg) = validateCommand(command)
        if (!isValid) {
            eventBridge.emit("command_error", mapOf("error" to errorMsg, "command" to command))
            return
        }

        val session = sessionManager.createSession()
        activity.showTerminal()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            session.write(command)
        }, 500)
    }

    // ═══════════════════════════════════════════
    // Setup domain
    // ═══════════════════════════════════════════

    @JavascriptInterface
    fun getSetupStatus(): String {
        return gson.toJson(bootstrapManager.getStatus())
    }

    @JavascriptInterface
    fun getBootstrapStatus(): String {
        return gson.toJson(
            mapOf(
                "installed" to bootstrapManager.isInstalled(),
                "prefixPath" to bootstrapManager.prefixDir.absolutePath
            )
        )
    }

    @JavascriptInterface
    fun startSetup() {
        launchWithErrorHandling(
            errorEventType = "setup_progress",
            errorContext = mapOf("progress" to 0f)
        ) {
            bootstrapManager.startSetup { progress, message ->
                eventBridge.emit(
                    "setup_progress",
                    mapOf("progress" to progress, "message" to message)
                )
            }
        }
    }

    @JavascriptInterface
    fun saveToolSelections(json: String) {
        val configFile = java.io.File(bootstrapManager.homeDir, ".openclaw-android/tool-selections.conf")
        configFile.parentFile?.mkdirs()
        val selections = gson.fromJson(json, Map::class.java) as? Map<*, *> ?: return
        val lines = selections.entries.joinToString("\n") { (key, value) ->
            val envKey = "INSTALL_${(key as String).uppercase().replace("-", "_")}"
            "$envKey=$value"
        }
        configFile.writeText(lines + "\n")
    }

    // ═══════════════════════════════════════════
    // Platform domain
    // ═══════════════════════════════════════════

    @JavascriptInterface
    fun getAvailablePlatforms(): String {
        return gson.toJson(
            listOf(
                mapOf("id" to "openclaw", "name" to "OpenClaw", "icon" to "🧠",
                    "desc" to "AI agent platform"),
            )
        )
    }

    @JavascriptInterface
    fun getInstalledPlatforms(): String {
        val env = EnvironmentBuilder.build(activity)
        val result = CommandRunner.runSync(
            "npm list -g --depth=0 --json 2>/dev/null",
            env, bootstrapManager.prefixDir, timeoutMs = 10_000
        )
        return result.stdout.ifBlank { "[]" }
    }

    @JavascriptInterface
    fun installPlatform(id: String) {
        launchWithErrorHandling(
            errorEventType = "install_progress",
            errorContext = mapOf("target" to id)
        ) {
            eventBridge.emit("install_progress",
                mapOf("target" to id, "progress" to 0f, "message" to "Installing $id..."))
            val env = EnvironmentBuilder.build(activity)
            CommandRunner.runStreaming(
                "npm install -g $id@latest --ignore-scripts",
                env, bootstrapManager.homeDir
            ) { output ->
                eventBridge.emit("install_progress",
                    mapOf("target" to id, "progress" to 0.5f, "message" to output))
            }
            eventBridge.emit("install_progress",
                mapOf("target" to id, "progress" to 1f, "message" to "$id installed"))
        }
    }

    @JavascriptInterface
    fun uninstallPlatform(id: String) {
        launchWithErrorHandling(
            errorEventType = "install_progress",
            errorContext = mapOf("target" to id)
        ) {
            val env = EnvironmentBuilder.build(activity)
            CommandRunner.runSync("npm uninstall -g $id", env, bootstrapManager.homeDir)
        }
    }

    @JavascriptInterface
    fun switchPlatform(id: String) {
        val markerFile = java.io.File(bootstrapManager.homeDir, ".openclaw-android/.platform")
        markerFile.parentFile?.mkdirs()
        markerFile.writeText(id)
    }

    @JavascriptInterface
    fun getActivePlatform(): String {
        val markerFile = java.io.File(bootstrapManager.homeDir, ".openclaw-android/.platform")
        val id = if (markerFile.exists()) markerFile.readText().trim() else "openclaw"
        return gson.toJson(mapOf("id" to id, "name" to id.replaceFirstChar { it.uppercase() }))
    }

    // ═══════════════════════════════════════════
    // Tools domain
    // ═══════════════════════════════════════════

    @JavascriptInterface
    fun getInstalledTools(): String {
        val env = EnvironmentBuilder.build(activity)
        val prefix = bootstrapManager.prefixDir.absolutePath
        val tools = mutableListOf<Map<String, String>>()

        val pkgChecks = mapOf(
            "tmux" to "$prefix/bin/tmux",
            "ttyd" to "$prefix/bin/ttyd",
            "dufs" to "$prefix/bin/dufs",
            "openssh-server" to "$prefix/bin/sshd",
            "android-tools" to "$prefix/bin/adb",
            "code-server" to "$prefix/bin/code-server"
        )
        for ((id, path) in pkgChecks) {
            if (java.io.File(path).exists()) {
                tools.add(mapOf("id" to id, "name" to id, "version" to "installed"))
            }
        }

        if (java.io.File("$prefix/bin/chromium-browser").exists() || java.io.File("$prefix/bin/chromium").exists()) {
            tools.add(mapOf("id" to "chromium", "name" to "chromium", "version" to "installed"))
        }

        val npmTools = listOf("claude-code", "gemini-cli", "codex-cli", "opencode")
        for (id in npmTools) {
            val binName = when (id) {
                "claude-code" -> "claude"
                "gemini-cli" -> "gemini"
                "codex-cli" -> "codex"
                else -> id
            }
            val result = CommandRunner.runSync("command -v $binName 2>/dev/null", env, bootstrapManager.prefixDir, timeoutMs = 5_000)
            if (result.stdout.trim().isNotEmpty()) {
                tools.add(mapOf("id" to id, "name" to id, "version" to "installed"))
            }
        }

        return gson.toJson(tools)
    }

    @JavascriptInterface
    fun installTool(id: String) {
        launchWithErrorHandling(
            errorEventType = "install_progress",
            errorContext = mapOf("target" to id)
        ) {
            val env = EnvironmentBuilder.build(activity)
            val cmd = when (id) {
                "tmux", "ttyd", "dufs", "openssh-server", "android-tools" ->
                    "${bootstrapManager.prefixDir.absolutePath}/bin/apt-get install -y ${if (id == "openssh-server") "openssh" else id}"
                "chromium" ->
                    "${bootstrapManager.prefixDir.absolutePath}/bin/apt-get install -y chromium"
                "code-server" -> "npm install -g code-server"
                "claude-code" -> "npm install -g @anthropic-ai/claude-code"
                "gemini-cli" -> "npm install -g @google/gemini-cli"
                "codex-cli" -> "npm install -g @openai/codex"
                "opencode" -> "npm install -g opencode"
                else -> "echo 'Unknown tool: $id'"
            }
            eventBridge.emit("install_progress",
                mapOf("target" to id, "progress" to 0f, "message" to "Installing $id..."))
            CommandRunner.runStreaming(cmd, env, bootstrapManager.homeDir) { output ->
                eventBridge.emit("install_progress",
                    mapOf("target" to id, "progress" to 0.5f, "message" to output))
            }
            eventBridge.emit("install_progress",
                mapOf("target" to id, "progress" to 1f, "message" to "$id installed"))
        }
    }

    @JavascriptInterface
    fun uninstallTool(id: String) {
        launchWithErrorHandling(
            errorEventType = "install_progress",
            errorContext = mapOf("target" to id)
        ) {
            val env = EnvironmentBuilder.build(activity)
            val cmd = when (id) {
                "tmux", "ttyd", "dufs", "openssh-server", "android-tools", "chromium" ->
                    "${bootstrapManager.prefixDir.absolutePath}/bin/apt-get remove -y ${if (id == "openssh-server") "openssh" else id}"
                "code-server" -> "npm uninstall -g code-server"
                "claude-code" -> "npm uninstall -g @anthropic-ai/claude-code"
                "gemini-cli" -> "npm uninstall -g @google/gemini-cli"
                "codex-cli" -> "npm uninstall -g @openai/codex"
                "opencode" -> "npm uninstall -g opencode"
                else -> "echo 'Unknown tool: $id'"
            }
            CommandRunner.runSync(cmd, env, bootstrapManager.homeDir)
        }
    }

    @JavascriptInterface
    fun isToolInstalled(id: String): String {
        val prefix = bootstrapManager.prefixDir.absolutePath
        val env = EnvironmentBuilder.build(activity)
        val exists = when (id) {
            "openssh-server" -> java.io.File("$prefix/bin/sshd").exists()
            "tmux", "ttyd", "dufs", "android-tools" -> java.io.File("$prefix/bin/${if (id == "android-tools") "adb" else id}").exists()
            "chromium" -> java.io.File("$prefix/bin/chromium-browser").exists() || java.io.File("$prefix/bin/chromium").exists()
            "code-server" -> java.io.File("$prefix/bin/code-server").exists()
            else -> {
                val result = CommandRunner.runSync("command -v $id 2>/dev/null", env, bootstrapManager.prefixDir, timeoutMs = 5_000)
                result.stdout.trim().isNotEmpty()
            }
        }
        return gson.toJson(mapOf("installed" to exists))
    }

    // ═══════════════════════════════════════════
    // Commands domain (with security improvements)
    // ═══════════════════════════════════════════

    @JavascriptInterface
    fun runCommand(cmd: String): String {
        // Security: Rate limiting
        if (!commandRateLimiter.tryAcquire()) {
            return gson.toJson(mapOf(
                "error" to "Rate limit exceeded. Please wait before sending more commands.",
                "stdout" to "",
                "stderr" to "",
                "exitCode" to -1
            ))
        }

        // Security: Command validation
        val (isValid, errorMsg) = validateCommand(cmd)
        if (!isValid) {
            Log.w(TAG, "Blocked command: $cmd - $errorMsg")
            return gson.toJson(mapOf(
                "error" to errorMsg,
                "stdout" to "",
                "stderr" to errorMsg,
                "exitCode" to -1
            ))
        }

        val env = EnvironmentBuilder.build(activity)
        val result = CommandRunner.runSync(cmd, env, bootstrapManager.homeDir)
        return gson.toJson(result)
    }

    @JavascriptInterface
    fun runCommandAsync(callbackId: String, cmd: String) {
        // Security: Rate limiting
        if (!commandRateLimiter.tryAcquire()) {
            eventBridge.emit("command_output",
                mapOf(
                    "callbackId" to callbackId,
                    "error" to "Rate limit exceeded",
                    "done" to true
                ))
            return
        }

        // Security: Command validation
        val (isValid, errorMsg) = validateCommand(cmd)
        if (!isValid) {
            Log.w(TAG, "Blocked async command: $cmd - $errorMsg")
            eventBridge.emit("command_output",
                mapOf(
                    "callbackId" to callbackId,
                    "error" to errorMsg,
                    "done" to true
                ))
            return
        }

        launchWithErrorHandling(
            errorEventType = "command_output",
            errorContext = mapOf("callbackId" to callbackId, "done" to true)
        ) {
            val env = EnvironmentBuilder.build(activity)
            CommandRunner.runStreaming(cmd, env, bootstrapManager.homeDir) { output ->
                eventBridge.emit(
                    "command_output",
                    mapOf("callbackId" to callbackId, "data" to output, "done" to false)
                )
            }
            eventBridge.emit(
                "command_output",
                mapOf("callbackId" to callbackId, "data" to "", "done" to true)
            )
        }
    }

    // ═══════════════════════════════════════════
    // Updates domain
    // ═══════════════════════════════════════════

    @JavascriptInterface
    fun checkForUpdates(): String {
        val updates = mutableListOf<Map<String, String>>()
        try {
            val configFile = java.io.File(activity.filesDir, "usr/share/openclaw-app/config.json")
            if (configFile.exists()) {
                val config = gson.fromJson(configFile.readText(), Map::class.java) as? Map<*, *>
                val localWwwVersion = activity.getSharedPreferences("openclaw", 0)
                    .getString("www_version", "0.0.0")
                val remoteWwwVersion = ((config?.get("www") as? Map<*, *>)?.get("version") as? String)
                if (remoteWwwVersion != null && remoteWwwVersion != localWwwVersion) {
                    updates.add(mapOf(
                        "component" to "www",
                        "currentVersion" to (localWwwVersion ?: "0.0.0"),
                        "newVersion" to remoteWwwVersion
                    ))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check for updates", e)
        }
        return gson.toJson(updates)
    }

    @JavascriptInterface
    fun applyUpdate(component: String) {
        launchWithErrorHandling(
            errorEventType = "install_progress",
            errorContext = mapOf("target" to component)
        ) {
            eventBridge.emit("install_progress",
                mapOf("target" to component, "progress" to 0f, "message" to "Updating $component..."))

            when (component) {
                "www" -> {
                    try {
                        val url = UrlResolver(activity).getWwwUrl()
                        val stagingWww = java.io.File(activity.cacheDir, "www-staging")
                        stagingWww.deleteRecursively()
                        stagingWww.mkdirs()

                        eventBridge.emit("install_progress",
                            mapOf("target" to "www", "progress" to 0.2f, "message" to "Downloading..."))
                        val zipFile = java.io.File(activity.cacheDir, "www.zip")
                        java.net.URL(url).openStream().use { input ->
                            zipFile.outputStream().use { output -> input.copyTo(output) }
                        }

                        eventBridge.emit("install_progress",
                            mapOf("target" to "www", "progress" to 0.6f, "message" to "Extracting..."))
                        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val destFile = java.io.File(stagingWww, entry.name)
                                if (entry.isDirectory) {
                                    destFile.mkdirs()
                                } else {
                                    destFile.parentFile?.mkdirs()
                                    destFile.outputStream().use { out -> zis.copyTo(out) }
                                }
                                entry = zis.nextEntry
                            }
                        }
                        zipFile.delete()

                        eventBridge.emit("install_progress",
                            mapOf("target" to "www", "progress" to 0.9f, "message" to "Applying..."))
                        val wwwDir = bootstrapManager.wwwDir
                        wwwDir.deleteRecursively()
                        wwwDir.parentFile?.mkdirs()
                        stagingWww.renameTo(wwwDir)

                        activity.runOnUiThread { activity.reloadWebView() }
                    } catch (e: Exception) {
                        eventBridge.emit("install_progress",
                            mapOf("target" to "www", "progress" to 0f,
                                "message" to "Update failed: ${e.message}"))
                    }
                }
                "bootstrap" -> {
                    try {
                        eventBridge.emit("install_progress",
                            mapOf("target" to "bootstrap", "progress" to 0.1f, "message" to "Downloading bootstrap..."))
                        bootstrapManager.startSetup { progress, message ->
                            eventBridge.emit("install_progress",
                                mapOf("target" to "bootstrap", "progress" to progress, "message" to message))
                        }
                    } catch (e: Exception) {
                        eventBridge.emit("install_progress",
                            mapOf("target" to "bootstrap", "progress" to 0f,
                                "message" to "Update failed: ${e.message}"))
                    }
                }
                "scripts" -> {
                    eventBridge.emit("install_progress",
                        mapOf("target" to "scripts", "progress" to 0.5f, "message" to "Scripts are updated with bootstrap"))
                }
            }

            eventBridge.emit("install_progress",
                mapOf("target" to component, "progress" to 1f, "message" to "$component updated"))
        }
    }

    // ═══════════════════════════════════════════
    // System domain
    // ═══════════════════════════════════════════

    @JavascriptInterface
    fun getAppInfo(): String {
        val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return gson.toJson(
            mapOf(
                "versionName" to (pInfo.versionName ?: "unknown"),
                "versionCode" to pInfo.versionCode,
                "packageName" to activity.packageName
            )
        )
    }

    @JavascriptInterface
    fun getBatteryOptimizationStatus(): String {
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        return gson.toJson(
            mapOf("isIgnoring" to pm.isIgnoringBatteryOptimizations(activity.packageName))
        )
    }

    @JavascriptInterface
    fun requestBatteryOptimizationExclusion() {
        activity.runOnUiThread {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun openSystemSettings(page: String) {
        activity.runOnUiThread {
            val intent = when (page) {
                "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                "app_info" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                else -> Intent(Settings.ACTION_SETTINGS)
            }
            activity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        activity.runOnUiThread {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OpenClaw", text))
        }
    }

    @JavascriptInterface
    fun getStorageInfo(): String {
        val filesDir = activity.filesDir
        val totalSpace = filesDir.totalSpace
        val freeSpace = filesDir.freeSpace
        
        // Optimized: Use cached values or quick estimation
        val bootstrapSize = estimateDirSize(bootstrapManager.prefixDir)
        val wwwSize = estimateDirSize(bootstrapManager.wwwDir)

        return gson.toJson(
            mapOf(
                "totalBytes" to totalSpace,
                "freeBytes" to freeSpace,
                "bootstrapBytes" to bootstrapSize,
                "wwwBytes" to wwwSize
            )
        )
    }

    /**
     * Quick directory size estimation (non-recursive for performance)
     */
    private fun estimateDirSize(dir: java.io.File): Long {
        if (!dir.exists()) return 0
        var size = 0L
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    size += file.length()
                } else if (file.isDirectory) {
                    // Only go one level deep for performance
                    file.listFiles()?.forEach { subFile ->
                        if (subFile.isFile) size += subFile.length()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to estimate dir size", e)
        }
        return size
    }

    @JavascriptInterface
    fun clearCache() {
        activity.cacheDir.deleteRecursively()
        activity.cacheDir.mkdirs()
    }

    // ═══════════════════════════════════════════
    // Network domain (new)
    // ═══════════════════════════════════════════

    @JavascriptInterface
    fun getNetworkStatus(): String {
        val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        
        val (connected, type) = when {
            network == null -> Pair(false, "none")
            caps == null -> Pair(false, "none")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Pair(true, "wifi")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Pair(true, "cellular")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Pair(true, "ethernet")
            else -> Pair(true, "unknown")
        }

        return gson.toJson(mapOf(
            "connected" to connected,
            "type" to type
        ))
    }

    @JavascriptInterface
    fun getSystemInfo(): String {
        return gson.toJson(mapOf(
            "os" to "android",
            "arch" to System.getProperty("os.arch"),
            "version" to android.os.Build.VERSION.RELEASE,
            "sdk" to android.os.Build.VERSION.SDK_INT,
            "device" to android.os.Build.DEVICE,
            "model" to android.os.Build.MODEL
        ))
    }
}
