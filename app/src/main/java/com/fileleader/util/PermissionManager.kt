package com.fileleader.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.fileleader.data.model.PermissionMode
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Current detected mode (lazy-evaluated)
    private var _mode: PermissionMode? = null

    /**
     * Detect the best available permission mode:
     *  1. Root (su available & granted)
     *  2. ADB via Shizuku
     *  3. Normal
     */
    suspend fun detectMode(): PermissionMode = withContext(Dispatchers.IO) {
        // Check Root first
        if (isRootAvailable()) {
            _mode = PermissionMode.ROOT
            return@withContext PermissionMode.ROOT
        }
        // Then ADB via Shizuku
        if (isShizukuAvailable()) {
            _mode = PermissionMode.ADB
            return@withContext PermissionMode.ADB
        }
        // Fallback to normal
        _mode = PermissionMode.NORMAL
        return@withContext PermissionMode.NORMAL
    }

    fun getCurrentMode(): PermissionMode = _mode ?: PermissionMode.NORMAL

    // ===== Root =====
    fun isRootAvailable(): Boolean {
        return try {
            Shell.getShell().isRoot
        } catch (e: Exception) {
            false
        }
    }

    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            if (shell.isRoot) {
                _mode = PermissionMode.ROOT
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    // ===== Shizuku (ADB) =====
    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            isShizukuInstalled() &&
                Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestShizukuPermission(callback: (Boolean) -> Unit) {
        if (!isShizukuInstalled()) {
            callback(false)
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            _mode = PermissionMode.ADB
            callback(true)
            return
        }
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                if (granted) _mode = PermissionMode.ADB
                callback(granted)
            }
        }
        Shizuku.requestPermission(1001)
    }

    // ===== Storage Access =====
    fun hasManageExternalStorage(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasMediaPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val imgs = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES)
            val vids = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO)
            val aud  = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_AUDIO)
            imgs == PackageManager.PERMISSION_GRANTED ||
            vids == PackageManager.PERMISSION_GRANTED ||
            aud  == PackageManager.PERMISSION_GRANTED
        } else {
            hasManageExternalStorage()
        }
    }

    // ===== Summary for UI =====
    data class PermissionStatus(
        val mode: PermissionMode,
        val hasStorageAccess: Boolean,
        val hasRootAccess: Boolean,
        val hasAdbAccess: Boolean,
        val shizukuInstalled: Boolean
    )

    suspend fun getPermissionStatus(): PermissionStatus {
        val mode = detectMode()
        return PermissionStatus(
            mode = mode,
            hasStorageAccess = hasManageExternalStorage(),
            hasRootAccess = isRootAvailable(),
            hasAdbAccess = isShizukuAvailable(),
            shizukuInstalled = isShizukuInstalled()
        )
    }
}
